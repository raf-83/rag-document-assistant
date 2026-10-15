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

foreach ($functionName in @("Wait-PullRequestHead", "Invoke-Submit")) {
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

function New-TestPullRequest {
    param(
        [string]$HeadSha,
        [string]$State = "OPEN"
    )

    return [pscustomobject]@{
        number       = 19
        state        = $State
        url          = "https://github.example/pull/19"
        headRefOid   = $HeadSha
        headRefName  = "feature/frontend-kubernetes"
        baseRefName  = "main"
        mergeCommit  = if ($State -eq "MERGED") { [pscustomobject]@{ oid = "merge-sha" } } else { $null }
    }
}

$oldHeadSha = "935ce49000000000000000000000000000000000"
$newHeadSha = "e78bd9600000000000000000000000000000000"
$changedHeadSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
$Branch = "feature/frontend-kubernetes"
$CommitMessage = "test commit"
$script:GitHubCli = "gh"
$script:PullRequestHeadRefreshTimeoutSeconds = 1
$script:PullRequestHeadRefreshPollIntervalSeconds = 0

function Assert-ExpectedBranch {}
function Assert-GitHubCliReady {}
function Assert-LocalSecretFilesAreUntracked {}
function Invoke-SecretPatternCheck {}
function Write-Step { param([string]$Message) }
function Confirm-Exact { param([string]$Prompt, [string]$Expected) }

function git {
    $command = $args -join " "
    $script:LASTEXITCODE = 0

    if ($command -eq "--no-pager rev-parse HEAD") {
        return $script:TestLocalHeadSha
    }
    if ($command -eq "--no-pager config --get branch.$Branch.remote") {
        return "origin"
    }
    if ($command -eq "--no-pager rev-list --count @{u}..HEAD") {
        return "$script:TestAheadCount"
    }
    if ($command -eq "--no-pager status --porcelain") {
        return
    }

    throw "Unexpected git command in regression test: $command"
}

function Reset-TestState {
    param(
        [int]$AheadCount,
        [object[]]$PullRequestResponses
    )

    $script:TestLocalHeadSha = $newHeadSha
    $script:TestAheadCount = $AheadCount
    $script:PullRequestResponses = [System.Collections.Generic.Queue[object]]::new()
    foreach ($response in $PullRequestResponses) {
        $script:PullRequestResponses.Enqueue($response)
    }
    $script:CheckHeadShas = @()
    $script:CheckedInvocationCount = 0
    $script:PushInvocationCount = 0
    $script:MergeInvocationCount = 0
    $script:MergeArguments = @()
    $script:CurrentPullRequestInvocationCount = 0
    $script:PendingOnSecondCheck = $false
}

function Get-PullRequestByNumber {
    param([int]$PullRequestNumber)

    if ($script:PullRequestResponses.Count -eq 0) {
        throw "No mocked Pull Request response is available."
    }

    return $script:PullRequestResponses.Dequeue()
}

function Get-CurrentPullRequest {
    $script:CurrentPullRequestInvocationCount++
    if ($script:CurrentPullRequestInvocationCount -eq 1) {
        return New-TestPullRequest -HeadSha $script:InitialPullRequestHeadSha
    }

    return New-TestPullRequest -HeadSha $newHeadSha -State "MERGED"
}

function Wait-PullRequestChecks {
    param(
        [int]$PullRequestNumber,
        [string]$HeadSha,
        [int]$TimeoutSeconds,
        [int]$PollIntervalSeconds
    )

    $script:CheckedInvocationCount++
    $script:CheckHeadShas += $HeadSha
    if ($script:PendingOnSecondCheck -and $script:CheckedInvocationCount -eq 2) {
        throw "Required Pull Request workflows are still pending for commit $HeadSha."
    }
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    if ($FilePath -eq "git" -and $Arguments -contains "push") {
        $script:PushInvocationCount++
        return
    }
    if ($FilePath -eq $script:GitHubCli -and $Arguments[0] -eq "pr" -and $Arguments[1] -eq "merge") {
        $script:MergeInvocationCount++
        $script:MergeArguments = $Arguments
        return
    }

    throw "Unexpected checked command in regression test: $FilePath $($Arguments -join ' ')"
}

# A push updates PR #19 asynchronously. CI and merge must use only the refreshed head SHA.
$script:InitialPullRequestHeadSha = $oldHeadSha
Reset-TestState -AheadCount 1 -PullRequestResponses @(
    (New-TestPullRequest -HeadSha $oldHeadSha),
    (New-TestPullRequest -HeadSha $oldHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha)
)
Invoke-Submit
Assert-Equal 1 $script:PushInvocationCount "The scenario should include one push."
Assert-Equal 2 $script:CheckedInvocationCount "CI should be checked after refresh and immediately before merge."
Assert-True (@($script:CheckHeadShas | Where-Object { $_ -ne $newHeadSha }).Count -eq 0) "CI used a stale Pull Request head SHA."
Assert-Equal 1 $script:MergeInvocationCount "A stable successful head should reach merge."
$matchHeadIndex = [Array]::IndexOf($script:MergeArguments, "--match-head-commit")
Assert-True ($matchHeadIndex -ge 0) "The merge command must use --match-head-commit."
Assert-Equal $newHeadSha $script:MergeArguments[$matchHeadIndex + 1] "The merge command used the wrong head SHA."

# A head change after CI must stop before the MERGE gate and merge command.
$script:InitialPullRequestHeadSha = $newHeadSha
Reset-TestState -AheadCount 0 -PullRequestResponses @(
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $changedHeadSha)
)
$headChangeRejected = $false
try {
    Invoke-Submit
}
catch {
    $headChangeRejected = $_.Exception.Message -like "Pull Request head changed*"
}
Assert-True $headChangeRejected "A changed Pull Request head was not rejected."
Assert-Equal 0 $script:MergeInvocationCount "Merge was attempted after the Pull Request head changed."

# A pending required workflow in the final pre-merge check must block merge.
$script:InitialPullRequestHeadSha = $newHeadSha
Reset-TestState -AheadCount 0 -PullRequestResponses @(
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha),
    (New-TestPullRequest -HeadSha $newHeadSha)
)
$script:PendingOnSecondCheck = $true
$pendingRejected = $false
try {
    Invoke-Submit
}
catch {
    $pendingRejected = $_.Exception.Message -like "*still pending*"
}
Assert-True $pendingRejected "Pending required workflows were not rejected immediately before merge."
Assert-Equal 0 $script:MergeInvocationCount "Merge was attempted while required workflows were pending."

Write-Output "RELEASE_WORKFLOW_HEAD_SHA_REGRESSION_OK"
