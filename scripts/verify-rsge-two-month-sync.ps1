param(
    [string]$BaseUrl = "http://localhost",
    [string]$Username,
    [string]$Password,
    [int]$MinUniqueCustomers = 261,
    [int]$PollSeconds = 3,
    [int]$TimeoutMinutes = 15
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "Provide -Username and -Password."
}

$loginUri = "$BaseUrl/api/v1/auth/login"
$triggerUri = "$BaseUrl/api/v1/sync/trigger"
$statusUri = "$BaseUrl/api/v1/sync/status"

$startDate = (Get-Date).Date.AddMonths(-2).ToString("yyyy-MM-dd")
Write-Host "Logging in to $BaseUrl as $Username ..."
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri $loginUri -WebSession $session -ContentType "application/json" -Body $loginBody | Out-Null

Write-Host "Triggering sync from $startDate to today ..."
$triggerBody = @{ type = "DAILY"; date = $startDate } | ConvertTo-Json
$trigger = Invoke-RestMethod -Method Post -Uri $triggerUri -WebSession $session -ContentType "application/json" -Body $triggerBody
Write-Host ("syncId={0}, status={1}" -f $trigger.syncId, $trigger.status)

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
do {
    Start-Sleep -Seconds $PollSeconds
    $status = Invoke-RestMethod -Method Get -Uri $statusUri -WebSession $session
    Write-Host ("status={0}, found={1}, added={2}" -f $status.status, $status.customersFound, $status.customersAdded)
    if ($status.status -eq "SUCCESS") {
        $found = [int]$status.customersFound
        if ($found -ge $MinUniqueCustomers) {
            Write-Host ("PASS: unique customers found = {0} (threshold = {1})" -f $found, $MinUniqueCustomers) -ForegroundColor Green
            exit 0
        }
        throw ("FAIL: sync succeeded but unique customers found = {0}, expected >= {1}" -f $found, $MinUniqueCustomers)
    }
    if ($status.status -eq "FAILED") {
        throw ("FAIL: sync failed. errorMessage={0}" -f $status.errorMessage)
    }
} while ((Get-Date) -lt $deadline)

throw "FAIL: sync did not finish before timeout."

