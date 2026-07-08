$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

$services = @(
    "config-server",
    "eureka-server",
    "ai-support-service",
    "sla-function-service",
    "support-rules-mcp-server",
    "ticket-orchestrator-service",
    "gateway-service"
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
