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

$loadedFunctionText = @{}
foreach ($functionName in @("Resolve-VerifiedReleaseCommit", "Invoke-Release")) {
    $functionAst = $workflowAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq $functionName
        }, $true) | Select-Object -First 1

    if ($null -eq $functionAst) {
        throw "Function '$functionName' was not found in release-workflow.ps1."
    }

    $loadedFunctionText[$functionName] = $functionAst.Extent.Text
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

Assert-True ($loadedFunctionText["Invoke-Release"] -notmatch "\b(?:mergeCommit|headRefOid)\b") "Invoke-Release must not read historical Pull Request commit identifiers."

$rewrittenMainSha = "3ba1ee2800000000000000000000000000000000"
$historicalMergeSha = "3d539c100000000000000000000000000000000"
$Version = "0.1.0"
$script:GitHubCli = "gh"
$script:BackendWorkflow = "backend-ci.yml"
$script:ContainerWorkflow = "container-images.yml"

function Write-Step { param([string]$Message) }
function Assert-GitHubCliReady {}
function Assert-ReleaseImagesArePublic { param([switch]$WaitForPublic) }

function Reset-TestState {
    $script:TestLocalMain = $rewrittenMainSha
    $script:TestOriginMain = $rewrittenMainSha
    $script:TestRemoteMain = $rewrittenMainSha
    $script:TestCommitExists = $true
    $script:TestCommitReachable = $true
    $script:GitCommands = @()
    $script:WorkflowCommits = @()
    $script:ReleaseCreateArguments = @()
    $script:ReleaseCreateCount = 0
    $script:ConfirmationOperations = @()
    $script:ConfirmationDetails = @()
    $script:RejectConfirmation = $false
    $script:RemoteMainAfterConfirmation = ""
}

function git {
    $command = $args -join " "
    $script:GitCommands += $command
    $script:LASTEXITCODE = 0

    if ($command -eq "--no-pager rev-parse --verify refs/heads/main") {
        return $script:TestLocalMain
    }
    if ($command -eq "--no-pager rev-parse --verify refs/remotes/origin/main") {
        return $script:TestOriginMain
    }
    if ($command -eq "--no-pager ls-remote --exit-code --heads origin refs/heads/main") {
        return "$($script:TestRemoteMain)`trefs/heads/main"
    }
    if ($command -eq "--no-pager cat-file -e $($script:TestRemoteMain)^{commit}") {
        if (-not $script:TestCommitExists) {
            $script:LASTEXITCODE = 1
        }
        return
    }
    if ($command -eq "--no-pager merge-base --is-ancestor $($script:TestRemoteMain) refs/heads/main") {
        if (-not $script:TestCommitReachable) {
            $script:LASTEXITCODE = 1
        }
        return
    }

    throw "Unexpected git command in regression test: $command"
}

function Get-GhJson {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $command = $Arguments -join " "
    if ($command -eq "repo view --json nameWithOwner,visibility") {
        return [pscustomobject]@{
            nameWithOwner = "raf-83/rag-document-assistant"
            visibility    = "PUBLIC"
        }
    }
    if ($command -eq "release view v$Version --json tagName,url") {
        return $null
    }

    throw "Unexpected GitHub CLI JSON query in regression test: $command"
}

function Get-CurrentPullRequest {
    throw "Invoke-Release must not query historical Pull Request metadata."
}

function Wait-GitHubWorkflow {
    param(
        [string]$Workflow,
        [string]$Ref,
        [string]$Commit = ""
    )

    $script:WorkflowCommits += $Commit
}

function Confirm-Action {
    param(
        [string]$Operation,
        [string[]]$Details
    )

    $script:ConfirmationOperations += $Operation
    $script:ConfirmationDetails += , @($Details)

    if ($script:RejectConfirmation) {
        throw "Operation cancelled. Enter y or Y to continue."
    }

    if (-not [string]::IsNullOrWhiteSpace($script:RemoteMainAfterConfirmation)) {
        $script:TestRemoteMain = $script:RemoteMainAfterConfirmation
    }
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    if ($FilePath -eq $script:GitHubCli -and $Arguments[0] -eq "release" -and $Arguments[1] -eq "create") {
        $script:ReleaseCreateCount++
        $script:ReleaseCreateArguments = $Arguments
        return
    }
    if ($FilePath -eq $script:GitHubCli -and $Arguments[0] -eq "attestation") {
        return
    }

    throw "Unexpected checked command in regression test: $FilePath $($Arguments -join ' ')"
}

