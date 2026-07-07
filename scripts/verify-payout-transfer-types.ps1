$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$internalKey = 'dev-monolith-internal-key'
$serviceId = '759ee600-1004-4a7b-8d82-37f11f977c75'
$memberId = '69d785de-1e6e-46c6-944a-7bddf78df654'
$courierUserId = 1000000000001
$ownerUserId = 1000000000000

$results = [ordered]@{}

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

function Invoke-Delivery($method, $path, $token, $body) {
    $params = @{
        Method      = $method
        Uri         = "$base/api/delivery$path"
        Headers     = @{ Authorization = "Bearer $token" }
        ContentType = 'application/json'
    }
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress -Depth 8) }
    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        return @{ Ok = $true; Status = $r.StatusCode; Body = ($r.Content | ConvertFrom-Json); Raw = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Close()
        try { $json = $text | ConvertFrom-Json } catch { $json = @{ raw = $text } }
        return @{ Ok = $false; Status = [int]$resp.StatusCode; Body = $json; Raw = $text }
    }
}

function Db($sql) {
    docker exec delivery_dev-db-1 psql -U postgres -d delivery -t -A -c $sql
}

function Assert($name, $cond, $detail) {
    $results[$name] = @{ pass = [bool]$cond; detail = $detail }
    $color = if ($cond) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1}: {2}" -f ($(if ($cond) { 'PASS' } else { 'FAIL' })), $name, $detail) -ForegroundColor $color
}

$courierToken = Get-Token $courierUserId
$ownerToken = Get-Token $ownerUserId

Write-Host '=== Cleanup pending main balance payouts ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status IN ('PENDING','SCHEDULED','PROCESSING');" | Out-Null

$bal = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "Balance: $($bal.balance), available: $($bal.availableForPayout)"

$cardDigits = '5555555555555555'
$cardSpaced = '5555 5555 5555 5555'
$phone = '+79991234567'

Write-Host ''
Write-Host '=== CARD payout ==='
$cardCreate = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount        = 50
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{
        transferType  = 'CARD'
        cardNumber    = $cardSpaced
        recipientName = 'Ivan Ivanov'
        bankName      = 'Alfa Bank'
    }
}
Assert 'CARD create HTTP 200' $cardCreate.Ok "status=$($cardCreate.Status) raw=$($cardCreate.Raw)"
$cardId = $cardCreate.Body.id
$dbCard = Db "SELECT payout_details FROM delivery.courier_balance_payout_requests WHERE id = '$cardId';"
Assert 'CARD DB transferType' ($dbCard -match 'transferType.: .CARD') $dbCard
Assert 'CARD DB cardNumber no spaces' ($dbCard -match 'cardNumber.: .5555555555555555') $dbCard
Assert 'CARD response transferType' ($cardCreate.Body.transferType -eq 'CARD') "transferType=$($cardCreate.Body.transferType)"
Assert 'CARD response cardMask' ($cardCreate.Body.cardMask -match '5555$') "cardMask=$($cardCreate.Body.cardMask)"
Assert 'CARD response no full card' ($cardCreate.Raw -notmatch $cardDigits) 'full card not in API response'

$balAfterCard = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
$historyCard = $balAfterCard.payoutHistory | Where-Object { $_.id -eq $cardId } | Select-Object -First 1
Assert 'CARD history mask' ($historyCard.cardMask -match '5555$') "cardMask=$($historyCard.cardMask)"

$adminCard = ((Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body |
    Where-Object { $_.id -eq $cardId } | Select-Object -First 1)
Assert 'CARD admin transferType' ($adminCard.transferType -eq 'CARD') "transferType=$($adminCard.transferType)"
Assert 'CARD admin full card' ($adminCard.cardNumber -eq $cardDigits) "cardNumber=$($adminCard.cardNumber)"
Assert 'CARD admin recipient' ($adminCard.recipientName -eq 'Ivan Ivanov') "recipient=$($adminCard.recipientName)"
Assert 'CARD admin bank' ($adminCard.bankName -eq 'Alfa Bank') "bank=$($adminCard.bankName)"

Write-Host ''
Write-Host '=== SBP payout ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status IN ('PENDING','SCHEDULED','PROCESSING');" | Out-Null
$sbpCreate = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount        = 50
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{
        transferType  = 'SBP_PHONE'
        phoneNumber   = $phone
        recipientName = 'Petr Petrov'
        bankName      = 'T-Bank'
    }
}
Assert 'SBP create HTTP 200' $sbpCreate.Ok "status=$($sbpCreate.Status) raw=$($sbpCreate.Raw)"
$sbpId = $sbpCreate.Body.id
$dbSbp = Db "SELECT payout_details FROM delivery.courier_balance_payout_requests WHERE id = '$sbpId';"
Assert 'SBP DB transferType' ($dbSbp -match 'SBP_PHONE') $dbSbp
Assert 'SBP DB phoneNumber' ($dbSbp -match '\+79991234567') $dbSbp
Assert 'SBP DB no cardNumber' ($dbSbp -notmatch 'cardNumber') $dbSbp
Assert 'SBP response transferType' ($sbpCreate.Body.transferType -eq 'SBP_PHONE') "transferType=$($sbpCreate.Body.transferType)"
Assert 'SBP response phone mask' ($sbpCreate.Body.cardMask -match '45-67$') "cardMask=$($sbpCreate.Body.cardMask)"
Assert 'SBP response no full phone' ($sbpCreate.Raw -notmatch '9991234567') 'full phone not in API response'

