$ErrorActionPreference = "Stop"

function Test-Url {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [string] $Url
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
        Write-Host "[OK]   $Name -> $($response.StatusCode) $Url"
    }
    catch {
        Write-Host "[FAIL] $Name -> $Url"
        Write-Host "       $($_.Exception.Message)"
    }
}

Test-Url -Name "Prometheus" -Url "http://localhost:9090"
Test-Url -Name "Grafana" -Url "http://localhost:3000"
Test-Url -Name "gateway-service prometheus" -Url "http://localhost:8080/actuator/prometheus"
Test-Url -Name "ticket-orchestrator-service prometheus" -Url "http://localhost:8081/actuator/prometheus"
Test-Url -Name "ai-support-service prometheus" -Url "http://localhost:8082/actuator/prometheus"
Test-Url -Name "sla-function-service prometheus" -Url "http://localhost:8083/actuator/prometheus"

Write-Host ""
Write-Host "Useful URLs:"
Write-Host "  Prometheus targets: http://localhost:9090/targets"
Write-Host "  Grafana:            http://localhost:3000"
Write-Host "  Gateway status:     http://localhost:8080/api/tickets/status"
Write-Host "  Smoke test:         .\scripts\smoke-test.ps1"
