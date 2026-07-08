$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

$services = @(
    "config-server",
    "eureka-server",
    "gateway-service",
    "ticket-orchestrator-service",
    "ai-support-service",
    "sla-function-service",
    "support-rules-mcp-server"
)

foreach ($service in $services) {
    $servicePath = Join-Path $projectRoot $service
    Write-Host ""
    Write-Host "Building $service..."
    Push-Location $servicePath
    try {
        & .\mvnw.cmd clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $service with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "All services built successfully."
