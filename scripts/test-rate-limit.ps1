param(
    [int] $Requests = 15,
    [int] $RefillWaitSeconds = 11
)

$ErrorActionPreference = "Stop"

$url = "http://localhost:8080/api/tickets/analyze"
$successCount = 0
$rateLimitedCount = 0
$otherCount = 0

$body = @{
    conversationId = "rate-limit-demo"
    description = "Meu notebook nao conecta no Wi-Fi"
} | ConvertTo-Json -Depth 10

function Invoke-AnalyzeRequest {
    param(
        [Parameter(Mandatory = $true)]
        [int] $Index
    )

    try {
        Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json" -Body $body | Out-Null
        return [PSCustomObject]@{
            index = $Index
            status = 200
            result = "OK"
        }
    }
    catch {
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int] $_.Exception.Response.StatusCode
        }

        if (-not $statusCode) {
            return [PSCustomObject]@{
                index = $Index
                status = "ERROR"
                result = $_.Exception.Message
            }
        }

        return [PSCustomObject]@{
            index = $Index
            status = $statusCode
            result = if ($statusCode -eq 429) { "RATE_LIMITED" } else { "HTTP_$statusCode" }
        }
    }
}

Write-Host "Testing Gateway rate limit at $url"
Write-Host "Sending $Requests requests quickly..."
Write-Host ""

for ($i = 1; $i -le $Requests; $i++) {
    $result = Invoke-AnalyzeRequest -Index $i
    Write-Host ("Request {0}: {1} {2}" -f $result.index, $result.status, $result.result)

    if ($result.status -eq 429) {
        $rateLimitedCount++
    }
    elseif ($result.status -is [int] -and $result.status -ge 200 -and $result.status -lt 300) {
        $successCount++
    }
    else {
        $otherCount++
    }
}

Write-Host ""
Write-Host "Summary:"
Write-Host "  Success:      $successCount"
Write-Host "  Rate limited: $rateLimitedCount"
Write-Host "  Other:        $otherCount"

Write-Host ""
Write-Host "Waiting $RefillWaitSeconds seconds to test refill..."
Start-Sleep -Seconds $RefillWaitSeconds

$afterRefill = Invoke-AnalyzeRequest -Index ($Requests + 1)
Write-Host ("After refill: {0} {1}" -f $afterRefill.status, $afterRefill.result)
