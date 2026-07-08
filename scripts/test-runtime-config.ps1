$ErrorActionPreference = "Stop"

$gatewayUrl = "http://localhost:8080/api/tickets/config-demo"
$reloadUrl = "http://localhost:8080/api/tickets/config-demo/reload"
$configFile = "config-repo/ticket-orchestrator-service.yml"

function Get-ConfigDemo {
    try {
        return Invoke-RestMethod -Method Get -Uri $gatewayUrl
    }
    catch {
        throw "Failed to call $gatewayUrl. Make sure all services are running. $($_.Exception.Message)"
    }
}

function Invoke-ConfigDemoReload {
    try {
        return Invoke-RestMethod -Method Post -Uri $reloadUrl
    }
    catch {
        throw "Failed to call $reloadUrl. Make sure gateway-service, ticket-orchestrator-service and config-server are running. $($_.Exception.Message)"
    }
}

Write-Host "Runtime Config Demo"
Write-Host ""
Write-Host "Calling $gatewayUrl"
$before = Get-ConfigDemo
Write-Host "Current fallback policy:"
Write-Host "  slaHours:    $($before.fallback.slaHours)"
Write-Host "  supportTeam: $($before.fallback.supportTeam)"
Write-Host "Source: $($before.source)"
Write-Host ""

Write-Host "Now edit this file:"
Write-Host "  $configFile"
Write-Host ""
Write-Host "Change these properties:"
Write-Host "  smartdesk.fallback.sla-hours"
Write-Host "  smartdesk.fallback.support-team"
Write-Host ""
Write-Host "This changes the fallback policy used by ticket-orchestrator-service when ai-support-service is unavailable."
Write-Host ""
Read-Host "Press ENTER after saving the new fallback policy"

Write-Host ""
Write-Host "Reloading ticket-orchestrator-service config via $reloadUrl"
$reloadResult = Invoke-ConfigDemoReload
Write-Host "Reload result:"
$reloadResult | ConvertTo-Json -Depth 10 | Write-Host

Write-Host ""
Write-Host "Calling $gatewayUrl again"
$after = Get-ConfigDemo
Write-Host "New fallback policy:"
Write-Host "  slaHours:    $($after.fallback.slaHours)"
Write-Host "  supportTeam: $($after.fallback.supportTeam)"
Write-Host "Source: $($after.source)"
Write-Host ""
Write-Host "Run scripts/smoke-test.ps1 with ai-support-service stopped to see mode = FALLBACK using these values."
