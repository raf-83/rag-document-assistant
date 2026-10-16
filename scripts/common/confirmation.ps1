function Confirm-Action {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string]$Operation,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string[]]$Details
    )

    Write-Host ""
    Write-Host "Operation: $Operation" -ForegroundColor Yellow
    foreach ($detail in $Details) {
        Write-Host $detail
    }

    $answer = Read-Host "Continue? (y/n)"
    if ($answer -cne "y" -and $answer -cne "Y") {
        throw "Operation cancelled. Enter y or Y to continue."
    }
}
