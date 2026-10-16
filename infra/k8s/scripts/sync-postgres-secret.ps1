param(
    [string]$KubeContext = "",
    [string]$Namespace = "default"
)

$ErrorActionPreference = "Stop"

$secretName = "postgres-secret"
$k8sDirectory = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $k8sDirectory)
. (Join-Path $repositoryRoot "scripts/common/confirmation.ps1")
$envFile = Join-Path $k8sDirectory "backend-secret.env"
$passwordSourceName = "SPRING_DATASOURCE_PASSWORD"
$allowedVariables = @(
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
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
        throw "kubectl is not installed or is not available in PATH."
    }

    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw "Missing secrets file: $envFile"
    }

    if ([string]::IsNullOrWhiteSpace($Namespace)) {
        throw "Namespace cannot be empty."
    }

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

        if ($name -notin $allowedVariables) {
            throw "Unexpected variable: $name"
        }

        if ($values.ContainsKey($name)) {
            throw "Duplicate variable: $name"
        }

        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Empty value for: $name"
        }

        if ($value -match '\$\{[^}]+\}' -or $value -eq "CHANGE_ME") {
            throw "Placeholder value for: $name"
        }

        $values[$name] = $value
        $value = $null
    }

    if (-not $values.ContainsKey($passwordSourceName)) {
        throw "Missing required secret: $passwordSourceName"
    }

    if ([string]::IsNullOrWhiteSpace($KubeContext)) {
        $context = (& kubectl config current-context | Out-String).Trim()

        if ($LASTEXITCODE -ne 0) {
            throw "Failed to read Kubernetes context."
        }
    }
    else {
        $context = $KubeContext
    }

    $availableContexts = @(kubectl config get-contexts -o name)
    if ($LASTEXITCODE -ne 0 -or $context -notin $availableContexts) {
        throw "Kubernetes context does not exist: $context"
    }

    Write-Host "Kubernetes context: $context"
    Write-Host "Namespace: $Namespace"
    Write-Host "Secret: $secretName"

    Confirm-Action `
        -Operation "Create or update the PostgreSQL Kubernetes Secret." `
        -Details @(
            "Kubernetes context: $context",
            "Namespace: $Namespace",
            "Secret: $secretName",
            "Secret values: not displayed"
        )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($values[$passwordSourceName])
    $secretData["POSTGRES_PASSWORD"] = [Convert]::ToBase64String($bytes)
    $bytes = $null

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

    $manifest | & kubectl --context=$context apply `
        --server-side `
        --field-manager=rag-document-assistant-secrets `
        --namespace=$Namespace `
        -f -

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to synchronize PostgreSQL Secret."
    }

    Write-Host "PostgreSQL Secret synchronized successfully."
}
finally {
    $values.Clear()
    $secretData.Clear()
    $manifest = $null
    $bytes = $null
    $value = $null
}
