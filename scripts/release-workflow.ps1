[CmdletBinding()]
param(
    [ValidateSet("All", "Validate", "Submit", "Release", "Deploy", "Verify")]
    [string]$Stage = "All",

    [string]$Branch = "feature/frontend-kubernetes",
    [string]$Version = "0.1.0",
    [string]$Namespace = "rag-document-assistant",
    [string]$KubeContext = "",
    [string]$CommitMessage = "feat: add portable Kubernetes deployment"
)

$ErrorActionPreference = "Stop"
$script:RepositoryRoot = Split-Path -Parent $PSScriptRoot
$script:ContainerWorkflow = "container-images.yml"
$script:BackendWorkflow = "backend-ci.yml"
$script:GitHubCli = $null
$script:RequiredPullRequestWorkflows = @(
    @{ File = $script:BackendWorkflow; Name = "Backend CI" },
    @{ File = $script:ContainerWorkflow; Name = "Container images" }
)
$script:PullRequestChecksTimeoutSeconds = 1800
$script:PullRequestChecksPollIntervalSeconds = 10
$script:PullRequestHeadRefreshTimeoutSeconds = 120
$script:PullRequestHeadRefreshPollIntervalSeconds = 2

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available in PATH: $Name"
    }
}

function Resolve-GitHubCli {
    $command = Get-Command "gh" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $standardPath = Join-Path $env:ProgramFiles "GitHub CLI/gh.exe"
    if (Test-Path -LiteralPath $standardPath -PathType Leaf) {
        return $standardPath
    }

    throw "Required command is not available: gh. Install GitHub CLI and authenticate with 'gh auth login'."
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    & $FilePath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code: $LASTEXITCODE)"
    }
}

function Confirm-Exact {
    param(
        [string]$Prompt,
        [string]$Expected
    )

    Write-Host ""
    Write-Host $Prompt -ForegroundColor Yellow
    $answer = Read-Host "Type exactly '$Expected' to continue"

    if ($answer -cne $Expected) {
        throw "Operation cancelled. Expected confirmation was not provided."
    }
}

function Get-GhJson {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:GitHubCli @Arguments 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        if ($AllowFailure) {
            return $null
        }

        throw "GitHub CLI command failed: gh $($Arguments -join ' ')"
    }

    $text = ($output | Out-String).Trim()

    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    return $text | ConvertFrom-Json
}

function Assert-ExpectedBranch {
    $currentBranch = (& git --no-pager branch --show-current | Out-String).Trim()

    if ($LASTEXITCODE -ne 0 -or $currentBranch -ne $Branch) {
        throw "Run this workflow from branch '$Branch'. Current branch: '$currentBranch'."
    }
}

function Assert-LocalSecretFilesAreUntracked {
    $localSecretFiles = @(
        "infra/k8s/backend-secret.env",
        "infra/k8s/postgres-secret.env"
    )

    foreach ($path in $localSecretFiles) {
        $trackedPath = (& git --no-pager ls-files -- $path | Out-String).Trim()

        if ($LASTEXITCODE -ne 0) {
            throw "Could not check whether a local secret file is tracked: $path"
        }

        if (-not [string]::IsNullOrWhiteSpace($trackedPath)) {
            throw "A local secret file is tracked by Git: $path"
        }
    }
}

function Invoke-SecretPatternCheck {
    Write-Step "Checking tracked and untracked source files for common secret formats"

    $pattern = "(sk-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)"
    $hits = @()

    $trackedHits = @(& git --no-pager grep -I -l -E $pattern -- . 2>$null)
    $trackedExitCode = $LASTEXITCODE

    if ($trackedExitCode -notin @(0, 1)) {
        throw "Git secret pattern check failed."
    }

    $hits += $trackedHits

    if (Get-Command rg -ErrorAction SilentlyContinue) {
        $untrackedHits = @(& rg -l --hidden --glob "!.git/**" $pattern . 2>$null)
        $rgExitCode = $LASTEXITCODE

        if ($rgExitCode -notin @(0, 1)) {
            throw "Untracked-file secret pattern check failed."
        }

        $hits += $untrackedHits
    }

    $hits = @($hits | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)

    if ($hits.Count -gt 0) {
        Write-Host "Potential secrets were detected in the following files (values are not displayed):" -ForegroundColor Red
        $hits | ForEach-Object { Write-Host " - $_" }
        throw "Review potential secrets before continuing."
    }

    Write-Host "No common secret formats detected."
}

