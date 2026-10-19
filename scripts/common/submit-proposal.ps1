$script:SubmitLocalReportPaths = @(
    "docs/final-git-workflow-report.md",
    "docs/final-project-documentation-report.md",
    "docs/intelligent-submit-implementation-report.md",
    "docs/intelligent-submit-live-integration-test-report.md",
    "docs/intelligent-submit-pr-detection-fix-report.md",
    "docs/public-v0.1.0-repository-preparation-report.md"
)

$script:SubmitSecretPathPatterns = @(
    "(^|/)infra/k8s/(backend|postgres)-secret\.env$",
    "(^|/)\.env($|\.)",
    "(^|/)(id_rsa|id_dsa|id_ecdsa|id_ed25519)$",
    "\.(key|pem|p12|pfx)$"
)

function ConvertTo-SubmitRepositoryPath {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$RepositoryRoot
    )

    $candidate = $Path.Trim().Replace("\", "/")
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        throw "The proposed file path is empty."
    }
    if ([System.IO.Path]::IsPathRooted($candidate) -or $candidate -match "(^|/)\.\.(/|$)" -or $candidate -match "[\r\n]") {
        throw "The proposed path is not a repository-relative path: $Path"
    }

    while ($candidate.StartsWith("./", [System.StringComparison]::Ordinal)) {
        $candidate = $candidate.Substring(2)
    }

    $root = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $root $candidate))
    $rootPrefix = $root + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The proposed path resolves outside the repository: $Path"
    }

    return $candidate
}

function Test-SubmitPathIsExcluded {
    param([Parameter(Mandatory)][string]$Path)

    $normalized = $Path.Replace("\", "/")
    if ($normalized -in $script:SubmitLocalReportPaths) {
        return $true
    }

    foreach ($pattern in $script:SubmitSecretPathPatterns) {
        if ($normalized -match $pattern) {
            if ($normalized -eq ".env.example" -or $normalized.EndsWith("/.env.example", [System.StringComparison]::OrdinalIgnoreCase)) {
                continue
            }
            return $true
        }
    }

    return $false
}

function Test-SubmitPathIsGitIgnored {
    param([Parameter(Mandatory)][string]$Path)

    & git --no-pager check-ignore --no-index --quiet -- $Path
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        return $true
    }
    if ($exitCode -eq 1) {
        return $false
    }
    throw "Git could not check ignore rules for: $Path"
}

function Test-SubmitPathUsesReparsePoint {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RepositoryRoot
    )

    $root = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    $currentPath = [System.IO.Path]::GetFullPath((Join-Path $root $Path))
    while ($currentPath.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) -and $currentPath -ne $root) {
        if (Test-Path -LiteralPath $currentPath) {
            $item = Get-Item -LiteralPath $currentPath -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
        }
        $parent = Split-Path -Parent $currentPath
        if ($parent -eq $currentPath) { break }
        $currentPath = $parent
    }
    return $false
}

function Test-SubmitFileContainsSecret {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RepositoryRoot
    )

    $fullPath = Join-Path $RepositoryRoot $Path
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        return $false
    }

    $file = Get-Item -LiteralPath $fullPath
    if ($file.Length -gt 5MB) {
        return $false
    }

    try {
        $text = [System.IO.File]::ReadAllText($file.FullName)
    }
    catch {
        return $false
    }

    $secretPattern = "(?im)(sk-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)"
    return $text -match $secretPattern
}

