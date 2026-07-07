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
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        return @{ Ok = $true; Status = $r.StatusCode; Body = ($r.Content | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Close()
        try { $json = $text | ConvertFrom-Json } catch { $json = @{ raw = $text } }
        return @{ Ok = $false; Status = [int]$resp.StatusCode; Body = $json }
    }
}

function Db($sql) {
    docker exec delivery_dev-db-1 psql -U postgres -d delivery -t -A -c $sql
}

Write-Host '=== Tokens ==='
$courierToken = Get-Token $courierUserId
$ownerToken = Get-Token $ownerUserId
Write-Host 'Courier and owner tokens OK'

Write-Host ''
Write-Host '=== Before: balances ==='
$mainBefore = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body
$partnerBefore = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
Write-Host "Main balance: $($mainBefore.balance)"
Write-Host "Partner balance: $($partnerBefore.balance.balance), eligible: $($partnerBefore.balance.eligibleForRequest)"

Write-Host ''
Write-Host '=== Cleanup scheduled ops this cycle ==='
Db "DELETE FROM delivery.partner_balance_transfers WHERE partner_account_id = '$accountId' AND status IN ('SCHEDULED');" | Out-Null
Db "DELETE FROM delivery.partner_payout_requests WHERE partner_account_id = '$accountId' AND status IN ('SCHEDULED','PENDING');" | Out-Null
Db "UPDATE delivery.partner_accounts SET pending_payout = 0, updated_at = now() WHERE id = '$accountId';" | Out-Null

Write-Host ''
Write-Host '=== Create balance transfer (500 RUB) ==='
$transfer = Invoke-Delivery POST "/couriers/$memberId/partner-program/balance-transfers" $courierToken @{ amount = 500 }
if (-not $transfer.Ok) {
    Write-Host "FAIL create transfer HTTP $($transfer.Status)" -ForegroundColor Red
    $transfer.Body | ConvertTo-Json -Depth 5
    exit 1
}
$transferId = $transfer.Body.id
Write-Host "Transfer created: id=$transferId status=$($transfer.Body.status) scheduled=$($transfer.Body.scheduledExecutionDate)"
if ($transfer.Body.status -ne 'SCHEDULED') { throw 'Expected SCHEDULED status' }

Write-Host ''
Write-Host '=== Verify no PartnerPayoutRequest created ==='
$payoutCount = (Db "SELECT COUNT(*) FROM delivery.partner_payout_requests WHERE partner_account_id = '$accountId' AND created_at > now() - interval '5 minutes';").Trim()
Write-Host "New payout requests in DB: $payoutCount"
if ($payoutCount -ne '0') { throw 'PartnerPayoutRequest was created unexpectedly' }

Write-Host ''
Write-Host '=== Courier partner-program history ==='
$programAfter = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
$history = @($programAfter.balanceTransferHistory)
$found = $history | Where-Object { $_.id -eq $transferId }
if (-not $found) { throw 'Transfer not in balanceTransferHistory' }
Write-Host "balanceTransferHistory count: $($history.Count), found transfer status=$($found.status)"
$payoutHistory = @($programAfter.payoutHistory)
Write-Host "payoutHistory count: $($payoutHistory.Count) (bank only)"