# Simulate git-filter-repo followed by a force push: Pull Request metadata still points
# to the historical merge, while all current main references point to rewritten history.
Reset-TestState
Invoke-Release

$targetIndex = [Array]::IndexOf($script:ReleaseCreateArguments, "--target")
Assert-True ($targetIndex -ge 0) "The release command did not specify an explicit target."
Assert-Equal $rewrittenMainSha $script:ReleaseCreateArguments[$targetIndex + 1] "The release target did not use the rewritten remote main commit."
Assert-True ($historicalMergeSha -notin $script:ReleaseCreateArguments) "Historical Pull Request metadata was used by the release command."
Assert-True (@($script:WorkflowCommits | Where-Object { $_ -ne $rewrittenMainSha }).Count -eq 0) "A workflow lookup used a commit outside rewritten main."
Assert-True (@($script:GitCommands | Where-Object { $_ -like "*$historicalMergeSha*" }).Count -eq 0) "The historical Pull Request commit was consulted during main verification."
Assert-Equal 1 $script:ReleaseCreateCount "A verified rewritten main should create exactly one release."
Assert-True ($script:ConfirmationOperations[0] -like "Create GitHub release v$Version*") "The release confirmation did not describe the release operation."
Assert-True ($script:ConfirmationDetails[0] -contains "Repository: raf-83/rag-document-assistant") "The release confirmation did not show the repository."
Assert-True ($script:ConfirmationDetails[0] -contains "Branch: main") "The release confirmation did not show the branch."
Assert-True ($script:ConfirmationDetails[0] -contains "Commit: $rewrittenMainSha") "The release confirmation did not show the verified main commit."

# A stale local main must stop before release creation.
Reset-TestState
$script:TestLocalMain = $historicalMergeSha
$localMismatchRejected = $false
try {
    Invoke-Release
}
catch {
    $localMismatchRejected = $_.Exception.Message -like "The local main branch does not match*"
}
Assert-True $localMismatchRejected "A stale local main was not rejected."
Assert-Equal 0 $script:ReleaseCreateCount "Release creation was attempted with a stale local main."

# A stale remote-tracking ref after a force push must stop before release creation.
Reset-TestState
$script:TestOriginMain = $historicalMergeSha
$trackingMismatchRejected = $false
try {
    Invoke-Release
}
catch {
    $trackingMismatchRejected = $_.Exception.Message -like "origin/main does not match*"
}
Assert-True $trackingMismatchRejected "A stale origin/main was not rejected."
Assert-Equal 0 $script:ReleaseCreateCount "Release creation was attempted with stale remote-tracking history."

# Reachability is checked independently before release creation.
Reset-TestState
$script:TestCommitReachable = $false
$unreachableCommitRejected = $false
try {
    Invoke-Release
}
catch {
    $unreachableCommitRejected = $_.Exception.Message -like "*not reachable from the current local main history*"
}
Assert-True $unreachableCommitRejected "An unreachable release commit was not rejected."
Assert-Equal 0 $script:ReleaseCreateCount "Release creation was attempted for an unreachable commit."

# A rejected confirmation must stop before creating the release or its tag.
Reset-TestState
$script:RejectConfirmation = $true
$confirmationRejected = $false
try {
    Invoke-Release
}
catch {
    $confirmationRejected = $_.Exception.Message -like "Operation cancelled.*"
}
Assert-True $confirmationRejected "A rejected release confirmation did not cancel the operation."
Assert-Equal 0 $script:ReleaseCreateCount "Release creation was attempted after confirmation was rejected."

# A force push after the confirmation gate must be detected by the final verification.
Reset-TestState
$script:RemoteMainAfterConfirmation = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
$mainChangedRejected = $false
try {
    Invoke-Release
}
catch {
    $mainChangedRejected = $_.Exception.Message -like "origin/main does not match*"
}
Assert-True $mainChangedRejected "A remote main change after release confirmation was not rejected."
Assert-Equal 0 $script:ReleaseCreateCount "Release creation was attempted after remote main changed."

Write-Output "RELEASE_WORKFLOW_MAIN_HISTORY_REGRESSION_OK"