function Get-SubmitGitPathOutput {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $output = @(& git @Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "Git could not enumerate Submit candidates: git $($Arguments -join ' ')"
    }

    return @($output | ForEach-Object { "$_" } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-SubmitCandidatePaths {
    param([Parameter(Mandatory)][string]$RepositoryRoot)

    $paths = @()
    $paths += Get-SubmitGitPathOutput -Arguments @("--no-pager", "-c", "core.quotepath=false", "diff", "--name-only", "--", ".")
    $paths += Get-SubmitGitPathOutput -Arguments @("--no-pager", "-c", "core.quotepath=false", "diff", "--cached", "--name-only", "--", ".")
    $paths += Get-SubmitGitPathOutput -Arguments @("--no-pager", "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard", "--", ".")

    $safePaths = @()
    $excludedPaths = @()
    foreach ($path in @($paths | Sort-Object -Unique)) {
        $normalized = ConvertTo-SubmitRepositoryPath -Path $path -RepositoryRoot $RepositoryRoot
        if ((Test-SubmitPathIsExcluded -Path $normalized) -or
            (Test-SubmitPathIsGitIgnored -Path $normalized) -or
            (Test-SubmitPathUsesReparsePoint -Path $normalized -RepositoryRoot $RepositoryRoot) -or
            (Test-SubmitFileContainsSecret -Path $normalized -RepositoryRoot $RepositoryRoot)) {
            $excludedPaths += $normalized
            continue
        }
        $safePaths += $normalized
    }

    if ($excludedPaths.Count -gt 0) {
        Write-Host "Excluded from the Submit proposal (contents are not displayed):" -ForegroundColor Yellow
        $excludedPaths | ForEach-Object { Write-Host " - $_" }
    }

    return @($safePaths)
}

function Protect-SubmitContextText {
    param([AllowEmptyString()][string]$Text)

    if ([string]::IsNullOrEmpty($Text)) {
        return $Text
    }

    $protected = $Text -replace "(?im)^([+\- ]?[^\r\n=:\s]*(?:password|secret|token|api[_-]?key|private[_-]?key)[^\r\n=:]*\s*[=:]\s*).*$", '$1[REDACTED]'
    $protected = $protected -replace "(?is)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "[PRIVATE KEY REDACTED]"
    return $protected
}

function Get-SubmitCodexContext {
    param(
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [int]$MaximumCharacters = 800000
    )

    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.AppendLine("Candidate repository-relative paths:")
    foreach ($path in $CandidatePaths) {
        [void]$builder.AppendLine("- $path")
    }

    foreach ($path in $CandidatePaths) {
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("===== $path =====")
        $literalPathspec = ":(literal)$path"

        $staged = @(& git --no-pager diff --cached --no-ext-diff --no-color -- $literalPathspec)
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the staged diff for $path."
        }
        $unstaged = @(& git --no-pager diff --no-ext-diff --no-color -- $literalPathspec)
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the working-tree diff for $path."
        }

        $content = (($staged + $unstaged) -join [Environment]::NewLine)
        if ([string]::IsNullOrWhiteSpace($content)) {
            $fullPath = Join-Path $RepositoryRoot $path
            if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
                $file = Get-Item -LiteralPath $fullPath
                if ($file.Length -gt 1MB) {
                    $content = "[File is larger than 1 MiB; contents omitted]"
                }
                else {
                    $bytes = [System.IO.File]::ReadAllBytes($fullPath)
                    if ($bytes -contains 0) {
                        $content = "[Binary file; contents omitted]"
                    }
                    else {
                        $content = [System.Text.Encoding]::UTF8.GetString($bytes)
                    }
                }
            }
            else {
                $content = "[Deleted file]"
            }
        }

        $content = Protect-SubmitContextText -Text $content
        $remaining = $MaximumCharacters - $builder.Length
        if ($remaining -le 0) {
            [void]$builder.AppendLine("[Context limit reached; remaining content omitted]")
            break
        }
        if ($content.Length -gt $remaining) {
            $content = $content.Substring(0, $remaining) + "`n[File content truncated]"
        }
        [void]$builder.AppendLine($content)
    }

    return $builder.ToString()
}

function Resolve-CodexCli {
    $command = Get-Command "codex" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $standardPath = Join-Path $env:LOCALAPPDATA "Programs/OpenAI/Codex/bin/codex.exe"
        if (Test-Path -LiteralPath $standardPath -PathType Leaf) {
            return $standardPath
        }
    }

    return $null
}