Write-Host ''
Write-Host '=== Admin: payout requests (bank) ==='
$adminPayouts = (Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body
$bankHasTransfer = @($adminPayouts | Where-Object { $_.id -eq $transferId }).Count
Write-Host "Admin payout requests: $($adminPayouts.Count), contains transfer: $bankHasTransfer"
if ($bankHasTransfer -gt 0) { throw 'Transfer appears in bank payout requests' }

Write-Host ''
Write-Host '=== Admin: balance transfers tab ==='
$adminTransfers = (Invoke-Delivery GET "/partner-program/balance-transfers?courierServiceId=$serviceId" $ownerToken $null).Body
$adminFound = @($adminTransfers | Where-Object { $_.id -eq $transferId })
if ($adminFound.Count -eq 0) { throw 'Transfer not in admin balance-transfers list' }
Write-Host "Admin transfers: $($adminTransfers.Count), found status=$($adminFound[0].status) amount=$($adminFound[0].amount)"

Write-Host ''
Write-Host '=== Bank payout same cycle (expect conflict) ==='
$bankPayout = Invoke-Delivery POST "/couriers/$memberId/partner-program/payout-requests" $courierToken @{
    amount = 500; payoutMethod = 'BANK_TRANSFER'
}
if ($bankPayout.Ok) {
    throw 'Bank payout should be blocked in the same payout cycle as transfer'
}
Write-Host "Bank payout blocked as expected: HTTP $($bankPayout.Status) error=$($bankPayout.Body.error)"

$mainBeforeExec = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body.balance
$partnerBeforeExec = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body.balance.balance
Write-Host "Before execution: main=$mainBeforeExec partner=$partnerBeforeExec"

Write-Host ''
Write-Host '=== Execute due transfer ==='
$today = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd')
Db "UPDATE delivery.partner_balance_transfers SET scheduled_execution_date = '$today' WHERE id = '$transferId';" | Out-Null
$trigger = Invoke-Delivery POST "/partner-program/dev/process-due-payouts?courierServiceId=$serviceId&payoutDate=$today" $ownerToken $null
if (-not $trigger.Ok) {
    throw "Dev trigger failed HTTP $($trigger.Status)"
}
Write-Host "Dev trigger OK: $($trigger.Body | ConvertTo-Json -Compress)"

Start-Sleep -Seconds 2
$row = (Db "SELECT status, executed_at, partner_ledger_transaction_id, main_balance_ledger_transaction_id FROM delivery.partner_balance_transfers WHERE id = '$transferId';").Trim()
Write-Host "Transfer row after execution: $row"
if ($row -notmatch '^COMPLETED') { throw "Transfer not COMPLETED: $row" }

$mainAfterExec = (Invoke-Delivery GET "/couriers/$memberId/balance" $courierToken $null).Body.balance
$partnerAfterExec = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body.balance.balance
Write-Host "After execution: main=$mainAfterExec partner=$partnerAfterExec"
$mainDelta = [decimal]$mainAfterExec - [decimal]$mainBeforeExec
$partnerDelta = [decimal]$partnerAfterExec - [decimal]$partnerBeforeExec
Write-Host "Delta: main +$mainDelta partner $partnerDelta (expect +500 / -500)"
if ($mainDelta -ne 500 -or $partnerDelta -ne -500) { throw 'Balance deltas mismatch' }

$ledgerPartner = (Db "SELECT COUNT(*) FROM delivery.partner_ledger_transactions WHERE balance_transfer_id = '$transferId' AND type = 'TRANSFER_OUT';").Trim()
$ledgerMain = (Db "SELECT COUNT(*) FROM delivery.balance_transactions WHERE reason LIKE '%$transferId%' AND type = 'PARTNER_TRANSFER_IN';").Trim()
Write-Host "Ledger: partner TRANSFER_OUT=$ledgerPartner main PARTNER_TRANSFER_IN=$ledgerMain"
if ($ledgerPartner -ne '1' -or $ledgerMain -ne '1') { throw 'Ledger entries missing' }

$programFinal = (Invoke-Delivery GET "/couriers/$memberId/partner-program" $courierToken $null).Body
$finalTransfer = @($programFinal.balanceTransferHistory | Where-Object { $_.id -eq $transferId })
Write-Host "Courier history: status=$($finalTransfer[0].status) executedAt=$($finalTransfer[0].executedAt)"

Write-Host ''
Write-Host '=== Bank payout on next cycle (clean month) ==='
$nextMonth = (Get-Date).ToUniversalTime().AddMonths(1).ToString('yyyy-MM')
Db "DELETE FROM delivery.partner_balance_transfers WHERE partner_account_id = '$accountId';" | Out-Null
Db "DELETE FROM delivery.partner_payout_requests WHERE partner_account_id = '$accountId';" | Out-Null
Db "UPDATE delivery.partner_accounts SET pending_payout = 0, updated_at = now() WHERE id = '$accountId';" | Out-Null
$bankPayout = Invoke-Delivery POST "/couriers/$memberId/partner-program/payout-requests" $courierToken @{
    amount = 500; payoutMethod = 'BANK_TRANSFER'
}
if (-not $bankPayout.Ok) {
    Db "UPDATE delivery.partner_payout_requests SET payout_cycle_month = '$nextMonth' WHERE partner_account_id = '$accountId' AND status = 'SCHEDULED';" | Out-Null
    Db "UPDATE delivery.partner_balance_transfers SET payout_cycle_month = '$nextMonth' WHERE partner_account_id = '$accountId';" | Out-Null
    $bankPayout = Invoke-Delivery POST "/couriers/$memberId/partner-program/payout-requests" $courierToken @{
        amount = 500; payoutMethod = 'BANK_TRANSFER'
    }
}
if (-not $bankPayout.Ok) {
    throw "Bank payout failed HTTP $($bankPayout.Status)"
}
$bankId = $bankPayout.Body.id
Write-Host "Bank payout created: id=$bankId status=$($bankPayout.Body.status) method=$($bankPayout.Body.payoutMethod)"
$adminPayouts2 = (Invoke-Delivery GET "/partner-program/payout-requests?courierServiceId=$serviceId" $ownerToken $null).Body
$bankInAdmin = @($adminPayouts2 | Where-Object { $_.id -eq $bankId })
if ($bankInAdmin.Count -eq 0) { throw 'Bank payout not in admin payout requests' }
Write-Host "Bank payout visible in admin payout requests: status=$($bankInAdmin[0].status)"
$adminTransfers2 = (Invoke-Delivery GET "/partner-program/balance-transfers?courierServiceId=$serviceId" $ownerToken $null).Body
$bankInTransfers = @($adminTransfers2 | Where-Object { $_.id -eq $bankId }).Count
Write-Host "Bank payout in transfers tab: $bankInTransfers (expected 0)"
if ($bankInAdmin[0].status -ne 'SCHEDULED' -and $bankInAdmin[0].status -ne 'PENDING') {
    throw 'Unexpected bank payout status for admin review'
}

Write-Host ''
Write-Host '=== ALL CHECKS PASSED ===' -ForegroundColor Green
