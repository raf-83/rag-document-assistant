$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repositoryRoot 'scripts/common/compose-validation.ps1')

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'adv12 compose ' + [Guid]::NewGuid().ToString('N')
)

$variableNames = @(
    'DB_PASSWORD',
    'OPENAI_API_KEY',
    'JWT_SECRET',
    'RABBITMQ_USER_DV',
    'RABBITMQ_PASSWORD_DV',
    'CONFIG_DIR',
    'STORAGE_DIR',
    'COMPOSE_FILE',
    'COMPOSE_ENV_FILES',
    'COMPOSE_PROJECT_NAME',
    'COMPOSE_PROFILES',
    'ADV12_REQUIRED_VALUE'
)

$originalEnvironment = @{}

function Assert-ValidationRejected {
    param([string]$Scenario)

    $rejected = $false

    try {
        Invoke-IsolatedComposeValidation -RepositoryRoot $testRoot
    }
    catch {
        if ($_.Exception.Message -ne 'Docker Compose configuration is invalid.') {
            throw
        }

        $rejected = $true
    }

    if (-not $rejected) {
        throw "Validation incorrectly accepted: $Scenario"
    }

    Write-Host "PASS: $Scenario rejected."
}

try {
    foreach ($name in $variableNames) {
        $originalEnvironment[$name] =
            [Environment]::GetEnvironmentVariable($name, 'Process')

        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }

    New-Item -ItemType Directory -Path $testRoot | Out-Null

    $composePath = Join-Path $testRoot 'compose.yaml'
    Copy-Item (Join-Path $repositoryRoot 'compose.yaml') $composePath

    # This file must never be read by the validator.
    Set-Content -LiteralPath (Join-Path $testRoot '.env') `
        -Value "BROKEN='unterminated" -Encoding utf8

    # These overrides must affect neither the selected file nor the project.
    $env:COMPOSE_FILE = 'missing-compose.yaml'
    $env:COMPOSE_ENV_FILES = 'missing-environment.env'
    $env:COMPOSE_PROJECT_NAME = 'INVALID PROJECT NAME!'
    $env:COMPOSE_PROFILES = 'unused-profile'

    Invoke-IsolatedComposeValidation -RepositoryRoot $testRoot
    Write-Host 'PASS: actual Compose validates without private secrets or .env.'

    foreach ($name in @(
        'DB_PASSWORD',
        'OPENAI_API_KEY',
        'JWT_SECRET',
        'RABBITMQ_USER_DV',
        'RABBITMQ_PASSWORD_DV',
        'CONFIG_DIR',
        'STORAGE_DIR'
    )) {
        if ($null -ne [Environment]::GetEnvironmentVariable($name, 'Process')) {
            throw "Validator changed the parent environment: $name"
        }
    }

    if ($env:COMPOSE_FILE -ne 'missing-compose.yaml' -or
        $env:COMPOSE_ENV_FILES -ne 'missing-environment.env' -or
        $env:COMPOSE_PROJECT_NAME -ne 'INVALID PROJECT NAME!' -or
        $env:COMPOSE_PROFILES -ne 'unused-profile') {
        throw 'Validator changed inherited Compose overrides in the parent.'
    }

    Write-Host 'PASS: parent environment preserved.'

    Set-Content -LiteralPath $composePath -Encoding utf8 -Value @'
services:
  probe:
    image: "example:${ADV12_REQUIRED_VALUE:?ADV12_REQUIRED_VALUE is required}"
'@

    Assert-ValidationRejected -Scenario 'Missing required variable'

    Set-Content -LiteralPath $composePath -Encoding utf8 `
        -Value 'services: [invalid'

    Assert-ValidationRejected -Scenario 'Invalid YAML'

    Write-Host 'All Compose validation regression tests passed.'
}
finally {
    foreach ($name in $originalEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $originalEnvironment[$name],
            'Process'
        )
    }

    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}