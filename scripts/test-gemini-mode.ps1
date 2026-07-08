$ErrorActionPreference = "Stop"

$graphqlUrl = "http://localhost:8082/graphql"

if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
    Write-Host "GEMINI_API_KEY is not defined."
    Write-Host ""
    Write-Host "Set it before starting ai-support-service. Example:"
    Write-Host '  $env:GEMINI_API_KEY = "your-key"'
    Write-Host '  $env:SMARTDESK_AI_MODE = "gemini"'
    Write-Host "Then restart ai-support-service and run this script again."
    exit 1
}

Write-Host "Testing Gemini mode through ai-support-service GraphQL at $graphqlUrl"
Write-Host "Make sure ai-support-service was started with SMARTDESK_AI_MODE=gemini."
Write-Host ""

$payload = @{
    query = @"
query AnalyzeTicket(`$input: AnalyzeTicketInput!) {
  analyzeTicket(input: `$input) {
    category
    priority
    summary
    suggestedAnswer
    ragSource
    mcpRuleUsed
    externalMcpToolUsed
    externalMcpAdvice
    aiProvider
    realAiUsed
  }
}
"@
    variables = @{
        input = @{
            conversationId = "gemini-demo-1"
            description = "Meu notebook nao conecta no Wi-Fi da universidade e preciso resolver com urgencia"
        }
    }
} | ConvertTo-Json -Depth 10

$response = Invoke-RestMethod -Method Post -Uri $graphqlUrl -ContentType "application/json" -Body $payload
if ($response.errors) {
    Write-Host "GraphQL errors:"
    $response.errors | ConvertTo-Json -Depth 10 | Write-Host
    throw "GraphQL returned errors."
}

$analysis = $response.data.analyzeTicket

[PSCustomObject]@{
    category = $analysis.category
    priority = $analysis.priority
    summary = $analysis.summary
    suggestedAnswer = $analysis.suggestedAnswer
    ragSource = $analysis.ragSource
    mcpRuleUsed = $analysis.mcpRuleUsed
    externalMcpToolUsed = $analysis.externalMcpToolUsed
    externalMcpAdvice = $analysis.externalMcpAdvice
    aiProvider = $analysis.aiProvider
    realAiUsed = $analysis.realAiUsed
} | Format-List

if ($analysis.aiProvider -eq "GEMINI" -and $analysis.realAiUsed -eq $true) {
    Write-Host "Gemini mode OK: aiProvider=GEMINI and realAiUsed=true"
}
else {
    Write-Host "Gemini mode was not used. Expected aiProvider=GEMINI and realAiUsed=true."
    Write-Host "Check SMARTDESK_AI_MODE=gemini, GEMINI_API_KEY, service logs and network access."
}
