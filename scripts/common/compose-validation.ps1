function Invoke-IsolatedComposeValidation {
    param(
        [Parameter(Mandatory)]
        [string]$RepositoryRoot
    )

    Write-Host "Validating Docker Compose configuration with non-secret placeholders"

    $validationEnvFile = [System.IO.Path]::GetTempFileName()
    $process = $null

    try {
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "docker"
        $startInfo.Arguments = "compose --env-file `"$validationEnvFile`" --file compose.yaml config --quiet"
        $startInfo.WorkingDirectory = $RepositoryRoot
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true

        # Ignore inherited Compose configuration overrides.
        foreach ($name in @(
            "COMPOSE_FILE",
            "COMPOSE_ENV_FILES",
            "COMPOSE_PROJECT_NAME",
            "COMPOSE_PROFILES"
        )) {
            $startInfo.EnvironmentVariables.Remove($name)
        }

        # Supply test values only to the child process.
        $startInfo.EnvironmentVariables["DB_PASSWORD"] = "validation-placeholder"
        $startInfo.EnvironmentVariables["OPENAI_API_KEY"] = "validation-placeholder"
        $startInfo.EnvironmentVariables["JWT_SECRET"] = "validation-placeholder-validation-placeholder"
        $startInfo.EnvironmentVariables["RABBITMQ_USER_DV"] = "validation-user"
        $startInfo.EnvironmentVariables["RABBITMQ_PASSWORD_DV"] = "validation-placeholder"

        $startInfo.EnvironmentVariables["CONFIG_DIR"] = [System.IO.Path]::GetTempPath()
        $startInfo.EnvironmentVariables["STORAGE_DIR"] = [System.IO.Path]::GetTempPath()

        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo
        [void]$process.Start()

        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()

        $process.WaitForExit()

        $standardOutput = $stdoutTask.GetAwaiter().GetResult()
        $standardError = $stderrTask.GetAwaiter().GetResult()

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
    finally {
        if ($null -ne $process) {
            $process.Dispose()
        }

        Remove-Item -LiteralPath $validationEnvFile -Force
    }
}
