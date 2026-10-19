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

foreach ($functionName in @("Get-GhJson", "ConvertTo-ValidatedPullRequest", "Get-CurrentPullRequest", "Get-PullRequestByNumber")) {
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

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param([object]$Expected, [object]$Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Expected: '$Expected'; actual: '$Actual'."
    }
}

$Branch = "feature/intelligent-submit-codex-cli"
$script:GitHubCli = "Invoke-MockedGitHubCli"
$script:MockedGitHubResponses = [System.Collections.Generic.Queue[object]]::new()
$script:GitHubInvocations = @()

function Invoke-MockedGitHubCli {
    $script:GitHubInvocations += ,@($args)
    if ($script:MockedGitHubResponses.Count -eq 0) {
        throw "No mocked GitHub CLI response is available."
    }

    $response = $script:MockedGitHubResponses.Dequeue()
    $script:LASTEXITCODE = $response.ExitCode
    if ($null -ne $response.Output) {
        Write-Output $response.Output
    }
}

function Add-MockedGitHubResponse {
    param([int]$ExitCode, [AllowNull()][string]$Output)
    $script:MockedGitHubResponses.Enqueue([pscustomobject]@{
            ExitCode = $ExitCode
            Output = $Output
        })
}

$validPullRequest = [ordered]@{
    number = 42
    state = "OPEN"
    url = "https://github.example/pull/42"
    mergeCommit = $null
    headRefName = $Branch
    headRefOid = "1234567890abcdef1234567890abcdef12345678"
    baseRefName = "main"
}
$validPullRequestJson = $validPullRequest | ConvertTo-Json -Compress

# A successful empty JSON array is the normal no-existing-PR result.
Add-MockedGitHubResponse -ExitCode 0 -Output "[]"
$missingPullRequest = Get-CurrentPullRequest
Assert-True ($null -eq $missingPullRequest) "An empty Pull Request array was not normalized to null."

# A complete Pull Request is returned and can also be refreshed by number.
Add-MockedGitHubResponse -ExitCode 0 -Output "[$validPullRequestJson]"
$existingPullRequest = Get-CurrentPullRequest
Assert-Equal 42 $existingPullRequest.number "The existing Pull Request number changed."
Assert-Equal "OPEN" $existingPullRequest.state "The existing Pull Request state changed."
Assert-Equal $Branch $existingPullRequest.headRefName "The existing Pull Request branch changed."

Add-MockedGitHubResponse -ExitCode 0 -Output $validPullRequestJson
$pullRequestByNumber = Get-PullRequestByNumber -PullRequestNumber 42
Assert-Equal 42 $pullRequestByNumber.number "Pull Request refresh returned the wrong object."

# GitHub CLI failures must not be interpreted as no Pull Request.
Add-MockedGitHubResponse -ExitCode 1 -Output $null
$cliFailureRejected = $false
try { Get-CurrentPullRequest | Out-Null }
catch { $cliFailureRejected = $_.Exception.Message -like "GitHub CLI command failed*" }
Assert-True $cliFailureRejected "A GitHub CLI failure was treated as a missing Pull Request."

# Successful but empty or malformed output is an invalid CLI response, not a missing PR.
Add-MockedGitHubResponse -ExitCode 0 -Output ""
$emptyResponseRejected = $false
try { Get-CurrentPullRequest | Out-Null }
catch { $emptyResponseRejected = $_.Exception.Message -like "GitHub CLI returned an empty response*" }
Assert-True $emptyResponseRejected "An empty GitHub CLI response was treated as a missing Pull Request."

Add-MockedGitHubResponse -ExitCode 0 -Output "{broken-json"
$invalidJsonRejected = $false
try { Get-CurrentPullRequest | Out-Null }
catch { $invalidJsonRejected = $_.Exception.Message -like "GitHub CLI returned invalid JSON*" }
Assert-True $invalidJsonRejected "Malformed GitHub CLI JSON was treated as a missing Pull Request."

# A JSON object without the required PR fields must never reach Submit as an existing PR.
Add-MockedGitHubResponse -ExitCode 0 -Output "[{}]"
$incompleteObjectRejected = $false
$incompleteObjectError = ""
try { Get-CurrentPullRequest | Out-Null }
catch {
    $incompleteObjectError = $_.Exception.Message
    $incompleteObjectRejected = $incompleteObjectError -like "GitHub CLI returned an incomplete Pull Request object*"
}
Assert-True $incompleteObjectRejected "An incomplete Pull Request object was accepted. Error: $incompleteObjectError"

# A response for another branch is also invalid for the current Submit workflow.
$wrongBranch = [ordered]@{
    number = $validPullRequest.number
    state = $validPullRequest.state
    url = $validPullRequest.url
    mergeCommit = $validPullRequest.mergeCommit
    headRefName = "feature/other"
    headRefOid = $validPullRequest.headRefOid
    baseRefName = $validPullRequest.baseRefName
}
$wrongBranchJson = $wrongBranch | ConvertTo-Json -Compress
Add-MockedGitHubResponse -ExitCode 0 -Output "[$wrongBranchJson]"
$wrongBranchRejected = $false
try { Get-CurrentPullRequest | Out-Null }
catch { $wrongBranchRejected = $_.Exception.Message -like "GitHub CLI returned Pull Request*expected*" }
Assert-True $wrongBranchRejected "A Pull Request for another branch was accepted."

Write-Output "RELEASE_WORKFLOW_PR_DETECTION_REGRESSION_OK"
