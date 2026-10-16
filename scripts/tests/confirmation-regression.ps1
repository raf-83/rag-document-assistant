[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$confirmationPath = Join-Path $repositoryRoot "scripts/common/confirmation.ps1"
. $confirmationPath

function Assert-Equal {
    param(
        [object]$Expected,
        [object]$Actual,
        [string]$Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message Expected: '$Expected'; actual: '$Actual'."
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$script:ReadHostResponse = $null
$script:ReadHostPrompts = @()
$script:DisplayedLines = @()
$script:ProtectedActionCount = 0

function Read-Host {
    param([string]$Prompt)

    $script:ReadHostPrompts += $Prompt
    return $script:ReadHostResponse
}

function Write-Host {
    param(
        [Parameter(Position = 0)]
        [object]$Object,
        [ConsoleColor]$ForegroundColor
    )

    $script:DisplayedLines += "$Object"
}

function Invoke-ProtectedTestAction {
    Confirm-Action `
        -Operation "Update a test resource." `
        -Details @(
            "Repository: example/repository",
            "Branch: test-branch"
        )

    $script:ProtectedActionCount++
}

foreach ($acceptedResponse in @("y", "Y")) {
    $script:ReadHostResponse = $acceptedResponse
    $beforeCount = $script:ProtectedActionCount
    Invoke-ProtectedTestAction
    Assert-Equal ($beforeCount + 1) $script:ProtectedActionCount "Response '$acceptedResponse' should approve the operation."
}

foreach ($rejectedResponse in @("n", "N", "", "yes", " y", "1")) {
    $script:ReadHostResponse = $rejectedResponse
    $beforeCount = $script:ProtectedActionCount
    $cancelled = $false

    try {
        Invoke-ProtectedTestAction
    }
    catch {
        $cancelled = $_.Exception.Message -like "Operation cancelled.*"
    }

    Assert-True $cancelled "Response '$rejectedResponse' should cancel the operation."
    Assert-Equal $beforeCount $script:ProtectedActionCount "A rejected response must stop before the protected operation."
}

Assert-True ($script:ReadHostPrompts.Count -eq 8) "Every confirmation should ask one question."
Assert-True (@($script:ReadHostPrompts | Where-Object { $_ -ne "Continue? (y/n)" }).Count -eq 0) "The shared confirmation prompt changed."
Assert-True ($script:DisplayedLines -contains "Operation: Update a test resource.") "The operation description was not displayed before confirmation."
Assert-True ($script:DisplayedLines -contains "Repository: example/repository") "The repository was not displayed before confirmation."
Assert-True ($script:DisplayedLines -contains "Branch: test-branch") "The branch was not displayed before confirmation."

$protectedScriptPaths = @(
    (Join-Path $repositoryRoot "scripts/release-workflow.ps1"),
    (Join-Path $repositoryRoot "infra/k8s/scripts/sync-backend-secret.ps1"),
    (Join-Path $repositoryRoot "infra/k8s/scripts/sync-postgres-secret.ps1")
)

foreach ($scriptPath in $protectedScriptPaths) {
    $tokens = $null
    $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $scriptPath,
        [ref]$tokens,
        [ref]$parseErrors
    )

    Assert-Equal 0 $parseErrors.Count "PowerShell syntax validation failed for $scriptPath."

    $directReadHostCalls = @($ast.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -eq "Read-Host"
            }, $true))
    Assert-Equal 0 $directReadHostCalls.Count "Protected scripts must use the shared confirmation helper: $scriptPath."
}

foreach ($secretScriptPath in $protectedScriptPaths[1..2]) {
    $tokens = $null
    $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $secretScriptPath,
        [ref]$tokens,
        [ref]$parseErrors
    )
    $confirmationCalls = @($ast.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -eq "Confirm-Action"
            }, $true))

    Assert-Equal 1 $confirmationCalls.Count "Each Secret synchronization script must have one confirmation gate."
    $confirmationText = $confirmationCalls[0].Extent.Text
    Assert-True ($confirmationText -match '\$context') "The Secret confirmation must show the Kubernetes context."
    Assert-True ($confirmationText -match '\$Namespace') "The Secret confirmation must show the namespace."
    Assert-True ($confirmationText -match '\$secretName') "The Secret confirmation must show the Secret name."
    Assert-True ($confirmationText -notmatch '\$(?:values|value|secretData|manifest)\b') "The Secret confirmation must not expose secret-bearing variables."
}

Write-Output "CONFIRMATION_REGRESSION_OK"
