$ErrorActionPreference = "Stop"

$statusUrl = "http://localhost:8080/api/tickets/status"
$analyzeUrl = "http://localhost:8080/api/tickets/analyze"
$graphqlUrl = "http://localhost:8082/graphql"

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Uri,

        [Parameter(Mandatory = $true)]
        [string] $Body
    )

    return Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Body $Body
}

Write-Host "GET $statusUrl"
$status = Invoke-RestMethod -Method Get -Uri $statusUrl
Write-Host "Service: $($status.service)"
Write-Host "Status:  $($status.status)"
Write-Host ""

$analyzeBody = @{
    conversationId = "docker-demo-1"
    description = "Meu notebook nao conecta no Wi-Fi da universidade"
} | ConvertTo-Json -Depth 10

Write-Host "POST $analyzeUrl"
$response = Invoke-JsonPost -Uri $analyzeUrl -Body $analyzeBody

[PSCustomObject]@{
    category = $response.category
    priority = $response.priority
    slaHours = $response.slaHours
    supportTeam = $response.supportTeam
    mode = $response.mode
} | Format-List

$graphqlBody = @{
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
            conversationId = "docker-direct-ai"
            description = "A internet e o Wi-Fi da sala estao indisponiveis"
        }
    }
} | ConvertTo-Json -Depth 10

try {
    Write-Host "POST $graphqlUrl"
    $graphqlResponse = Invoke-JsonPost -Uri $graphqlUrl -Body $graphqlBody
    if ($graphqlResponse.errors) {
        Write-Host "GraphQL errors:"
        $graphqlResponse.errors | ConvertTo-Json -Depth 10 | Write-Host
        throw "GraphQL returned errors."
    }

    $analysis = $graphqlResponse.data.analyzeTicket
    [PSCustomObject]@{
        directAiCategory = $analysis.category
        directAiPriority = $analysis.priority
        ragSource = $analysis.ragSource
        mcpRuleUsed = $analysis.mcpRuleUsed
        externalMcpToolUsed = $analysis.externalMcpToolUsed
        aiProvider = $analysis.aiProvider
        realAiUsed = $analysis.realAiUsed
    } | Format-List
}
catch {
    Write-Host "Direct ai-support-service GraphQL check failed: $($_.Exception.Message)"
    throw
}