function ConvertFrom-SubmitProposalJson {
    param(
        [Parameter(Mandatory)][string]$Json,
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot
    )

    try {
        $raw = $Json | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        throw "Codex returned invalid proposal JSON. $($_.Exception.Message)"
    }

    foreach ($property in @("files", "commitMessage", "pullRequestTitle", "pullRequestBody")) {
        if ($raw.PSObject.Properties.Name -notcontains $property) {
            throw "The proposal is missing the '$property' field."
        }
    }

    $candidateSet = @{}
    foreach ($candidate in $CandidatePaths) {
        $candidateSet[$candidate.ToLowerInvariant()] = $candidate
    }

    $files = @()
    foreach ($path in @($raw.files)) {
        $normalized = ConvertTo-SubmitRepositoryPath -Path "$path" -RepositoryRoot $RepositoryRoot
        if (Test-SubmitPathIsExcluded -Path $normalized) {
            throw "The proposal contains an excluded path: $normalized"
        }
        $key = $normalized.ToLowerInvariant()
        if (-not $candidateSet.ContainsKey($key)) {
            throw "The proposal contains a path that is not a current Submit candidate: $normalized"
        }
        if ($files -contains $candidateSet[$key]) {
            throw "The proposal contains a duplicate path: $normalized"
        }
        $files += $candidateSet[$key]
    }

    if ($files.Count -eq 0) {
        throw "The proposal must select at least one file."
    }

    $commitMessage = "$($raw.commitMessage)".Trim()
    $pullRequestTitle = "$($raw.pullRequestTitle)".Trim()
    $pullRequestBody = "$($raw.pullRequestBody)".Trim()
    if ([string]::IsNullOrWhiteSpace($commitMessage) -or $commitMessage.Length -gt 200 -or $commitMessage.Contains("`n")) {
        throw "The proposed commit message must be one non-empty line of at most 200 characters."
    }
    if ([string]::IsNullOrWhiteSpace($pullRequestTitle) -or $pullRequestTitle.Length -gt 256 -or $pullRequestTitle.Contains("`n")) {
        throw "The proposed Pull Request title must be one non-empty line of at most 256 characters."
    }
    $bodyMatch = [System.Text.RegularExpressions.Regex]::Match(
        $pullRequestBody,
        "(?is)^\s*## Summary\s*(?<summary>.*?)\s*## Tests\s*(?<tests>.+?)\s*$"
    )
    if (-not $bodyMatch.Success -or
        [string]::IsNullOrWhiteSpace($bodyMatch.Groups["summary"].Value) -or
        [string]::IsNullOrWhiteSpace($bodyMatch.Groups["tests"].Value)) {
        throw "The proposed Pull Request body must contain non-empty '## Summary' and '## Tests' sections."
    }

    return [pscustomobject]@{
        Files            = @($files)
        CommitMessage    = $commitMessage
        PullRequestTitle = $pullRequestTitle
        PullRequestBody  = $pullRequestBody
    }
}