function Invoke-ComposeValidation {
    Write-Step "Validating Docker Compose configuration with non-secret placeholders"

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "docker"
    $startInfo.Arguments = "compose config --quiet"
    $startInfo.WorkingDirectory = $script:RepositoryRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables["DB_PASSWORD"] = "validation-placeholder"
    $startInfo.EnvironmentVariables["OPENAI_API_KEY"] = "validation-placeholder"
    $startInfo.EnvironmentVariables["JWT_SECRET"] = "validation-placeholder-validation-placeholder"
    $startInfo.EnvironmentVariables["CONFIG_DIR"] = "C:\tmp\rag-config"
    $startInfo.EnvironmentVariables["STORAGE_DIR"] = "C:\tmp\rag-storage"

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($standardOutput)) {
            Write-Host $standardOutput
        }

        if (-not [string]::IsNullOrWhiteSpace($standardError)) {
            Write-Host $standardError
        }

        throw "Docker Compose configuration is invalid."
    }
}

function Invoke-Actionlint {
    Write-Step "Linting GitHub Actions workflows"

    if (Get-Command actionlint -ErrorAction SilentlyContinue) {
        Invoke-Checked -FilePath "actionlint" -Arguments @(
            ".github/workflows/backend-ci.yml",
            ".github/workflows/container-images.yml"
        ) -FailureMessage "actionlint failed"
        return
    }

    Invoke-Checked -FilePath "docker" -Arguments @(
        "run",
        "--rm",
        "--volume", "${script:RepositoryRoot}:/repo:ro",
        "--workdir", "/repo",
        "rhysd/actionlint:1.7.12",
        "-color",
        ".github/workflows/backend-ci.yml",
        ".github/workflows/container-images.yml"
    ) -FailureMessage "actionlint container failed"
}

