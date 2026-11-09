[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $projectRoot "scripts/common/submit-proposal.ps1")

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

function Invoke-TestGit {
    param([string[]]$Arguments)
    & git @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Test Git command failed: git $($Arguments -join ' ')"
    }
}

function Invoke-Checked {
    param([string]$FilePath, [string[]]$Arguments, [string]$FailureMessage)
    & $FilePath @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw $FailureMessage }
}

$testRootParent = Join-Path $projectRoot "temp"
$testRoot = Join-Path $testRootParent ("submit-proposal-regression-" + [Guid]::NewGuid().ToString("N"))
[void](New-Item -ItemType Directory -Path $testRoot -Force)

try {
    Push-Location $testRoot
    try {
        Invoke-TestGit -Arguments @("init", "--quiet")
        Invoke-TestGit -Arguments @("config", "user.name", "Regression Test")
        Invoke-TestGit -Arguments @("config", "user.email", "regression@example.invalid")

        [System.IO.File]::WriteAllText((Join-Path $testRoot ".gitignore"), "ignored.txt`n.env`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "tracked.txt"), "base`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "staged.txt"), "base`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "tracked-secret.key"), "test-only secret placeholder`n")
        [void](New-Item -ItemType Directory -Path (Join-Path $testRoot "docs") -Force)
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/intelligent-submit-workflow-filter-fix-report.md"), "tracked local report`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/intelligent-submit-pr-detection-fix-report.md"), "tracked local report`n")
        Invoke-TestGit -Arguments @("add", "--", ".gitignore", "tracked.txt", "staged.txt", "tracked-secret.key", "docs/intelligent-submit-workflow-filter-fix-report.md", "docs/intelligent-submit-pr-detection-fix-report.md")
        Invoke-TestGit -Arguments @("commit", "--quiet", "-m", "test base")

        $workflowPath = Join-Path $projectRoot "scripts/release-workflow.ps1"
        $tokens = $null
        $parseErrors = $null
        $workflowAst = [System.Management.Automation.Language.Parser]::ParseFile($workflowPath, [ref]$tokens, [ref]$parseErrors)
        Assert-Equal 0 $parseErrors.Count "release-workflow.ps1 did not parse for branch regression coverage."
        $branchFunctionAst = $workflowAst.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq "Resolve-SubmitBranch"
            }, $true) | Select-Object -First 1
        . ([scriptblock]::Create($branchFunctionAst.Extent.Text))
        function Assert-ExpectedBranch {
            $actualBranch = (& git branch --show-current | Out-String).Trim()
            if ($actualBranch -ne $Branch) { throw "Unexpected branch." }
        }
        $script:BranchParameterWasProvided = $false
        $Branch = "obsolete-default"
        $protectedBranchRejected = $false
        try { Resolve-SubmitBranch }
        catch { $protectedBranchRejected = $_.Exception.Message -like "Submit requires a working branch*" }
        Assert-True $protectedBranchRejected "Submit did not reject the protected default branch."
        Invoke-TestGit -Arguments @("switch", "--quiet", "-c", "feature/test")
        Resolve-SubmitBranch
        Assert-Equal "feature/test" $Branch "Submit did not infer the current working branch."

        [System.IO.File]::AppendAllText((Join-Path $testRoot ".gitignore"), "docs/intelligent-submit-workflow-filter-fix-report.md`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "tracked.txt"), "changed`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "staged.txt"), "staged change`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "untracked.txt"), "new file`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "ignored.txt"), "ignored`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot ".env"), "PASSWORD=not-for-codex`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/final-project-documentation-report.md"), "local report`n")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/intelligent-submit-pr-detection-fix-report.md"), "modified tracked local report`n")
        Remove-Item -LiteralPath (Join-Path $testRoot "docs/intelligent-submit-workflow-filter-fix-report.md")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/intelligent-submit-local-preparation-report.md"), "staged-only local report`n")
        Invoke-TestGit -Arguments @("add", "--", "docs/intelligent-submit-local-preparation-report.md")
        Remove-Item -LiteralPath (Join-Path $testRoot "docs/intelligent-submit-local-preparation-report.md")
        Remove-Item -LiteralPath (Join-Path $testRoot "tracked-secret.key")
        $fakeSecret = "sk-" + ("a" * 26)
        [System.IO.File]::WriteAllText((Join-Path $testRoot "leak.txt"), "token=$fakeSecret`n")
        Invoke-TestGit -Arguments @("add", "--", "staged.txt")

        $candidatePaths = @(Get-SubmitCandidatePaths -RepositoryRoot $testRoot)
        Assert-True ($candidatePaths -contains "tracked.txt") "An unstaged tracked file was not offered."
        Assert-True ($candidatePaths -contains "staged.txt") "A staged tracked file was not offered."
        Assert-True ($candidatePaths -contains "untracked.txt") "A safe untracked file was not offered."
        Assert-True ($candidatePaths -notcontains "ignored.txt") "An ignored file was offered to Codex."
        Assert-True ($candidatePaths -notcontains ".env") "An ignored secret file was offered to Codex."
        Assert-True ($candidatePaths -notcontains "docs/final-project-documentation-report.md") "A local report was offered to Codex."
        Assert-True ($candidatePaths -notcontains "docs/intelligent-submit-pr-detection-fix-report.md") "A modified tracked local report was offered to Codex."
        Assert-True ($candidatePaths -notcontains "docs/intelligent-submit-local-preparation-report.md") "A report added only to the index and then removed was treated as a previously tracked deletion."
        Assert-True ($candidatePaths -contains "docs/intelligent-submit-workflow-filter-fix-report.md") "The tracked deletion of a local report was not offered."
        Assert-True ($candidatePaths -notcontains "tracked-secret.key") "Deletion of a tracked secret path was offered to Codex."
        Assert-True ($candidatePaths -notcontains "leak.txt") "A file containing a common secret format was offered to Codex."

        $codexContext = Get-SubmitCodexContext -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-True ($codexContext -match "changed") "The tracked diff was not included in the bounded Codex context."
        Assert-True ($codexContext -match "new file") "The untracked file was not included in the bounded Codex context."
        Assert-True (-not $codexContext.Contains("not-for-codex")) "An ignored secret value reached the Codex context."
        Assert-True (-not $codexContext.Contains($fakeSecret)) "A detected secret value reached the Codex context."

        $validJson = @{
            files = @("tracked.txt", "untracked.txt")
            commitMessage = "test: cover intelligent submit"
            pullRequestTitle = "test: cover intelligent submit"
            pullRequestBody = "## Summary`n`n- cover Submit`n`n## Tests`n`n- regression test"
        } | ConvertTo-Json -Depth 4
        $validProposal = ConvertFrom-SubmitProposalJson -Json $validJson -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal 2 $validProposal.Files.Count "A valid structured proposal lost files."
        Assert-Equal "test: cover intelligent submit" $validProposal.CommitMessage "The commit message changed while parsing."

        $deletedReportPath = "docs/intelligent-submit-workflow-filter-fix-report.md"
        $deletedReportJson = @{
            files = @($deletedReportPath)
            commitMessage = "chore: remove local report"
            pullRequestTitle = "chore: remove local report"
            pullRequestBody = "## Summary`n`n- remove a local report`n`n## Tests`n`n- regression test"
        } | ConvertTo-Json -Depth 4
        $deletedReportProposal = ConvertFrom-SubmitProposalJson -Json $deletedReportJson -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal $deletedReportPath $deletedReportProposal.Files[0] "Codex proposal validation rejected a tracked local-report deletion."

        $invalidJsonRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json "{broken" -CandidatePaths $candidatePaths -RepositoryRoot $testRoot | Out-Null }
        catch { $invalidJsonRejected = $_.Exception.Message -like "Codex returned invalid proposal JSON*" }
        Assert-True $invalidJsonRejected "Malformed Codex JSON was accepted."

        $emptySectionsJson = @{
            files = @("tracked.txt")
            commitMessage = "test: empty body"
            pullRequestTitle = "test: empty body"
            pullRequestBody = "## Summary`n`n## Tests"
        } | ConvertTo-Json -Depth 4
        $emptySectionsRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json $emptySectionsJson -CandidatePaths $candidatePaths -RepositoryRoot $testRoot | Out-Null }
        catch { $emptySectionsRejected = $true }
        Assert-True $emptySectionsRejected "Empty Pull Request summary/test sections were accepted."

        foreach ($unsafePath in @("../outside.txt", ".env", "docs/final-project-documentation-report.md", "not-a-candidate.txt")) {
            $unsafeJson = @{
                files = @($unsafePath)
                commitMessage = "test: unsafe"
                pullRequestTitle = "test: unsafe"
                pullRequestBody = "## Summary`n`n- unsafe`n`n## Tests`n`n- not run"
            } | ConvertTo-Json -Depth 4
            $unsafeRejected = $false
            try { ConvertFrom-SubmitProposalJson -Json $unsafeJson -CandidatePaths $candidatePaths -RepositoryRoot $testRoot | Out-Null }
            catch { $unsafeRejected = $true }
            Assert-True $unsafeRejected "Unsafe proposal path '$unsafePath' was accepted."
        }

        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        foreach ($response in @("", "test: edited proposal", "", "")) { $script:ReadHostResponses.Enqueue($response) }
        function Read-Host { param([string]$Prompt) return $script:ReadHostResponses.Dequeue() }
        $edited = Edit-SubmitProposal -Proposal $validProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal "test: edited proposal" $edited.CommitMessage "Proposal editing did not update the commit message."
        Assert-Equal $validProposal.PullRequestTitle $edited.PullRequestTitle "Enter should keep the current PR title."

        $deletedReportSelection = [Array]::IndexOf($candidatePaths, $deletedReportPath) + 1
        Assert-True ($deletedReportSelection -gt 0) "The deleted local report is missing from manual selection candidates."
        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        foreach ($response in @("$deletedReportSelection", "chore: remove local report", "", "remove local report", "regression test")) {
            $script:ReadHostResponses.Enqueue($response)
        }
        $manualDeletedReportProposal = New-ManualSubmitProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal $deletedReportPath $manualDeletedReportProposal.Files[0] "Manual proposal validation rejected a tracked local-report deletion."

        function Resolve-CodexCli { return $null }
        function New-ManualSubmitProposal {
            param([string[]]$CandidatePaths, [string]$RepositoryRoot, [string]$DefaultCommitMessage)
            return $validProposal
        }
        function Show-SubmitProposal { param([object]$Proposal) }
        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        $script:ReadHostResponses.Enqueue("a")
        $manualFallback = Get-ApprovedSubmitProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal $validProposal.CommitMessage $manualFallback.CommitMessage "Unavailable Codex did not use manual mode."

        function Resolve-CodexCli { return "codex-test" }
        function Invoke-CodexSubmitProposal {
            param([string]$CodexPath, [string[]]$CandidatePaths, [string]$RepositoryRoot)
            throw "Codex returned invalid proposal JSON."
        }
        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        $script:ReadHostResponses.Enqueue("a")
        $invalidJsonFallback = Get-ApprovedSubmitProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot
        Assert-Equal $validProposal.CommitMessage $invalidJsonFallback.CommitMessage "Invalid Codex JSON did not fall back to manual mode."

        function Resolve-CodexCli { return $null }
        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        $script:ReadHostResponses.Enqueue("c")
        $cancelled = $false
        try { Get-ApprovedSubmitProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot | Out-Null }
        catch { $cancelled = $_.Exception.Message -eq "Submit proposal cancelled. No files were staged." }
        Assert-True $cancelled "Cancelling a proposal did not stop Submit."

        function Resolve-CodexCli { return "codex-test" }
        $script:CodexProposalInvocationCount = 0
        function Invoke-CodexSubmitProposal {
            param([string]$CodexPath, [string[]]$CandidatePaths, [string]$RepositoryRoot)
            $script:CodexProposalInvocationCount++
            return $validProposal
        }
        $script:ReadHostResponses = [System.Collections.Generic.Queue[string]]::new()
        $script:ReadHostResponses.Enqueue("r")
        $script:ReadHostResponses.Enqueue("a")
        Get-ApprovedSubmitProposal -CandidatePaths $candidatePaths -RepositoryRoot $testRoot | Out-Null
        Assert-Equal 2 $script:CodexProposalInvocationCount "Regenerate did not request a fresh Codex proposal."

        Set-SubmitStagingSelection -SelectedPaths @($deletedReportPath) -RepositoryRoot $testRoot
        $stagedDeletionPaths = @(Get-SubmitStagedPaths | Sort-Object)
        Assert-Equal $deletedReportPath ($stagedDeletionPaths -join "`n") "Exact staging did not preserve the selected local-report deletion."

        Set-SubmitStagingSelection -SelectedPaths @("tracked.txt", "untracked.txt") -RepositoryRoot $testRoot
        $stagedPaths = @(Get-SubmitStagedPaths | Sort-Object)
        Assert-Equal "tracked.txt`nuntracked.txt" ($stagedPaths -join "`n") "Staging included files outside the accepted selection."

        Invoke-TestGit -Arguments @("commit", "--quiet", "-m", "test selected staging")
        $headSha = (& git rev-parse HEAD | Out-String).Trim()
        $script:RepositoryRoot = $testRoot
        Save-SubmitProposalState -Proposal $validProposal -Branch "feature/test" -HeadSha $headSha
        $resumed = Get-SubmitProposalState -Branch "feature/test" -HeadSha $headSha
        Assert-Equal $validProposal.PullRequestTitle $resumed.PullRequestTitle "Interrupted Submit state was not resumed."
        Assert-True ($null -eq (Get-SubmitProposalState -Branch "feature/test" -HeadSha ("0" * 40))) "Stale Submit state was reused for another commit."
        Remove-SubmitProposalState

        $workflowText = [System.IO.File]::ReadAllText((Join-Path $projectRoot "scripts/release-workflow.ps1"))
        Assert-True ($workflowText -notmatch 'git[^\r\n]+add[^\r\n]+--all') "Submit still uses unconditional git add --all."
        Assert-True ($workflowText -match '--match-head-commit') "Squash merge lost exact-head protection."
        Assert-True ($workflowText -match 'Wait-PullRequestChecks[^\r\n]+-TimeoutSeconds 0') "The final immediate CI check was removed."

        $helperText = [System.IO.File]::ReadAllText((Join-Path $projectRoot "scripts/common/submit-proposal.ps1"))
        foreach ($requiredOption in @('--sandbox", "read-only', '--disable", "shell_tool', '--ephemeral', '--ignore-user-config', '--output-schema', '--output-last-message')) {
            Assert-True ($helperText.Contains($requiredOption)) "Codex invocation is missing: $requiredOption"
        }

        Invoke-TestGit -Arguments @("add", "--update", "--", "docs/intelligent-submit-workflow-filter-fix-report.md")
        Invoke-TestGit -Arguments @("commit", "--quiet", "-m", "remove local report")
        [System.IO.File]::WriteAllText((Join-Path $testRoot "docs/intelligent-submit-workflow-filter-fix-report.md"), "recreated local report`n")
        $postDeletionCandidates = @(Get-SubmitCandidatePaths -RepositoryRoot $testRoot)
        Assert-True ($postDeletionCandidates -notcontains "docs/intelligent-submit-workflow-filter-fix-report.md") "A recreated local report was offered again after its tracked deletion."

        $recreatedReportRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json $deletedReportJson -CandidatePaths @($deletedReportPath) -RepositoryRoot $testRoot | Out-Null }
        catch { $recreatedReportRejected = $_.Exception.Message -like "The proposal contains an excluded path*" }
        Assert-True $recreatedReportRejected "Proposal validation accepted an added or modified excluded report."

        $recreatedReportStagingRejected = $false
        try { Set-SubmitStagingSelection -SelectedPaths @($deletedReportPath) -RepositoryRoot $testRoot }
        catch { $recreatedReportStagingRejected = $_.Exception.Message -like "Refusing to stage an excluded path*" }
        Assert-True $recreatedReportStagingRejected "Staging accepted an added or modified excluded report."

        $modifiedReportPath = "docs/intelligent-submit-pr-detection-fix-report.md"
        $modifiedReportJson = $deletedReportJson.Replace($deletedReportPath, $modifiedReportPath)
        $modifiedReportRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json $modifiedReportJson -CandidatePaths @($modifiedReportPath) -RepositoryRoot $testRoot | Out-Null }
        catch { $modifiedReportRejected = $_.Exception.Message -like "The proposal contains an excluded path*" }
        Assert-True $modifiedReportRejected "Proposal validation accepted a modified excluded report."

        $indexOnlyReportPath = "docs/intelligent-submit-local-preparation-report.md"
        $indexOnlyReportJson = $deletedReportJson.Replace($deletedReportPath, $indexOnlyReportPath)
        $indexOnlyReportRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json $indexOnlyReportJson -CandidatePaths @($indexOnlyReportPath) -RepositoryRoot $testRoot | Out-Null }
        catch { $indexOnlyReportRejected = $_.Exception.Message -like "The proposal contains an excluded path*" }
        Assert-True $indexOnlyReportRejected "Proposal validation accepted deletion of a report that was never tracked in HEAD."

        $secretDeletionRejected = $false
        try { ConvertFrom-SubmitProposalJson -Json $deletedReportJson.Replace($deletedReportPath, "tracked-secret.key") -CandidatePaths @("tracked-secret.key") -RepositoryRoot $testRoot | Out-Null }
        catch { $secretDeletionRejected = $_.Exception.Message -like "The proposal contains an excluded path*" }
        Assert-True $secretDeletionRejected "Proposal validation accepted deletion of an excluded secret path."

        $secretDeletionStagingRejected = $false
        try { Set-SubmitStagingSelection -SelectedPaths @("tracked-secret.key") -RepositoryRoot $testRoot }
        catch { $secretDeletionStagingRejected = $_.Exception.Message -like "Refusing to stage an excluded path*" }
        Assert-True $secretDeletionStagingRejected "Staging accepted deletion of an excluded secret path."
    }
    finally {
        Pop-Location
    }
}
finally {
    if (Test-Path -LiteralPath $testRoot -PathType Container) {
        $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
        $expectedPrefix = [System.IO.Path]::GetFullPath((Join-Path $testRootParent "submit-proposal-regression-"))
        if (-not $resolvedTestRoot.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected regression-test path: $resolvedTestRoot"
        }
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}

Write-Output "SUBMIT_PROPOSAL_REGRESSION_OK"
