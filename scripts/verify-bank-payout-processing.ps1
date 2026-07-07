$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$internalKey = 'dev-monolith-internal-key'
$serviceId = '759ee600-1004-4a7b-8d82-37f11f977c75'
$memberId = '69d785de-1e6e-46c6-944a-7bddf78df654'
$accountId = '8e5afea7-4a15-4c92-b8ff-714e067fb8b8'
$courierUserId = 1000000000001
$ownerUserId = 1000000000000

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
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress -Depth 6) }
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

$courierToken = Get-Token $courierUserId
$ownerToken = Get-Token $ownerUserId

Write-Host '=== CLEANUP ==='
Db "DELETE FROM delivery.partner_payout_requests WHERE partner_account_id = '$accountId' AND status IN ('SCHEDULED','PENDING','PROCESSING');" | Out-Null
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status = 'PENDING';" | Out-Null
Db "UPDATE delivery.partner_accounts SET pending_payout = 0, updated_at = now() WHERE id = '$accountId';" | Out-Null

$partnerBefore = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
$mainBefore = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "PARTNER before: balance=$($partnerBefore.balance.balance) eligible=$($partnerBefore.balance.eligibleForRequest) pending=$($partnerBefore.balance.pendingPayout)"
Write-Host "MAIN before: balance=$($mainBefore.balance) available=$($mainBefore.availableForPayout) pending=$($mainBefore.pendingPayout)"

Write-Host ''
Write-Host '=== PARTNER: create bank payout ==='
$cardFull = '4276123456789012'
$partnerCreate = Invoke-Delivery POST "/couriers/$memberId/partner-program/payout-requests" $courierToken @{
    amount        = 500
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = $cardFull; recipientName = 'Test Courier'; bankName = 'Sberbank' }
}
if (-not $partnerCreate.Ok) {
    Write-Host "FAIL create partner payout: $($partnerCreate.Status) $($partnerCreate.Raw)"
    exit 1
}
$partnerPayoutId = $partnerCreate.Body.id
Write-Host "Created partner payout id=$partnerPayoutId status=$($partnerCreate.Body.status) scheduled=$($partnerCreate.Body.scheduledPayoutDate) cardMask=$($partnerCreate.Body.cardMask)"

$partnerAfterCreate = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
Write-Host "PARTNER after create: eligible=$($partnerAfterCreate.balance.eligibleForRequest) pending=$($partnerAfterCreate.balance.pendingPayout)"

$adminList = (Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body
$adminPartner = $adminList | Where-Object { $_.id -eq $partnerPayoutId }
Write-Host "Admin list: balanceSource=$($adminPartner.balanceSource) status=$($adminPartner.status) cardEnds=$($adminPartner.cardNumber.Substring($adminPartner.cardNumber.Length - 4)) recipient=$($adminPartner.recipientName) bank=$($adminPartner.bankName)"

Write-Host ''
Write-Host '=== PARTNER: reject without comment (expect 400) ==='
$rejectNoComment = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId}/process?courierServiceId=$serviceId&approve=false" $ownerToken $null
Write-Host "Reject no comment: HTTP $($rejectNoComment.Status) error=$($rejectNoComment.Body.error)"

Write-Host ''
Write-Host '=== PARTNER: approve before date (expect conflict) ==='
$approveEarly = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId}/process?courierServiceId=$serviceId&approve=true" $ownerToken $null
Write-Host "Approve early: HTTP $($approveEarly.Status) error=$($approveEarly.Body.error)"

Write-Host ''
Write-Host '=== PARTNER: reject with comment ==='
$rejectOk = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId}/process?courierServiceId=$serviceId&approve=false&comment=Test+rejection+reason" $ownerToken $null
if (-not $rejectOk.Ok) {
    Write-Host "FAIL reject: $($rejectOk.Raw)"
    exit 1
}
Write-Host "Rejected status=$($rejectOk.Body.status)"

$partnerAfterReject = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
$rejectedHistory = $partnerAfterReject.payoutHistory | Where-Object { $_.id -eq $partnerPayoutId }
Write-Host "PARTNER after reject: eligible=$($partnerAfterReject.balance.eligibleForRequest) pending=$($partnerAfterReject.balance.pendingPayout) rejectionComment=$($rejectedHistory.rejectionComment)"

$doubleReject = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId}/process?courierServiceId=$serviceId&approve=false&comment=Again" $ownerToken $null
Write-Host "Double reject: HTTP $($doubleReject.Status) (expect 409)"

Write-Host ''
Write-Host '=== PARTNER: create second payout for approve test ==='
$partnerCreate2 = Invoke-Delivery POST "/couriers/$memberId/partner-program/payout-requests" $courierToken @{
    amount        = 500
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = '4276123456789012'; recipientName = 'Test Courier'; bankName = 'Sberbank' }
}
$partnerPayoutId2 = $partnerCreate2.Body.id
Write-Host "Created id=$partnerPayoutId2 status=$($partnerCreate2.Body.status) scheduled=$($partnerCreate2.Body.scheduledPayoutDate)"

Db "UPDATE delivery.partner_payout_requests SET scheduled_payout_date = CURRENT_DATE - interval '1 day' WHERE id = '$partnerPayoutId2';" | Out-Null