function Invoke-FrontendValidation {
    $sourceDirectory = Join-Path $script:RepositoryRoot "frontend"
    $validationRoot = [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot "temp"))
    $temporaryDirectory = Join-Path $validationRoot ("rag-frontend-validation-" + [Guid]::NewGuid().ToString("N"))

    [void](New-Item -ItemType Directory -Path $validationRoot -Force)
    [void](New-Item -ItemType Directory -Path $temporaryDirectory)

    try {
        Write-Step "Copying frontend sources to an isolated temporary directory"
        & robocopy $sourceDirectory $temporaryDirectory /E `
            /XD node_modules .next out dist coverage `
            /XF .env ".env.*" "*.log" `
            /NFL /NDL /NJH /NJS /NP

        if ($LASTEXITCODE -ge 8) {
            throw "Could not prepare the isolated frontend validation directory (robocopy exit code: $LASTEXITCODE)."
        }

        Push-Location $temporaryDirectory
        try {
            Invoke-Checked -FilePath "npm" -Arguments @("ci") -FailureMessage "npm ci failed"
            Invoke-Checked -FilePath "npm" -Arguments @("run", "lint") -FailureMessage "Frontend lint failed"
            Invoke-Checked -FilePath "npm" -Arguments @("run", "typecheck") -FailureMessage "Frontend typecheck failed"
            Invoke-Checked -FilePath "npm" -Arguments @("run", "build") -FailureMessage "Frontend production build failed"
        }
        finally {
            Pop-Location
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporaryDirectory -PathType Container) {
            $resolvedTemporaryDirectory = [System.IO.Path]::GetFullPath($temporaryDirectory)
            $expectedPrefix = Join-Path $validationRoot "rag-frontend-validation-"

            if (-not $resolvedTemporaryDirectory.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove an unexpected temporary path: $resolvedTemporaryDirectory"
            }

            Remove-Item -LiteralPath $resolvedTemporaryDirectory -Recurse -Force
        }
    }
}

function Invoke-Validation {
    Write-Step "Checking local prerequisites"

    foreach ($command in @("git", "docker", "kubectl", "node", "npm", "robocopy")) {
        Assert-Command $command
    }

    Assert-ExpectedBranch
    Assert-LocalSecretFilesAreUntracked

    Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "diff", "--check") -FailureMessage "Working-tree whitespace check failed"
    Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "diff", "--cached", "--check") -FailureMessage "Staged whitespace check failed"
    Invoke-Checked -FilePath "docker" -Arguments @("info", "--format", "{{.ServerVersion}}") -FailureMessage "Docker engine is not available"

    Invoke-SecretPatternCheck

    Write-Step "Running backend tests"
    Push-Location (Join-Path $script:RepositoryRoot "backend")
    try {
        Invoke-Checked -FilePath ".\mvnw.cmd" -Arguments @("-B", "test") -FailureMessage "Backend tests failed"
    }
    finally {
        Pop-Location
    }

    Invoke-FrontendValidation

    Invoke-ComposeValidation

    Write-Step "Rendering Kubernetes manifests"
    & kubectl kustomize (Join-Path $script:RepositoryRoot "infra/k8s") | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Kubernetes manifests could not be rendered."
    }

    Invoke-Actionlint

    Write-Step "Local validation completed successfully"
    & git --no-pager status --short
}

function Assert-GitHubCliReady {
    $script:GitHubCli = Resolve-GitHubCli
    Invoke-Checked -FilePath $script:GitHubCli -Arguments @("auth", "status") -FailureMessage "GitHub CLI is not authenticated"
}

function Get-CurrentPullRequest {
    $pullRequests = @(Get-GhJson -Arguments @(
        "pr", "list",
        "--head", $Branch,
        "--base", "main",
        "--state", "all",
        "--limit", "1",
        "--json", "number,state,url,mergeCommit,headRefName,headRefOid,baseRefName"
    ))

    return $pullRequests | Select-Object -First 1
}

function Get-PullRequestByNumber {
    param([int]$PullRequestNumber)

    return Get-GhJson -Arguments @(
        "pr", "view", "$PullRequestNumber",
        "--json", "number,state,url,mergeCommit,headRefName,headRefOid,baseRefName"
    )
}

function Wait-PullRequestHead {
    param(
        [int]$PullRequestNumber,
        [string]$ExpectedHeadSha,
        [int]$TimeoutSeconds = $script:PullRequestHeadRefreshTimeoutSeconds,
        [int]$PollIntervalSeconds = $script:PullRequestHeadRefreshPollIntervalSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    while ($true) {
        $pullRequest = Get-PullRequestByNumber -PullRequestNumber $PullRequestNumber

        if ($pullRequest.state -ne "OPEN") {
            throw "Pull Request #$PullRequestNumber is not open. Current state: $($pullRequest.state)"
        }

        if ($pullRequest.headRefOid -eq $ExpectedHeadSha) {
            return $pullRequest
        }

        if ([DateTime]::UtcNow -ge $deadline) {
            throw "GitHub did not update Pull Request #$PullRequestNumber to expected head commit $ExpectedHeadSha. Current head: $($pullRequest.headRefOid)."
        }

        Write-Host "Waiting for GitHub to update Pull Request #$PullRequestNumber head from $($pullRequest.headRefOid) to $ExpectedHeadSha."
        Start-Sleep -Seconds $PollIntervalSeconds
    }
}

function Get-PullRequestWorkflowRun {
    param(
        [string]$Workflow,
        [string]$HeadSha
    )

    $runs = @(Get-GhJson -Arguments @(
        "run", "list",
        "--workflow", $Workflow,
        "--event", "pull_request",
        "--commit", $HeadSha,
        "--limit", "10",
        "--json", "databaseId,headSha,status,conclusion,url,workflowName,createdAt,attempt"
    ))

    return $runs |
        Where-Object { $_.headSha -eq $HeadSha } |
        Sort-Object -Property @(
            @{ Expression = { [DateTime]$_.createdAt }; Descending = $true },
            @{ Expression = { [int]$_.attempt }; Descending = $true }
        ) |
        Select-Object -First 1
}

function Wait-PullRequestChecks {
    param(
        [int]$PullRequestNumber,
        [string]$HeadSha,
        [int]$TimeoutSeconds = $script:PullRequestChecksTimeoutSeconds,
        [int]$PollIntervalSeconds = $script:PullRequestChecksPollIntervalSeconds
    )

    if ([string]::IsNullOrWhiteSpace($HeadSha)) {
        throw "Could not resolve the current head commit for Pull Request #$PullRequestNumber."
    }

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastStatusMessage = ""

    while ($true) {
        $missing = @()
        $pending = @()
        $failed = @()
        $passed = @()

        foreach ($requiredWorkflow in $script:RequiredPullRequestWorkflows) {
            $run = Get-PullRequestWorkflowRun -Workflow $requiredWorkflow.File -HeadSha $HeadSha

            if ($null -eq $run) {
                $missing += $requiredWorkflow.Name
                continue
            }

            $status = "$($run.status)".ToLowerInvariant()
            $conclusion = "$($run.conclusion)".ToLowerInvariant()
            $stateDescription = "$($requiredWorkflow.Name) - status=$status, conclusion=$conclusion, commit=$($run.headSha)"

            if ($status -ne "completed") {
                $pending += $stateDescription
            }
            elseif ($conclusion -eq "success") {
                $passed += $stateDescription
            }
            else {
                $failed += $stateDescription
            }
        }

        if ($failed.Count -gt 0) {
            Write-Host "Required Pull Request workflows failed or did not complete successfully:" -ForegroundColor Red
            $failed | ForEach-Object { Write-Host " - $_" }
            throw "Required Pull Request workflows did not pass for commit $HeadSha."
        }

        if ($missing.Count -eq 0 -and $pending.Count -eq 0) {
            Write-Host "Required Pull Request workflows passed for commit ${HeadSha}:" -ForegroundColor Green
            $passed | ForEach-Object { Write-Host " - $_" }
            return
        }

        $statusParts = @()
        if ($missing.Count -gt 0) {
            $statusParts += "not reported: $($missing -join '; ')"
        }
        if ($pending.Count -gt 0) {
            $statusParts += "pending: $($pending -join '; ')"
        }
        $statusMessage = $statusParts -join " | "

        if ($statusMessage -ne $lastStatusMessage) {
            Write-Host "Waiting for required Pull Request workflows on commit $HeadSha ($statusMessage)."
            $lastStatusMessage = $statusMessage
        }

        if ([DateTime]::UtcNow -ge $deadline) {
            if ($missing.Count -gt 0) {
                throw "Timed out after $TimeoutSeconds seconds. Required Pull Request workflows were not reported for commit ${HeadSha}: $($missing -join '; ')."
            }

            throw "Timed out after $TimeoutSeconds seconds. Required Pull Request workflows are still pending for commit ${HeadSha}: $($pending -join '; ')."
        }

        Start-Sleep -Seconds $PollIntervalSeconds
    }
}

function Invoke-Submit {
    Assert-ExpectedBranch
    Assert-GitHubCliReady

    $pullRequest = Get-CurrentPullRequest
    $continuingExistingPullRequest = $null -ne $pullRequest

    if ($continuingExistingPullRequest) {
        Write-Host "Continuing existing Pull Request #$($pullRequest.number): $($pullRequest.url)"

        if ($pullRequest.state -eq "MERGED") {
            Write-Host "Pull Request is already merged."
            return
        }

        if ($pullRequest.state -ne "OPEN") {
            throw "Pull Request is not open. Current state: $($pullRequest.state)"
        }
    }

    $changes = @(& git --no-pager status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read Git status."
    }

    if ($changes.Count -gt 0) {
        Write-Step "Changes proposed for the commit"
        & git --no-pager status --short

        $commitPrompt = "This will stage every file listed above and create one local commit."
        if ($continuingExistingPullRequest) {
            $commitPrompt += " The commit will update Pull Request #$($pullRequest.number) after the separately confirmed push."
        }

        Confirm-Exact -Prompt $commitPrompt -Expected "COMMIT"

        Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "add", "--all") -FailureMessage "git add failed"
        Assert-LocalSecretFilesAreUntracked
        Invoke-SecretPatternCheck
        Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "diff", "--cached", "--check") -FailureMessage "Staged whitespace check failed"

        Write-Step "Staged files"
        & git --no-pager diff --cached --name-status
        Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "commit", "-m", $CommitMessage) -FailureMessage "git commit failed"
    }
    else {
        Write-Host "Working tree is clean; no commit is needed."
    }

    $localHeadSha = (& git --no-pager rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($localHeadSha)) {
        throw "Could not resolve the local HEAD commit."
    }

    $remoteName = (& git --no-pager config --get "branch.$Branch.remote" | Out-String).Trim()
    $hasUpstream = $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($remoteName)
    $needsPush = -not $hasUpstream

    if ($hasUpstream) {
        $aheadText = (& git --no-pager rev-list --count "@{u}..HEAD" | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "Could not compare the local branch with its upstream."
        }
        $needsPush = [int]$aheadText -gt 0
    }

    $pushed = $false
    if ($needsPush) {
        $pushPrompt = "This will push branch '$Branch' to origin."
        if ($continuingExistingPullRequest) {
            $pushPrompt += " The push will update existing Pull Request #$($pullRequest.number)."
        }

        Confirm-Exact -Prompt $pushPrompt -Expected "PUSH"
        Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "push", "--set-upstream", "origin", $Branch) -FailureMessage "git push failed"
        $pushed = $true
    }
    else {
        Write-Host "The branch has no local commits waiting to be pushed."
    }

    if ($continuingExistingPullRequest) {
        $pullRequest = Get-PullRequestByNumber -PullRequestNumber $pullRequest.number
    }
    else {
        $pullRequest = Get-CurrentPullRequest
    }

    if ($null -eq $pullRequest) {
        Confirm-Exact -Prompt "This will create a Pull Request from '$Branch' to 'main'." -Expected "PR"

        $prBody = @"
## Summary

- add repeatable Kubernetes deployment for PostgreSQL, backend and frontend
- publish versioned backend and frontend images to GHCR
- keep frontend health checks and Origin validation suitable for local port-forward and future HTTPS

## Validation

- backend unit tests
- frontend lint, typecheck and production build
- Docker Compose configuration validation
- Kubernetes manifest rendering
- GitHub Actions lint
"@

        Invoke-Checked -FilePath $script:GitHubCli -Arguments @(
            "pr", "create",
            "--base", "main",
            "--head", $Branch,
            "--title", "feat: add portable Kubernetes deployment",
            "--body", $prBody
        ) -FailureMessage "Pull Request creation failed"

        $pullRequest = Get-CurrentPullRequest
    }

    if ($null -eq $pullRequest) {
        throw "Pull Request could not be resolved."
    }

    if ($pushed) {
        $pullRequest = Wait-PullRequestHead -PullRequestNumber $pullRequest.number -ExpectedHeadSha $localHeadSha
    }

    Write-Host "Pull Request: $($pullRequest.url)"

    if ($pullRequest.state -eq "MERGED") {
        Write-Host "Pull Request is already merged."
        return
    }

    if ($pullRequest.state -ne "OPEN") {
        throw "Pull Request is not open. Current state: $($pullRequest.state)"
    }

    Write-Step "Waiting for Pull Request checks"
    $validatedHeadSha = $pullRequest.headRefOid
    Wait-PullRequestChecks -PullRequestNumber $pullRequest.number -HeadSha $validatedHeadSha

    $pullRequestBeforeMergePrompt = Get-PullRequestByNumber -PullRequestNumber $pullRequest.number
    if ($pullRequestBeforeMergePrompt.state -ne "OPEN") {
        throw "Pull Request #$($pullRequest.number) is not open. Current state: $($pullRequestBeforeMergePrompt.state)"
    }
    if ($pullRequestBeforeMergePrompt.headRefOid -ne $validatedHeadSha) {
        throw "Pull Request head changed from $validatedHeadSha to $($pullRequestBeforeMergePrompt.headRefOid) while waiting for CI. Restart the Submit stage to validate the new commit."
    }

    Confirm-Exact -Prompt "All required checks passed. This will squash-merge Pull Request #$($pullRequest.number) into main." -Expected "MERGE"

    $pullRequestBeforeMerge = Get-PullRequestByNumber -PullRequestNumber $pullRequest.number
    if ($pullRequestBeforeMerge.state -ne "OPEN") {
        throw "Pull Request #$($pullRequest.number) is not open. Current state: $($pullRequestBeforeMerge.state)"
    }
    if ($pullRequestBeforeMerge.headRefOid -ne $validatedHeadSha) {
        throw "Pull Request head changed from $validatedHeadSha to $($pullRequestBeforeMerge.headRefOid) after merge confirmation. Merge was not attempted."
    }

    Wait-PullRequestChecks -PullRequestNumber $pullRequest.number -HeadSha $validatedHeadSha -TimeoutSeconds 0 -PollIntervalSeconds 0

    $pullRequestImmediatelyBeforeMerge = Get-PullRequestByNumber -PullRequestNumber $pullRequest.number
    if ($pullRequestImmediatelyBeforeMerge.headRefOid -ne $validatedHeadSha) {
        throw "Pull Request head changed immediately before merge. Merge was not attempted."
    }

    Invoke-Checked -FilePath $script:GitHubCli -Arguments @(
        "pr", "merge", "$($pullRequest.number)",
        "--squash",
        "--delete-branch=false",
        "--match-head-commit", $validatedHeadSha
    ) -FailureMessage "Squash merge failed"

    $mergedPullRequest = Get-CurrentPullRequest
    if ($null -eq $mergedPullRequest -or $mergedPullRequest.state -ne "MERGED") {
        throw "GitHub did not report the Pull Request as merged."
    }

    Write-Host "Pull Request merged as $($mergedPullRequest.mergeCommit.oid)."
}

function Resolve-VerifiedReleaseCommit {
    Write-Step "Verifying the current main branch before release"

    $localMain = (& git --no-pager rev-parse --verify refs/heads/main | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($localMain)) {
        throw "Could not resolve the local main branch."
    }

    $originMain = (& git --no-pager rev-parse --verify refs/remotes/origin/main | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($originMain)) {
        throw "Could not resolve origin/main. Fetch the current remote main branch before releasing."
    }

    $remoteMainLines = @(& git --no-pager ls-remote --exit-code --heads origin refs/heads/main)
    $remoteMainExitCode = $LASTEXITCODE
    $remoteMainLines = @($remoteMainLines | Where-Object { -not [string]::IsNullOrWhiteSpace("$_") })

    if ($remoteMainExitCode -ne 0 -or $remoteMainLines.Count -ne 1) {
        throw "Could not unambiguously resolve the actual origin main branch."
    }

    $remoteMainParts = @(("$($remoteMainLines[0])" -split "\s+") | Where-Object { $_ -ne "" })
    if ($remoteMainParts.Count -ne 2 -or $remoteMainParts[1] -ne "refs/heads/main" -or $remoteMainParts[0] -notmatch "^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$") {
        throw "The actual origin main branch returned an unexpected reference."
    }

    $remoteMain = $remoteMainParts[0].ToLowerInvariant()
    $localMain = $localMain.ToLowerInvariant()
    $originMain = $originMain.ToLowerInvariant()

    if ($originMain -ne $remoteMain) {
        throw "origin/main does not match the actual remote main branch. Fetch origin and review the rewritten history before releasing."
    }

    if ($localMain -ne $remoteMain) {
        throw "The local main branch does not match origin/main and the actual remote main branch. Update local main from the cleaned remote history before releasing."
    }

    & git --no-pager cat-file -e "$remoteMain`^{commit}"
    if ($LASTEXITCODE -ne 0) {
        throw "The verified main reference does not identify a local commit object."
    }

    & git --no-pager merge-base --is-ancestor $remoteMain refs/heads/main
    if ($LASTEXITCODE -ne 0) {
        throw "The verified release commit is not reachable from the current local main history."
    }

    Write-Host "Release commit verified from local main, origin/main and the actual remote main: $remoteMain" -ForegroundColor Green
    return $remoteMain
}

