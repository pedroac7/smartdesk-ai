$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$projectMarker = "smartdesk-ai"
$knownPorts = @(8888, 8761, 8762, 8080, 8081, 8082, 8083, 8084)

function Get-SmartDeskJavaProcessesByCommandLine {
    try {
        return @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -and
                ($_.CommandLine -like "*$projectMarker*" -or $_.CommandLine -like "*$projectRoot*")
            } |
            Select-Object ProcessId, Name, CommandLine)
    }
    catch {
        Write-Host "Could not inspect Java command lines with CIM/WMI."
        Write-Host "Reason: $($_.Exception.Message)"
        return @()
    }
}

function Get-ProcessesByKnownPorts {
    $portProcesses = New-Object System.Collections.Generic.List[object]

    foreach ($port in $knownPorts) {
        $lines = netstat -ano | Select-String -Pattern ":$port\s+.*LISTENING"
        foreach ($line in $lines) {
            $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
            $processIdText = $parts[-1]
            $processId = 0

            if ([int]::TryParse($processIdText, [ref] $processId)) {
                $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
                $portProcesses.Add([PSCustomObject]@{
                    ProcessId = $processId
                    Name = if ($process) { $process.ProcessName } else { "unknown" }
                    Port = $port
                    CommandLine = "Listening on localhost port $port"
                })
            }
        }
    }

    return @($portProcesses | Sort-Object ProcessId, Port -Unique)
}

Write-Host "Searching Java processes related to $projectMarker..."
$processes = Get-SmartDeskJavaProcessesByCommandLine

if (-not $processes) {
    Write-Host ""
    Write-Host "No Java processes were found by command line."
    Write-Host "Checking known SmartDesk ports as a fallback: $($knownPorts -join ', ')"
    $processes = Get-ProcessesByKnownPorts
}

if (-not $processes) {
    Write-Host "No matching processes found."
    return
}

$processes | Format-Table -AutoSize ProcessId, Name, Port, CommandLine

$answer = Read-Host "Terminate these processes? Type YES to confirm"
if ($answer -ne "YES") {
    Write-Host "Canceled. No process was terminated."
    return
}

$processIds = @($processes | Select-Object -ExpandProperty ProcessId -Unique)
foreach ($processId in $processIds) {
    Write-Host "Stopping process $processId..."
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}

Write-Host "Done."
