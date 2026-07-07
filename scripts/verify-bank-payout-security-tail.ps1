$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$internalKey = 'dev-monolith-internal-key'
$serviceId = '759ee600-1004-4a7b-8d82-37f11f977c75'
$memberId = '69d785de-1e6e-46c6-944a-7bddf78df654'
$ownerUserId = 1000000000000
$restaurantOrgId = '0c4ab721-7017-4496-af97-8a749fb4872a'
$mainPayoutId = 'a3b2a4b8-cc02-432d-8f82-69bbb2ebea14'

function Get-Token($userId) {
    $creds = Invoke-RestMethod -Method Post -Uri "$base/api/internal/monolith/users/$userId/reset-web-credentials" -Headers @{ 'X-Internal-Key' = $internalKey }
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body (@{ login = $creds.login; password = $creds.temporaryPassword } | ConvertTo-Json)
    if ($login.accessToken) { return $login.accessToken }
    $verify = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login/verify-email-code" -ContentType 'application/json' -Body (@{ challengeId = $login.challengeId; code = '000000' } | ConvertTo-Json)
    return $verify.accessToken
}

$token = Get-Token $ownerUserId
$path = "/couriers/$memberId/balance/payout-requests/$mainPayoutId/process?courierServiceId=$restaurantOrgId&approve=false&comment=test"
try {
    Invoke-WebRequest -UseBasicParsing -Method POST -Uri "$base/api/delivery$path" -Headers @{ Authorization = "Bearer $token" } | Out-Null
    Write-Host 'Wrong org: unexpected success'
} catch {
    Write-Host "Wrong org as courierServiceId HTTP $([int]$_.Exception.Response.StatusCode)"
}

$path2 = "/partner-program/payout-requests/4321ab59-40df-4c92-afad-d853eebdbd98/process?courierServiceId=$restaurantOrgId&approve=true"
try {
    Invoke-WebRequest -UseBasicParsing -Method POST -Uri "$base/api/delivery$path2" -Headers @{ Authorization = "Bearer $token" } | Out-Null
    Write-Host 'Partner wrong org: unexpected success'
} catch {
    Write-Host "Partner wrong org HTTP $([int]$_.Exception.Response.StatusCode)"
}

$path3 = "/couriers/$memberId/balance/payout-requests/$mainPayoutId/process?courierServiceId=00000000-0000-0000-0000-000000000099&approve=false&comment=test"
try {
    Invoke-WebRequest -UseBasicParsing -Method POST -Uri "$base/api/delivery$path3" -Headers @{ Authorization = "Bearer $token" } | Out-Null
    Write-Host 'Fake service: unexpected success'
} catch {
    Write-Host "Fake courier service HTTP $([int]$_.Exception.Response.StatusCode)"
}

$logText = docker compose -f docker-compose.dev.yml logs --tail=300 delivery-backend 2>&1 | Out-String
$hits = ([regex]::Matches($logText, '4276123456789012')).Count + ([regex]::Matches($logText, '5536914012345678')).Count
Write-Host "Full card hits in last 300 log lines: $hits (expect 0)"