function Wait-GitHubWorkflow {
    param(
        [string]$Workflow,
        [string]$Ref,
        [string]$Commit = ""
    )

    Write-Step "Waiting for GitHub Actions workflow '$Workflow' on '$Ref'"

    $run = $null
    for ($attempt = 1; $attempt -le 30 -and $null -eq $run; $attempt++) {
        $arguments = @(
            "run", "list",
            "--workflow", $Workflow,
            "--branch", $Ref,
            "--event", "push",
            "--limit", "10",
            "--json", "databaseId,headSha,status,conclusion,url"
        )

        $runs = @(Get-GhJson -Arguments $arguments)

        if (-not [string]::IsNullOrWhiteSpace($Commit)) {
            $run = $runs | Where-Object { $_.headSha -eq $Commit } | Select-Object -First 1
        }
        else {
            $run = $runs | Select-Object -First 1
        }

        if ($null -eq $run) {
            Start-Sleep -Seconds 5
        }
    }

    if ($null -eq $run) {
        throw "No matching GitHub Actions run appeared for workflow '$Workflow'."
    }

    Write-Host "Workflow run: $($run.url)"
    Invoke-Checked -FilePath $script:GitHubCli -Arguments @("run", "watch", "$($run.databaseId)", "--exit-status") -FailureMessage "Workflow '$Workflow' failed"
}

