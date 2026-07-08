$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

Push-Location $projectRoot
try {
    docker compose up -d --build
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "SmartDesk AI Docker stack is starting."
Write-Host "Useful URLs:"
Write-Host "  Gateway:       http://localhost:8080/api/tickets/status"
Write-Host "  Eureka 8761:   http://localhost:8761"
Write-Host "  Eureka 8762:   http://localhost:8762"
Write-Host "  Config Server: http://localhost:8888/application/default"
Write-Host "  Prometheus:    http://localhost:9090"
Write-Host "  Grafana:       http://localhost:3000"
Write-Host ""
Write-Host "Smoke test:"
Write-Host "  .\scripts\docker-smoke-test.ps1"
