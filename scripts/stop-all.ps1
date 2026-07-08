$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$projectMarker = "smartdesk-ai"

Write-Host "Searching Java processes related to $projectMarker..."

$processes = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -match "^java(\.exe)?$" -and
        $_.CommandLine -and
        $_.CommandLine -like "*$projectMarker*"
    } |
    Select-Object ProcessId, Name, CommandLine

if (-not $processes) {
    Write-Host "No matching Java processes found."
    return
}

$processes | Format-Table -AutoSize ProcessId, Name, CommandLine

$answer = Read-Host "Terminate these Java processes? Type YES to confirm"
if ($answer -ne "YES") {
    Write-Host "Canceled. No process was terminated."
    return
}

foreach ($process in $processes) {
    Write-Host "Stopping Java process $($process.ProcessId)..."
    Stop-Process -Id $process.ProcessId -Force
}

Write-Host "Done."