function Test-PublicImage {
    param([string]$Image)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker manifest inspect $Image *> $null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Assert-ReleaseImagesArePublic {
    param([switch]$WaitForPublic)

    $images = @(
        "ghcr.io/raf-83/rag-document-assistant-backend:$Version",
        "ghcr.io/raf-83/rag-document-assistant-frontend:$Version"
    )

    do {
        $unavailable = @()
        foreach ($image in $images) {
            if (-not (Test-PublicImage -Image $image)) {
                $unavailable += $image
            }
        }

        if ($unavailable.Count -eq 0) {
            return
        }

        Write-Host "The following release images are not anonymously downloadable:" -ForegroundColor Yellow
        $unavailable | ForEach-Object { Write-Host " - $_" }

        if (-not $WaitForPublic) {
            throw "Set both GHCR packages to Public in GitHub package settings, then retry."
        }

        Confirm-Exact -Prompt "Set both newly published GHCR packages to Public in GitHub package settings. The script will only perform another anonymous read check." -Expected "PUBLIC"
    } while ($true)
}

function Invoke-Release {
    Assert-GitHubCliReady

    $releaseCommit = Resolve-VerifiedReleaseCommit

    $repository = Get-GhJson -Arguments @("repo", "view", "--json", "nameWithOwner,visibility")
    if ($repository.visibility -ne "PUBLIC") {
        throw "The repository is not public. Make it public before v$Version so the release pipeline can generate provenance attestations."
    }

    Wait-GitHubWorkflow -Workflow $script:BackendWorkflow -Ref "main" -Commit $releaseCommit
    Wait-GitHubWorkflow -Workflow $script:ContainerWorkflow -Ref "main" -Commit $releaseCommit

    $existingRelease = Get-GhJson -Arguments @("release", "view", "v$Version", "--json", "tagName,url") -AllowFailure

    if ($null -eq $existingRelease) {
        Confirm-Exact -Prompt "This will create GitHub release v$Version at $releaseCommit. Pushing its tag automatically publishes both multi-platform images to GHCR." -Expected "RELEASE v$Version"

        $releaseCommitBeforeCreation = Resolve-VerifiedReleaseCommit
        if ($releaseCommitBeforeCreation -ne $releaseCommit) {
            throw "The verified main commit changed while preparing the release. Release creation was not attempted."
        }

        Invoke-Checked -FilePath $script:GitHubCli -Arguments @(
            "release", "create", "v$Version",
            "--target", $releaseCommit,
            "--title", "v$Version",
            "--generate-notes"
        ) -FailureMessage "GitHub release creation failed"
    }
    else {
        Write-Host "Release already exists: $($existingRelease.url)"
    }

    Wait-GitHubWorkflow -Workflow $script:ContainerWorkflow -Ref "v$Version" -Commit $releaseCommit
    Assert-ReleaseImagesArePublic -WaitForPublic

    Write-Step "Verifying image provenance attestations"
    foreach ($component in @("backend", "frontend")) {
        $image = "oci://ghcr.io/raf-83/rag-document-assistant-${component}:$Version"
        Invoke-Checked -FilePath $script:GitHubCli -Arguments @(
            "attestation", "verify", $image,
            "--repo", $repository.nameWithOwner
        ) -FailureMessage "Attestation verification failed for $component"
    }
}

function Get-DeploymentContext {
    if (-not [string]::IsNullOrWhiteSpace($KubeContext)) {
        return $KubeContext
    }

    $context = (& kubectl config current-context | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
        throw "Could not resolve the current Kubernetes context."
    }

    return $context
}

function Invoke-Deploy {
    Assert-Command "kubectl"
    Assert-Command "docker"
    Assert-ReleaseImagesArePublic

    $backendSecretFile = Join-Path $script:RepositoryRoot "infra/k8s/backend-secret.env"
    if (-not (Test-Path -LiteralPath $backendSecretFile -PathType Leaf)) {
        throw "Missing local secret file: infra/k8s/backend-secret.env. See infra/k8s/SECRETS.md."
    }

    $context = Get-DeploymentContext
    $availableContexts = @(& kubectl config get-contexts -o name)
    if ($LASTEXITCODE -ne 0 -or $context -notin $availableContexts) {
        throw "Kubernetes context does not exist: $context"
    }

    Write-Host "Kubernetes context: $context"
    Write-Host "Namespace: $Namespace"
    Confirm-Exact -Prompt "This will create or update the application, PostgreSQL, persistent volumes and Kubernetes Secrets in the selected cluster." -Expected "DEPLOY $context"

    $namespaceManifest = & kubectl --context=$context create namespace $Namespace --dry-run=client -o yaml
    if ($LASTEXITCODE -ne 0) {
        throw "Could not generate the namespace manifest."
    }

    $namespaceManifest | & kubectl --context=$context apply -f -
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create or update the namespace."
    }

    & (Join-Path $script:RepositoryRoot "infra/k8s/scripts/sync-backend-secret.ps1") -KubeContext $context -Namespace $Namespace
    & (Join-Path $script:RepositoryRoot "infra/k8s/scripts/sync-postgres-secret.ps1") -KubeContext $context -Namespace $Namespace

    Invoke-Checked -FilePath "kubectl" -Arguments @(
        "--context=$context", "apply", "-k", (Join-Path $script:RepositoryRoot "infra/k8s"), "--namespace=$Namespace"
    ) -FailureMessage "Kubernetes deployment failed"

    Invoke-Verify -Context $context
}

