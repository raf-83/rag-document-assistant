[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$workflowPath = Join-Path (Split-Path -Parent $PSScriptRoot) "release-workflow.ps1"
$tokens = $null
$parseErrors = $null
$workflowAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $workflowPath,
    [ref]$tokens,
    [ref]$parseErrors
)

if ($parseErrors.Count -gt 0) {
    throw "Could not parse release-workflow.ps1: $($parseErrors[0].Message)"
}

$functionNames = @(
    "Get-PullRequestChangedPaths",
    "ConvertFrom-WorkflowYamlListValue",
    "Get-WorkflowPullRequestPathFilters",
    "ConvertTo-GitHubPathRegex",
    "Test-PathMatchesWorkflowPatterns",
    "Test-WorkflowRunsForPullRequest",
    "Wait-PullRequestChecks"
)

foreach ($functionName in $functionNames) {
    $functionAst = $workflowAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq $functionName
        }, $true) | Select-Object -First 1

    if ($null -eq $functionAst) {
        throw "Function '$functionName' was not found in release-workflow.ps1."
    }

    . ([scriptblock]::Create($functionAst.Extent.Text))
}

function Assert-Equal {
    param([object]$Expected, [object]$Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Expected: '$Expected'; actual: '$Actual'."
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

# The parser remains synchronized with the workflow files currently used by Submit.
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$actualBackendFilters = Get-WorkflowPullRequestPathFilters `
    -Workflow "backend-ci.yml" `
    -Content ([System.IO.File]::ReadAllText((Join-Path $repositoryRoot ".github/workflows/backend-ci.yml")))
$actualContainerFilters = Get-WorkflowPullRequestPathFilters `
    -Workflow "container-images.yml" `
    -Content ([System.IO.File]::ReadAllText((Join-Path $repositoryRoot ".github/workflows/container-images.yml")))
Assert-Equal 0 $actualBackendFilters.Paths.Count "Backend CI unexpectedly gained a pull_request paths filter."
Assert-True ($actualContainerFilters.Paths -contains "backend/**") "The backend container path filter was not parsed."
Assert-True ($actualContainerFilters.Paths -contains "frontend/**") "The frontend container path filter was not parsed."

# Changed paths use the paginated Pull Request API; an API error must propagate.
$script:ChangedPathsApiMockFailure = $false
$script:ChangedPathsApiArguments = @()
function Get-GhJson {
    param([string[]]$Arguments)
    $script:ChangedPathsApiArguments = @($Arguments)
    if ($script:ChangedPathsApiMockFailure) {
        throw "GitHub CLI command failed (exit code: 1): changed-files API"
    }
    return @("scripts/release-workflow.ps1", "docs/release-workflow.md")
}

$apiPaths = @(Get-PullRequestChangedPaths -PullRequestNumber 3)
Assert-Equal 2 $apiPaths.Count "Changed Pull Request paths were not returned."
Assert-True ($script:ChangedPathsApiArguments -contains "--paginate") "The changed-files API lookup is not paginated."
Assert-True ($script:ChangedPathsApiArguments -contains "--slurp") "Paginated changed-file results are not combined as JSON."
$script:ChangedPathsApiMockFailure = $true
$directApiFailureRejected = $false
try { Get-PullRequestChangedPaths -PullRequestNumber 3 | Out-Null }
catch { $directApiFailureRejected = $_.Exception.Message -like "GitHub CLI command failed*" }
Assert-True $directApiFailureRejected "Get-PullRequestChangedPaths swallowed an API failure."

$headSha = "1234567890abcdef1234567890abcdef12345678"
$script:RequiredPullRequestWorkflows = @(
    @{ File = "backend-ci.yml"; Name = "Backend CI" },
    @{ File = "container-images.yml"; Name = "Container images" }
)

$script:BackendWorkflowContent = @"
name: Backend CI

'on':
  workflow_dispatch:
  pull_request:
    branches:
      - main
"@

$script:ContainerWorkflowContent = @"
name: Container images

on:
  workflow_dispatch:
  pull_request:
    branches: [main]
    paths:
      - ".github/workflows/container-images.yml"
      - "backend/**"
      - "frontend/**"
"@

function Get-PullRequestChangedPaths {
    param([int]$PullRequestNumber)
    if ($script:ChangedPathsApiFailure) {
        throw "GitHub CLI command failed (exit code: 1): changed-files API"
    }
    return $script:ChangedPaths
}

function Get-WorkflowFileContentAtCommit {
    param([string]$Workflow, [string]$HeadSha)
    $script:WorkflowContentRequests += "$Workflow@$HeadSha"
    if ($Workflow -eq "backend-ci.yml") {
        return $script:BackendWorkflowContent
    }
    if ($Workflow -eq "container-images.yml") {
        return $script:ContainerWorkflowContent
    }
    throw "Unexpected workflow content request: $Workflow"
}

function Get-PullRequestWorkflowRun {
    param([string]$Workflow, [string]$HeadSha)
    $script:RunRequests += "$Workflow@$HeadSha"
    if ($script:WorkflowRuns.ContainsKey($Workflow)) {
        return $script:WorkflowRuns[$Workflow]
    }
    return $null
}

function New-WorkflowRun {
    param(
        [string]$Status = "completed",
        [string]$Conclusion = "success"
    )
    return [pscustomobject]@{
        status = $Status
        conclusion = $Conclusion
        headSha = $headSha
    }
}

function Reset-Scenario {
    param([string[]]$ChangedPaths)
    $script:ChangedPaths = $ChangedPaths
    $script:ChangedPathsApiFailure = $false
    $script:WorkflowRuns = @{
        "backend-ci.yml" = New-WorkflowRun
    }
    $script:WorkflowContentRequests = @()
    $script:RunRequests = @()
}

# Documentation and script-only Pull Requests do not trigger Container images.
Reset-Scenario -ChangedPaths @("docs/release-workflow.md", "scripts/release-workflow.ps1")
Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
Assert-Equal 1 $script:RunRequests.Count "Only Backend CI should be queried for docs/scripts-only changes."
Assert-True ($script:RunRequests[0] -eq "backend-ci.yml@$headSha") "Backend CI was not checked at the exact head SHA."
Assert-True ($script:WorkflowContentRequests -contains "container-images.yml@$headSha") "Container image filters were not read from the exact head SHA."

# A backend change requires Container images even if its run has not appeared yet.
Reset-Scenario -ChangedPaths @("backend/src/main/java/example/App.java")
$missingBackendImageRunRejected = $false
try {
    Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
}
catch {
    $missingBackendImageRunRejected = $_.Exception.Message -like "*not reported*Container images*"
}
Assert-True $missingBackendImageRunRejected "A missing Container images run was skipped for a backend change."
Assert-True ($script:RunRequests -contains "container-images.yml@$headSha") "Container images was not queried for a backend change."

# A frontend change has the same requirement and must not be mistaken for a filtered workflow.
Reset-Scenario -ChangedPaths @("frontend/src/app/page.tsx")
$missingFrontendImageRunRejected = $false
try {
    Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
}
catch {
    $missingFrontendImageRunRejected = $_.Exception.Message -like "*not reported*Container images*"
}
Assert-True $missingFrontendImageRunRejected "A missing Container images run was skipped for a frontend change."
Assert-True ($script:RunRequests -contains "container-images.yml@$headSha") "Container images was not queried for a frontend change."

# Reported failures still stop Submit.
Reset-Scenario -ChangedPaths @("backend/pom.xml")
$script:WorkflowRuns["container-images.yml"] = New-WorkflowRun -Conclusion "failure"
$failedRunRejected = $false
try {
    Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
}
catch {
    $failedRunRejected = $_.Exception.Message -like "*did not pass*"
}
Assert-True $failedRunRejected "A failed required workflow did not stop Submit."

# Pending applicable workflows still use the existing timeout path.
Reset-Scenario -ChangedPaths @("frontend/package.json")
$script:WorkflowRuns["container-images.yml"] = New-WorkflowRun -Status "in_progress" -Conclusion ""
$pendingRunTimedOut = $false
try {
    Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
}
catch {
    $pendingRunTimedOut = $_.Exception.Message -like "*still pending*Container images*"
}
Assert-True $pendingRunTimedOut "A pending applicable workflow did not follow the timeout path."

# API failures are errors, never evidence that a workflow was filtered out.
Reset-Scenario -ChangedPaths @("docs/release-workflow.md")
$script:ChangedPathsApiFailure = $true
$apiFailureRejected = $false
try {
    Wait-PullRequestChecks -PullRequestNumber 3 -HeadSha $headSha -TimeoutSeconds 0 -PollIntervalSeconds 0
}
catch {
    $apiFailureRejected = $_.Exception.Message -like "GitHub CLI command failed*"
}
Assert-True $apiFailureRejected "A changed-files API failure was treated as a skipped workflow."
Assert-Equal 0 $script:RunRequests.Count "Workflow runs were queried after the changed-files API failed."

Write-Output "RELEASE_WORKFLOW_CHECK_FILTERS_REGRESSION_OK"
