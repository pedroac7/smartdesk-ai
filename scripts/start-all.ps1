$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

function ConvertTo-PowerShellSingleQuoted {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    return "'" + ($Value -replace "'", "''") + "'"
}

function Start-SmartDeskService {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ServiceDirectory,

        [Parameter(Mandatory = $true)]
        [string] $WindowTitle,

        [hashtable] $Environment = @{}
    )

    $servicePath = Join-Path $projectRoot $ServiceDirectory
    if (-not (Test-Path $servicePath)) {
        throw "Service directory not found: $servicePath"
    }

    $commands = New-Object System.Collections.Generic.List[string]
    $commands.Add("`$Host.UI.RawUI.WindowTitle = $(ConvertTo-PowerShellSingleQuoted $WindowTitle)")
    $commands.Add("Set-Location -LiteralPath $(ConvertTo-PowerShellSingleQuoted $servicePath)")

    foreach ($entry in $Environment.GetEnumerator()) {
        $commands.Add("[Environment]::SetEnvironmentVariable($(ConvertTo-PowerShellSingleQuoted $entry.Key), $(ConvertTo-PowerShellSingleQuoted $entry.Value), 'Process')")
    }

    $commands.Add("Write-Host 'Starting $WindowTitle'")
    $commands.Add(".\mvnw.cmd spring-boot:run")

    $command = $commands -join "; "
    Start-Process -FilePath "powershell" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command)
}

Write-Host "SmartDesk AI project root: $projectRoot"
Write-Host ""

Start-SmartDeskService -ServiceDirectory "config-server" -WindowTitle "SmartDesk config-server"
Write-Host "Waiting for config-server..."
Start-Sleep -Seconds 15

Start-SmartDeskService -ServiceDirectory "eureka-server" -WindowTitle "SmartDesk eureka-server 8761" -Environment @{
    EUREKA_PORT = "8761"
    EUREKA_HOSTNAME = "localhost"
    EUREKA_PEERS = "http://localhost:8762/eureka/"
}
Start-Sleep -Seconds 5

Start-SmartDeskService -ServiceDirectory "eureka-server" -WindowTitle "SmartDesk eureka-server 8762" -Environment @{
    EUREKA_PORT = "8762"
    EUREKA_HOSTNAME = "localhost"
    EUREKA_PEERS = "http://localhost:8761/eureka/"
}

Write-Host "Waiting for Eureka servers..."
Start-Sleep -Seconds 20

Start-SmartDeskService -ServiceDirectory "ai-support-service" -WindowTitle "SmartDesk ai-support-service"
Start-Sleep -Seconds 5

Start-SmartDeskService -ServiceDirectory "sla-function-service" -WindowTitle "SmartDesk sla-function-service"
Start-Sleep -Seconds 5

Start-SmartDeskService -ServiceDirectory "ticket-orchestrator-service" -WindowTitle "SmartDesk ticket-orchestrator-service"
Start-Sleep -Seconds 8

Start-SmartDeskService -ServiceDirectory "gateway-service" -WindowTitle "SmartDesk gateway-service"

Write-Host ""
Write-Host "Services are starting in separate PowerShell windows."
Write-Host "Useful URLs:"
Write-Host "  Eureka 8761:  http://localhost:8761"
Write-Host "  Eureka 8762:  http://localhost:8762"
Write-Host "  Gateway test: http://localhost:8080/api/tickets/status"