$partnerBeforeApprove = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
$balanceBeforeApprove = $partnerBeforeApprove.balance.balance
$pendingBeforeApprove = $partnerBeforeApprove.balance.pendingPayout

$approveOk = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId2}/process?courierServiceId=$serviceId&approve=true" $ownerToken $null
if (-not $approveOk.Ok) {
    Write-Host "FAIL approve: $($approveOk.Raw)"
    exit 1
}
Write-Host "Approved status=$($approveOk.Body.status)"

$partnerAfterApprove = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
Write-Host "PARTNER after approve: balance=$($partnerAfterApprove.balance.balance) (was $balanceBeforeApprove) pending=$($partnerAfterApprove.balance.pendingPayout) (was $pendingBeforeApprove)"

$doubleApprove = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId2}/process?courierServiceId=$serviceId&approve=true" $ownerToken $null
Write-Host "Double approve: HTTP $($doubleApprove.Status) (expect 409)"

Write-Host ''
Write-Host '=== MAIN: create bank payout ==='
Db "DELETE FROM delivery.courier_balance_payout_requests WHERE courier_member_id = '$memberId' AND status = 'PENDING';" | Out-Null
$mainCreate = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount        = 25
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = '5536914012345678'; recipientName = 'Main Balance User'; bankName = 'Tinkoff' }
}
if (-not $mainCreate.Ok) {
    Write-Host "FAIL main create: $($mainCreate.Raw)"
    exit 1
}
$mainPayoutId = $mainCreate.Body.id
Write-Host "Created main payout id=$mainPayoutId status=$($mainCreate.Body.status) cardMask=$($mainCreate.Body.cardMask)"

$mainAfterCreate = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "MAIN after create: balance=$($mainAfterCreate.balance) available=$($mainAfterCreate.availableForPayout) pending=$($mainAfterCreate.pendingPayout)"

$adminMain = @((Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body | Where-Object { $_.id -eq $mainPayoutId })
Write-Host "Admin main: balanceSource=$($adminMain.balanceSource) recipient=$($adminMain.recipientName) bank=$($adminMain.bankName) cardEnds=$($adminMain.cardNumber.Substring($adminMain.cardNumber.Length - 4))"

Write-Host ''
Write-Host '=== MAIN: reject ==='
$mainReject = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests/${mainPayoutId}/process?courierServiceId=$serviceId&approve=false&comment=Main+reject+reason" $ownerToken $null
Write-Host "Main rejected status=$($mainReject.Body.status) comment=$($mainReject.Body.rejectionComment)"

$mainAfterReject = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "MAIN after reject: balance=$($mainAfterReject.balance) available=$($mainAfterReject.availableForPayout) pending=$($mainAfterReject.pendingPayout)"

Write-Host ''
Write-Host '=== MAIN: create and approve ==='
$mainCreate2 = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests" $courierToken @{
    amount        = 30
    payoutMethod  = 'BANK_TRANSFER'
    payoutDetails = @{ cardNumber = '5536914012345678'; recipientName = 'Main Balance User'; bankName = 'Tinkoff' }
}
$mainPayoutId2 = $mainCreate2.Body.id
$balBefore = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "Before approve: balance=$($balBefore.balance) available=$($balBefore.availableForPayout)"

$mainApprove = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests/${mainPayoutId2}/process?courierServiceId=$serviceId&approve=true" $ownerToken $null
Write-Host "Main approved status=$($mainApprove.Body.status)"

$balAfter = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
Write-Host "After approve: balance=$($balAfter.balance) available=$($balAfter.availableForPayout) paidOut=$($balAfter.paidOut)"

$mainDouble = Invoke-Delivery POST "/couriers/$memberId/balance/payout-requests/${mainPayoutId2}/process?courierServiceId=$serviceId&approve=true" $ownerToken $null
Write-Host "Main double approve: HTTP $($mainDouble.Status) (expect 409)"

Write-Host ''
Write-Host '=== SECURITY: wrong service admin ==='
$wrong = Invoke-Delivery POST "/partner-program/payout-requests/${partnerPayoutId2}/process?courierServiceId=00000000-0000-0000-0000-000000000099&approve=true" $ownerToken $null
Write-Host "Wrong service UUID process partner: HTTP $($wrong.Status) (expect 403)"

Write-Host ''
Write-Host '=== LOGS: full card number check (count only) ==='
$logText = docker compose -f docker-compose.dev.yml logs --tail=300 delivery-backend 2>&1 | Out-String
$hits4276 = ([regex]::Matches($logText, '4276123456789012')).Count
$hits5536 = ([regex]::Matches($logText, '5536914012345678')).Count
Write-Host "Full card hits in last 300 log lines: $($hits4276 + $hits5536) (expect 0)"

Write-Host ''
Write-Host '=== MASK CHECK ==='
$hist = $balAfter.payoutHistory | Where-Object { $_.id -eq $mainPayoutId2 }
Write-Host "Courier main history cardMask=$($hist.cardMask) (should be masked)"

Write-Host ''
Write-Host '=== ALL CHECKS COMPLETE ==='
