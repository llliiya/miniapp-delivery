$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$internalKey = 'dev-monolith-internal-key'
$memberId = '69d785de-1e6e-46c6-944a-7bddf78df654'
$courierUserId = 1000000000001
$cardFull = '1111222233334444'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$successPayloadPath = Join-Path $scriptDir 'payloads\courier-payout-success.json'

function Get-Token($userId) {
    $creds = Invoke-RestMethod -Method Post -Uri "$base/api/internal/monolith/users/$userId/reset-web-credentials" `
        -Headers @{ 'X-Internal-Key' = $internalKey } -ContentType 'application/json'
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
        -Body (@{ login = $creds.login; password = $creds.temporaryPassword } | ConvertTo-Json)
    if ($login.accessToken) { return $login.accessToken }
    $verify = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login/verify-email-code" -ContentType 'application/json' `
        -Body (@{ challengeId = $login.challengeId; code = '000000' } | ConvertTo-Json)
    return $verify.accessToken
}

function Invoke-PayoutJson($token, $jsonBody) {
    try {
        $resp = Invoke-WebRequest -Method Post -Uri "$base/api/delivery/couriers/$memberId/balance/payout-requests" `
            -Headers @{ Authorization = "Bearer $token" } -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($jsonBody)) -UseBasicParsing
        return @{ Ok = $true; Status = [int]$resp.StatusCode; Body = ($resp.Content | ConvertFrom-Json); Raw = $resp.Content }
    } catch {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Close()
        return @{ Ok = $false; Status = [int]$resp.StatusCode; Raw = $text }
    }
}

function Invoke-PayoutApi($token, $body) {
    $json = $body | ConvertTo-Json -Depth 5 -Compress
    return Invoke-PayoutJson $token $json
}

function Db($sql) {
    docker exec delivery_dev-db-1 psql -U postgres -d delivery -t -A -c $sql
}

Write-Host '=== Cleanup pending payouts for test courier ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status IN ('PENDING','SCHEDULED','PROCESSING');" | Out-Null

$token = Get-Token $courierUserId
Write-Host 'Token OK'

Write-Host ''
Write-Host '=== GET balance (before) ==='
$balBefore = Invoke-RestMethod -Method Get -Uri "$base/api/delivery/couriers/$memberId/balance" `
    -Headers @{ Authorization = "Bearer $token" }
$balBefore | ConvertTo-Json -Depth 5

Write-Host ''
Write-Host '=== POST success payout ==='
$successJson = [System.IO.File]::ReadAllText($successPayloadPath, [System.Text.Encoding]::UTF8)
$success = Invoke-PayoutJson $token $successJson
Write-Host "HTTP $($success.Status)"
Write-Host $success.Raw

if ($success.Raw -match $cardFull) {
    Write-Host 'FAIL: full card number in response' -ForegroundColor Red
} else {
    Write-Host 'OK: full card number NOT in response' -ForegroundColor Green
}

Write-Host ''
Write-Host '=== DB payout_details ==='
Db "SELECT payout_details FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' ORDER BY created_at DESC LIMIT 1;"

Write-Host ''
Write-Host '=== Error: empty recipientName ==='
$errRecipient = Invoke-PayoutApi $token @{
    amount        = 50
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = $cardFull; recipientName = '   ' }
}
Write-Host "HTTP $($errRecipient.Status): $($errRecipient.Raw)"

Write-Host ''
Write-Host '=== Error: invalid card (cleanup pending first) ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status IN ('PENDING','SCHEDULED','PROCESSING');" | Out-Null
$errCard = Invoke-PayoutApi $token @{
    amount        = 50
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = '123'; recipientName = 'Ivan' }
}
Write-Host "HTTP $($errCard.Status): $($errCard.Raw)"

Write-Host ''
Write-Host '=== Error: amount exceeds available ==='
$available = [decimal]$balBefore.availableForPayout
$tooMuch = [math]::Floor($available) + 1000
$errAmount = Invoke-PayoutApi $token @{
    amount        = $tooMuch
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = $cardFull; recipientName = 'Ivan Ivanov' }
}
Write-Host "HTTP $($errAmount.Status): $($errAmount.Raw)"

Write-Host ''
Write-Host '=== Create pending then duplicate (409) ==='
$first = Invoke-PayoutJson $token $successJson
Write-Host "First HTTP $($first.Status)"
$dup = Invoke-PayoutJson $token $successJson
Write-Host "Duplicate HTTP $($dup.Status): $($dup.Raw)"

Write-Host ''
Write-Host '=== GET balance (after) ==='
$balAfter = Invoke-RestMethod -Method Get -Uri "$base/api/delivery/couriers/$memberId/balance" `
    -Headers @{ Authorization = "Bearer $token" }
$balAfter | ConvertTo-Json -Depth 5

Write-Host ''
Write-Host '=== Backend logs card leak check ==='
$logs = docker logs delivery_dev-delivery-backend-1 --since 3m 2>&1 | Out-String
if ($logs -match $cardFull) {
    Write-Host 'FAIL: full card found in logs' -ForegroundColor Red
} else {
    Write-Host 'OK: full card number NOT in recent logs' -ForegroundColor Green
}

Write-Host ''
Write-Host '=== Health ==='
$health = Invoke-RestMethod -Uri "$base/api/delivery/health" -TimeoutSec 5
Write-Host ($health | ConvertTo-Json -Compress)

Write-Host ''
Write-Host '=== Liquibase 026 check ==='
Db "SELECT id, filename FROM public.databasechangelog WHERE id = '026-courier-balance-payout-details';"
