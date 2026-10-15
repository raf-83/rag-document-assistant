
param(
    [string]$KubeContext = "",
    [string]$Namespace = "default"
)

$ErrorActionPreference = "Stop"

$secretName = "backend-secret"
$k8sDirectory = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $k8sDirectory "backend-secret.env"

$requiredSecrets = @(
    "APP_AI_OPENAI_API_KEY",
    "APP_SECURITY_JWT_SECRET",
    "SPRING_DATASOURCE_PASSWORD"
)

$values = @{}
$secretData = @{}
$manifest = $null
$bytes = $null
$value = $null

try {
    # 1. Check prerequisites.
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
        throw "kubectl is not installed or is not available in PATH."
    }

    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw "Missing secrets file: $envFile"
    }

    if ([string]::IsNullOrWhiteSpace($Namespace)) {
        throw "Namespace cannot be empty."
    }

    # 2. Load and validate secrets.
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $line = $line.Trim()

        if ($line -eq "" -or $line.StartsWith("#")) {
            continue
        }

        if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            throw "Invalid entry in backend-secret.env."
        }

        $name = $Matches[1]
        $value = $Matches[2]

        if ($name -notin $requiredSecrets) {
            throw "Unexpected variable: $name"
        }

        if ($values.ContainsKey($name)) {
            throw "Duplicate variable: $name"
        }

        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Empty value for: $name"
        }

        if ($value -match '\$\{[^}]+\}') {
            throw "Unresolved placeholder for: $name"
        }

        $values[$name] = $value
        $value = $null
    }

    foreach ($name in $requiredSecrets) {
        if (-not $values.ContainsKey($name)) {
            throw "Missing required secret: $name"
        }
    }

    # 3. Resolve Kubernetes context.
    $kubectlArgs = @()

    if (-not [string]::IsNullOrWhiteSpace($KubeContext)) {
        $kubectlArgs += "--context=$KubeContext"
    }

    if ([string]::IsNullOrWhiteSpace($KubeContext)) {
        $context = kubectl config current-context

        if ($LASTEXITCODE -ne 0) {
            throw "Failed to read Kubernetes context."
        }
    } else {
        $context = $KubeContext
    }

    # Verify that the selected context exists.
    $availableContexts = @(kubectl config get-contexts -o name)

    if ($LASTEXITCODE -ne 0 -or $context -notin $availableContexts) {
        throw "Kubernetes context does not exist: $context"
    }

    # Always target the verified context explicitly.
    $kubectlArgs = @("--context=$context")

    Write-Host "Kubernetes context: $context"
    Write-Host "Namespace: $Namespace"
    Write-Host "Secret: $secretName"

    # 4. Require explicit confirmation.
    $confirmation = Read-Host "Type YES to synchronize secrets"

    if ($confirmation -cne "YES") {
        throw "Operation cancelled."
    }

    # 5. Create Secret manifest in memory.
    foreach ($name in $requiredSecrets) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($values[$name])
        $secretData[$name] = [Convert]::ToBase64String($bytes)
        $bytes = $null
    }

    $manifest = @{
        apiVersion = "v1"
        kind = "Secret"
        metadata = @{
            name = $secretName
            namespace = $Namespace
        }
        type = "Opaque"
        data = $secretData
    } | ConvertTo-Json -Depth 5 -Compress

    # 6. Synchronize the Kubernetes Secret.
    $manifest | & kubectl @kubectlArgs apply `
        --server-side `
        --field-manager=rag-document-assistant-secrets `
        --namespace=$Namespace `
        -f -

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to synchronize Kubernetes Secret."
    }

    Write-Host "Kubernetes Secret synchronized successfully."
    Write-Host "Restart the backend if secret values changed."
}
finally {
    $values.Clear()
    $secretData.Clear()

    $manifest = $null
    $bytes = $null
    $value = $null
}