function Invoke-CodexSubmitProposal {
    param(
        [Parameter(Mandatory)][string]$CodexPath,
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot
    )

    $temporaryRoot = [System.IO.Path]::GetTempPath()
    $temporaryDirectory = Join-Path $temporaryRoot ("rag-submit-proposal-" + [Guid]::NewGuid().ToString("N"))
    [void](New-Item -ItemType Directory -Path $temporaryDirectory)

    try {
        $schemaPath = Join-Path $temporaryDirectory "proposal-schema.json"
        $outputPath = Join-Path $temporaryDirectory "proposal.json"
        $schema = @{
            type                 = "object"
            additionalProperties = $false
            properties           = [ordered]@{
                files            = @{ type = "array"; items = @{ type = "string" } }
                commitMessage    = @{ type = "string" }
                pullRequestTitle = @{ type = "string" }
                pullRequestBody  = @{ type = "string" }
            }
            required             = @("files", "commitMessage", "pullRequestTitle", "pullRequestBody")
        } | ConvertTo-Json -Depth 8
        [System.IO.File]::WriteAllText($schemaPath, $schema, [System.Text.UTF8Encoding]::new($false))

        $context = Get-SubmitCodexContext -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot
        $prompt = @"
You prepare Git metadata only. Do not execute or suggest commands and do not inspect the filesystem.
Use only the supplied candidate list and change context below.

Return a proposal that:
- selects only files that belong together in this commit;
- uses a concise Conventional Commit message;
- provides a concise Pull Request title;
- provides a Markdown Pull Request body with exactly the sections '## Summary' and '## Tests';
- never invents test results. If no executed-test evidence is supplied, write '- Not run by Submit; run Validate separately.' under Tests.

$context
"@

        $arguments = @(
            "exec",
            "--sandbox", "read-only",
            "--disable", "shell_tool",
            "--ephemeral",
            "--ignore-user-config",
            "--skip-git-repo-check",
            "--color", "never",
            "--output-schema", $schemaPath,
            "--output-last-message", $outputPath,
            "--cd", $temporaryDirectory,
            "-"
        )

        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $prompt | & $CodexPath @arguments 2>$null | Out-Null
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        if ($exitCode -ne 0 -or -not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
            throw "Codex CLI could not produce a proposal (exit code: $exitCode)."
        }

        $json = [System.IO.File]::ReadAllText($outputPath)
        return ConvertFrom-SubmitProposalJson -Json $json -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot
    }
    finally {
        if (Test-Path -LiteralPath $temporaryDirectory -PathType Container) {
            $resolved = [System.IO.Path]::GetFullPath($temporaryDirectory)
            $expectedPrefix = [System.IO.Path]::GetFullPath((Join-Path $temporaryRoot "rag-submit-proposal-"))
            if (-not $resolved.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove an unexpected temporary path: $resolved"
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}

function Read-SubmitFileSelection {
    param(
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [string[]]$CurrentPaths = @()
    )

    for ($index = 0; $index -lt $CandidatePaths.Count; $index++) {
        Write-Host (" [{0}] {1}" -f ($index + 1), $CandidatePaths[$index])
    }
    $answer = Read-Host "Files: comma-separated numbers, 'all', or Enter to keep the current selection"
    if ([string]::IsNullOrWhiteSpace($answer)) {
        if ($CurrentPaths.Count -eq 0) {
            throw "At least one file must be selected."
        }
        return @($CurrentPaths)
    }
    if ($answer.Trim().ToLowerInvariant() -eq "all") {
        return @($CandidatePaths)
    }

    $selected = @()
    foreach ($part in $answer.Split(',')) {
        $number = 0
        if (-not [int]::TryParse($part.Trim(), [ref]$number) -or $number -lt 1 -or $number -gt $CandidatePaths.Count) {
            throw "Invalid file selection: $part"
        }
        $path = $CandidatePaths[$number - 1]
        if ($selected -notcontains $path) {
            $selected += $path
        }
    }
    return @($selected)
}

function New-ManualSubmitProposal {
    param(
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [string]$DefaultCommitMessage = ""
    )

    Write-Host "Manual Submit proposal mode." -ForegroundColor Yellow
    $files = Read-SubmitFileSelection -CandidatePaths $CandidatePaths -CurrentPaths $CandidatePaths
    $commitMessage = Read-Host "Commit message$(if ($DefaultCommitMessage) { " [$DefaultCommitMessage]" })"
    if ([string]::IsNullOrWhiteSpace($commitMessage)) {
        $commitMessage = $DefaultCommitMessage
    }
    $pullRequestTitle = Read-Host "Pull Request title [$commitMessage]"
    if ([string]::IsNullOrWhiteSpace($pullRequestTitle)) {
        $pullRequestTitle = $commitMessage
    }
    $summary = Read-Host "Pull Request summary (one line)"
    $tests = Read-Host "Test results (one line; Enter means not run by Submit)"
    if ([string]::IsNullOrWhiteSpace($tests)) {
        $tests = "Not run by Submit; run Validate separately."
    }
    $body = "## Summary`n`n- $summary`n`n## Tests`n`n- $tests"

    $json = @{
        files = @($files)
        commitMessage = $commitMessage
        pullRequestTitle = $pullRequestTitle
        pullRequestBody = $body
    } | ConvertTo-Json -Depth 4
    return ConvertFrom-SubmitProposalJson -Json $json -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot
}

function New-ManualSubmitPullRequestMetadata {
    param([Parameter(Mandatory)][string]$DefaultTitle)

    Write-Host "No matching saved Submit proposal was found. Enter Pull Request metadata manually." -ForegroundColor Yellow
    $title = Read-Host "Pull Request title [$DefaultTitle]"
    if ([string]::IsNullOrWhiteSpace($title)) {
        $title = $DefaultTitle
    }
    $summary = Read-Host "Pull Request summary (one line)"
    $tests = Read-Host "Test results (one line; Enter means not run by Submit)"
    if ([string]::IsNullOrWhiteSpace($tests)) {
        $tests = "Not run by Submit; run Validate separately."
    }
    if ([string]::IsNullOrWhiteSpace($title) -or $title.Length -gt 256 -or $title.Contains("`n")) {
        throw "The Pull Request title must be one non-empty line of at most 256 characters."
    }
    if ([string]::IsNullOrWhiteSpace($summary)) {
        throw "The Pull Request summary cannot be empty."
    }

    return [pscustomobject]@{
        Files            = @()
        CommitMessage    = $DefaultTitle
        PullRequestTitle = $title.Trim()
        PullRequestBody  = "## Summary`n`n- $summary`n`n## Tests`n`n- $tests"
    }
}

function Edit-SubmitProposal {
    param(
        [Parameter(Mandatory)][object]$Proposal,
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot
    )

    Write-Host "Edit the proposal. Enter keeps the displayed value. Use literal \n for line breaks in the PR body."
    $files = Read-SubmitFileSelection -CandidatePaths $CandidatePaths -CurrentPaths $Proposal.Files
    $commitMessage = Read-Host "Commit message [$($Proposal.CommitMessage)]"
    if ([string]::IsNullOrWhiteSpace($commitMessage)) { $commitMessage = $Proposal.CommitMessage }
    $title = Read-Host "Pull Request title [$($Proposal.PullRequestTitle)]"
    if ([string]::IsNullOrWhiteSpace($title)) { $title = $Proposal.PullRequestTitle }
    $body = Read-Host "Pull Request body [Enter keeps current body]"
    if ([string]::IsNullOrWhiteSpace($body)) {
        $body = $Proposal.PullRequestBody
    }
    else {
        $body = $body.Replace("\n", "`n")
    }

    $json = @{
        files = @($files)
        commitMessage = $commitMessage
        pullRequestTitle = $title
        pullRequestBody = $body
    } | ConvertTo-Json -Depth 4
    return ConvertFrom-SubmitProposalJson -Json $json -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot
}

function Show-SubmitProposal {
    param([Parameter(Mandatory)][object]$Proposal)

    Write-Host ""
    Write-Host "Submit proposal" -ForegroundColor Cyan
    Write-Host "Files:"
    $Proposal.Files | ForEach-Object { Write-Host " - $_" }
    Write-Host "Commit message: $($Proposal.CommitMessage)"
    Write-Host "Pull Request title: $($Proposal.PullRequestTitle)"
    Write-Host "Pull Request body:"
    Write-Host $Proposal.PullRequestBody
}

function Get-ApprovedSubmitProposal {
    param(
        [Parameter(Mandatory)][string[]]$CandidatePaths,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [string]$DefaultCommitMessage = ""
    )

    $codexPath = Resolve-CodexCli
    $proposal = $null
    $generate = $true

    while ($true) {
        if ($generate) {
            if ($null -ne $codexPath) {
                Write-Host "Requesting a structured Submit proposal from Codex CLI..."
                try {
                    $proposal = Invoke-CodexSubmitProposal -CodexPath $codexPath -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot
                }
                catch {
                    Write-Warning $_.Exception.Message
                    Write-Host "Codex proposal is unavailable; switching to manual mode." -ForegroundColor Yellow
                    $proposal = New-ManualSubmitProposal -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot -DefaultCommitMessage $DefaultCommitMessage
                }
            }
            else {
                Write-Host "Codex CLI is unavailable; switching to manual mode." -ForegroundColor Yellow
                $proposal = New-ManualSubmitProposal -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot -DefaultCommitMessage $DefaultCommitMessage
            }
            $generate = $false
        }

        Show-SubmitProposal -Proposal $proposal
        $decision = (Read-Host "Choose: [a]ccept, [e]dit, [r]egenerate, [c]ancel").Trim().ToLowerInvariant()
        switch ($decision) {
            "a" { return $proposal }
            "e" { $proposal = Edit-SubmitProposal -Proposal $proposal -CandidatePaths $CandidatePaths -RepositoryRoot $RepositoryRoot }
            "r" { $generate = $true }
            "c" { throw "Submit proposal cancelled. No files were staged." }
            default { Write-Host "Unknown choice. No operation was performed." -ForegroundColor Yellow }
        }
    }
}

function Get-SubmitStatePath {
    $gitPath = (& git --no-pager rev-parse --git-path "release-workflow/submit-proposal.json" | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitPath)) {
        throw "Could not resolve the local Submit state path."
    }
    if (-not [System.IO.Path]::IsPathRooted($gitPath)) {
        $gitPath = Join-Path $script:RepositoryRoot $gitPath
    }
    return [System.IO.Path]::GetFullPath($gitPath)
}

function Save-SubmitProposalState {
    param(
        [Parameter(Mandatory)][object]$Proposal,
        [Parameter(Mandatory)][string]$Branch,
        [Parameter(Mandatory)][string]$HeadSha
    )

    $statePath = Get-SubmitStatePath
    $stateDirectory = Split-Path -Parent $statePath
    [void](New-Item -ItemType Directory -Path $stateDirectory -Force)
    $state = [ordered]@{
        branch = $Branch
        headSha = $HeadSha
        files = @($Proposal.Files)
        commitMessage = $Proposal.CommitMessage
        pullRequestTitle = $Proposal.PullRequestTitle
        pullRequestBody = $Proposal.PullRequestBody
    } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($statePath, $state, [System.Text.UTF8Encoding]::new($false))
}

function Get-SubmitProposalState {
    param(
        [Parameter(Mandatory)][string]$Branch,
        [Parameter(Mandatory)][string]$HeadSha
    )

    $statePath = Get-SubmitStatePath
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        return $null
    }
    try {
        $state = [System.IO.File]::ReadAllText($statePath) | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        Write-Warning "Ignoring invalid local Submit state."
        return $null
    }
    if ($state.branch -ne $Branch -or $state.headSha -ne $HeadSha) {
        return $null
    }

    return [pscustomobject]@{
        Files            = @($state.files)
        CommitMessage    = "$($state.commitMessage)"
        PullRequestTitle = "$($state.pullRequestTitle)"
        PullRequestBody  = "$($state.pullRequestBody)"
    }
}

function Remove-SubmitProposalState {
    $statePath = Get-SubmitStatePath
    if (Test-Path -LiteralPath $statePath -PathType Leaf) {
        Remove-Item -LiteralPath $statePath -Force
    }
}

function Get-SubmitStagedPaths {
    return @(Get-SubmitGitPathOutput -Arguments @("--no-pager", "-c", "core.quotepath=false", "diff", "--cached", "--name-only", "--", "."))
}

function Set-SubmitStagingSelection {
    param([Parameter(Mandatory)][string[]]$SelectedPaths)

    $currentlyStaged = Get-SubmitStagedPaths
    foreach ($path in $currentlyStaged) {
        if ($SelectedPaths -notcontains $path) {
            Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "reset", "--", ":(literal)$path") -FailureMessage "Could not unstage an unselected path: $path"
        }
    }
    foreach ($path in $SelectedPaths) {
        Invoke-Checked -FilePath "git" -Arguments @("--no-pager", "add", "--", ":(literal)$path") -FailureMessage "Could not stage the selected path: $path"
    }

    $actual = @(Get-SubmitStagedPaths | Sort-Object -Unique)
    $expected = @($SelectedPaths | Sort-Object -Unique)
    if (($actual -join "`n") -ne ($expected -join "`n")) {
        throw "The staged file set does not exactly match the accepted Submit proposal."
    }
}
