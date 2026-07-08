$ErrorActionPreference = "Stop"

$statusUrl = "http://localhost:8080/api/tickets/status"
$analyzeUrl = "http://localhost:8080/api/tickets/analyze"

Write-Host "GET $statusUrl"
$status = Invoke-RestMethod -Method Get -Uri $statusUrl
Write-Host "Service: $($status.service)"
Write-Host "Status:  $($status.status)"
Write-Host ""

$body = @{
    conversationId = "demo-1"
    description = "Meu notebook nao conecta no Wi-Fi"
} | ConvertTo-Json

Write-Host "POST $analyzeUrl"
$response = Invoke-RestMethod -Method Post -Uri $analyzeUrl -ContentType "application/json" -Body $body

[PSCustomObject]@{
    category = $response.category
    priority = $response.priority
    slaHours = $response.slaHours
    supportTeam = $response.supportTeam
    mode = $response.mode
} | Format-List
