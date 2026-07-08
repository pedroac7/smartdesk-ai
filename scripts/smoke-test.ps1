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
} | ConvertTo-Json -Depth 10

Write-Host "POST $analyzeUrl"
$response = Invoke-RestMethod -Method Post -Uri $analyzeUrl -ContentType "application/json" -Body $body

[PSCustomObject]@{
    category = $response.category
    priority = $response.priority
    slaHours = $response.slaHours
    supportTeam = $response.supportTeam
    mode = $response.mode
} | Format-List

$graphqlUrl = "http://localhost:8082/graphql"
$graphqlPayload = @{
    query = @"
query AnalyzeTicket(`$input: AnalyzeTicketInput!) {
  analyzeTicket(input: `$input) {
    category
    priority
    ragSource
    mcpRuleUsed
  }
}
"@
    variables = @{
        input = @{
            conversationId = "smoke-direct-ai"
            description = "Meu notebook nao conecta no Wi-Fi"
        }
    }
} | ConvertTo-Json -Depth 10

try {
    Write-Host "POST $graphqlUrl"
    $graphqlResponse = Invoke-RestMethod -Method Post -Uri $graphqlUrl -ContentType "application/json" -Body $graphqlPayload
    if ($graphqlResponse.errors) {
        Write-Host "GraphQL errors:"
        $graphqlResponse.errors | ConvertTo-Json -Depth 10 | Write-Host
    }
    $analysis = $graphqlResponse.data.analyzeTicket

    [PSCustomObject]@{
        directAiCategory = $analysis.category
        directAiPriority = $analysis.priority
        ragSource = $analysis.ragSource
        mcpRuleUsed = $analysis.mcpRuleUsed
    } | Format-List
}
catch {
    Write-Host "Direct ai-support-service GraphQL check skipped: $($_.Exception.Message)"
}
