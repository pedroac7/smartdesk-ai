$ErrorActionPreference = "Stop"

$graphqlUrl = "http://localhost:8082/graphql"

function Invoke-AiTicketAnalysis {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ConversationId,

        [Parameter(Mandatory = $true)]
        [string] $Description
    )

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
  }
}
"@
        variables = @{
            input = @{
                conversationId = $ConversationId
                description = $Description
            }
        }
    } | ConvertTo-Json -Depth 10

    $response = Invoke-RestMethod -Method Post -Uri $graphqlUrl -ContentType "application/json" -Body $payload
    if ($response.errors) {
        Write-Host "GraphQL errors for conversationId=$ConversationId"
        $response.errors | ConvertTo-Json -Depth 10 | Write-Host
        throw "GraphQL returned errors for conversationId=$ConversationId"
    }

    return $response.data.analyzeTicket
}

Write-Host "Testing ai-support-service GraphQL at $graphqlUrl"
Write-Host ""

$network = Invoke-AiTicketAnalysis -ConversationId "ai-feature-rede" -Description "A internet e o Wi-Fi da sala estao instaveis"
$hardware = Invoke-AiTicketAnalysis -ConversationId "ai-feature-hardware" -Description "O notebook liga, mas o monitor nao funciona"
$memoryFirst = Invoke-AiTicketAnalysis -ConversationId "ai-feature-memory" -Description "Meu computador esta lento"
$memorySecond = Invoke-AiTicketAnalysis -ConversationId "ai-feature-memory" -Description "Agora o mesmo computador esta parado"

@(
    [PSCustomObject]@{
        scenario = "REDE"
        category = $network.category
        priority = $network.priority
        ragSource = $network.ragSource
        mcpRuleUsed = $network.mcpRuleUsed
    },
    [PSCustomObject]@{
        scenario = "HARDWARE"
        category = $hardware.category
        priority = $hardware.priority
        ragSource = $hardware.ragSource
        mcpRuleUsed = $hardware.mcpRuleUsed
    },
    [PSCustomObject]@{
        scenario = "MEMORY_FIRST"
        category = $memoryFirst.category
        priority = $memoryFirst.priority
        ragSource = $memoryFirst.ragSource
        mcpRuleUsed = $memoryFirst.mcpRuleUsed
    },
    [PSCustomObject]@{
        scenario = "MEMORY_SECOND"
        category = $memorySecond.category
        priority = $memorySecond.priority
        ragSource = $memorySecond.ragSource
        mcpRuleUsed = $memorySecond.mcpRuleUsed
    }
) | Format-Table -AutoSize

Write-Host ""
Write-Host "Memory demo summary from second call:"
Write-Host $memorySecond.summary