function Invoke-Verify {
    param([string]$Context = "")

    if ([string]::IsNullOrWhiteSpace($Context)) {
        $Context = Get-DeploymentContext
    }

    Write-Step "Waiting for Kubernetes rollouts"
    foreach ($deployment in @("postgres", "backend", "frontend")) {
        Invoke-Checked -FilePath "kubectl" -Arguments @(
            "--context=$Context",
            "--namespace=$Namespace",
            "rollout", "status", "deployment/$deployment",
            "--timeout=300s"
        ) -FailureMessage "Deployment '$deployment' did not become ready"
    }

    Write-Step "Checking PostgreSQL and application health endpoints"
    Invoke-Checked -FilePath "kubectl" -Arguments @(
        "--context=$Context", "--namespace=$Namespace",
        "exec", "deployment/postgres", "--",
        "pg_isready", "-U", "postgres", "-d", "ai_document_vault"
    ) -FailureMessage "PostgreSQL health check failed"

    $healthCheck = @"
const urls = [
  'http://127.0.0.1:3000/api/health/live',
  'http://127.0.0.1:3000/api/health',
  'http://backend:8080/api/health'
];
(async () => {
  for (const url of urls) {
    const response = await fetch(url);
    console.log(url + ' -> ' + response.status);
    if (!response.ok) process.exitCode = 1;
  }
})().catch(error => {
  console.error(error.message);
  process.exit(1);
});
"@

    Invoke-Checked -FilePath "kubectl" -Arguments @(
        "--context=$Context", "--namespace=$Namespace",
        "exec", "deployment/frontend", "--",
        "node", "-e", $healthCheck
    ) -FailureMessage "Application health checks failed"

    & kubectl --context=$Context --namespace=$Namespace get deployments,pods,services
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read final Kubernetes status."
    }

    Write-Host ""
    Write-Host "Deployment verified. Open the application with:" -ForegroundColor Green
    Write-Host "kubectl --context=$Context --namespace=$Namespace port-forward service/frontend 13000:3000"
    Write-Host "Then visit http://localhost:13000"
}

Set-Location $script:RepositoryRoot

try {
    switch ($Stage) {
        "Validate" {
            Invoke-Validation
        }
        "Submit" {
            Invoke-Submit
        }
        "Release" {
            Invoke-Release
        }
        "Deploy" {
            Invoke-Deploy
        }
        "Verify" {
            Invoke-Verify
        }
        "All" {
            Invoke-Validation
            Invoke-Submit
            Invoke-Release
            Invoke-Deploy
        }
    }
}
finally {
    Set-Location $script:RepositoryRoot
}