$balAfterSbp = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
$historySbp = $balAfterSbp.payoutHistory | Where-Object { $_.id -eq $sbpId } | Select-Object -First 1
Assert 'SBP history mask' ($historySbp.cardMask -match '45-67$') "cardMask=$($historySbp.cardMask)"

$adminSbp = ((Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body |
    Where-Object { $_.id -eq $sbpId } | Select-Object -First 1)
Assert 'SBP admin transferType' ($adminSbp.transferType -eq 'SBP_PHONE') "transferType=$($adminSbp.transferType)"
Assert 'SBP admin phone' ($adminSbp.phoneNumber -eq $phone) "phone=$($adminSbp.phoneNumber)"
Assert 'SBP admin recipient' ($adminSbp.recipientName -eq 'Petr Petrov') "recipient=$($adminSbp.recipientName)"
Assert 'SBP admin bank' ($adminSbp.bankName -eq 'T-Bank') "bank=$($adminSbp.bankName)"
Assert 'SBP admin no card' ([string]::IsNullOrEmpty($adminSbp.cardNumber)) "cardNumber=$($adminSbp.cardNumber)"

Write-Host ''
Write-Host '=== Validation errors ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status IN ('PENDING','SCHEDULED','PROCESSING');" | Out-Null

$errNoCard = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'CARD'; recipientName = 'Test' }
}
Assert 'CARD no card field' ($errNoCard.Body.conflictField -eq 'cardNumber') "field=$($errNoCard.Body.conflictField) msg=$($errNoCard.Body.message)"

$errNoNameCard = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'CARD'; cardNumber = $cardDigits; recipientName = '  ' }
}
Assert 'CARD no name field' ($errNoNameCard.Body.conflictField -eq 'recipientName') "field=$($errNoNameCard.Body.conflictField)"

$errBadCard = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'CARD'; cardNumber = '123'; recipientName = 'Test' }
}
Assert 'CARD bad card field' ($errBadCard.Body.conflictField -eq 'cardNumber') "field=$($errBadCard.Body.conflictField) msg=$($errBadCard.Body.message)"

$errNoPhone = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'SBP_PHONE'; recipientName = 'Test'; bankName = 'Bank' }
}
Assert 'SBP no phone field' ($errNoPhone.Body.conflictField -eq 'phoneNumber') "field=$($errNoPhone.Body.conflictField)"

$errNoBank = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'SBP_PHONE'; phoneNumber = $phone; recipientName = 'Test' }
}
Assert 'SBP no bank field' ($errNoBank.Body.conflictField -eq 'bankName') "field=$($errNoBank.Body.conflictField)"

$errBadPhone = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount = 50; payoutMethod = 'BANK_TRANSFER'
    payoutDetails = @{ transferType = 'SBP_PHONE'; phoneNumber = '+7123'; recipientName = 'Test'; bankName = 'Bank' }
}
Assert 'SBP bad phone field' ($errBadPhone.Body.conflictField -eq 'phoneNumber') "field=$($errBadPhone.Body.conflictField) msg=$($errBadPhone.Body.message)"

Write-Host ''
Write-Host '=== Backward compatibility ==='
$legacyId = [guid]::NewGuid().ToString()
$legacyDetails = '{"cardNumber":"4111111111111111","recipientName":"Legacy User","bankName":"Old Bank"}'
$sql = "INSERT INTO delivery.courier_balance_payout_requests (id, courier_member_id, amount, payout_method, status, payout_details, created_at, updated_at) VALUES ('$legacyId', '$memberId', 10, 'BANK_TRANSFER', 'PAID', '$legacyDetails', now(), now());"
Db $sql | Out-Null
$adminLegacy = ((Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body |
    Where-Object { $_.id -eq $legacyId } | Select-Object -First 1)
Assert 'Legacy transferType CARD' ($adminLegacy.transferType -eq 'CARD') "transferType=$($adminLegacy.transferType)"
Assert 'Legacy card preserved' ($adminLegacy.cardNumber -eq '4111111111111111') "card=$($adminLegacy.cardNumber)"
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE id = '$legacyId';" | Out-Null

Write-Host ''
Write-Host '=== Backend logs check ==='
docker logs delivery_dev-delivery-backend-1 --since 5m 2>&1 | Out-File -FilePath "$env:TEMP\payout-logs.txt" -Encoding utf8
$logs = Get-Content "$env:TEMP\payout-logs.txt" -Raw
Assert 'Logs no full CARD' ($logs -notmatch $cardDigits) '5555555555555555 not in logs'
Assert 'Logs no full SBP phone' ($logs -notmatch '9991234567') '9991234567 not in logs'

Write-Host ''
$passed = ($results.Values | Where-Object { $_.pass }).Count
$total = $results.Count
Write-Host "=== SUMMARY: $passed / $total passed ===" -ForegroundColor $(if ($passed -eq $total) { 'Green' } else { 'Yellow' })
$results.GetEnumerator() | ForEach-Object { [pscustomobject]@{ Test = $_.Key; Pass = $_.Value.pass; Detail = $_.Value.detail } } | Format-Table -AutoSize

if ($passed -ne $total) { exit 1 }
