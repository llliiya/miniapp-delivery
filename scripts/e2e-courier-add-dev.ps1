$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$serviceId = '759ee600-1004-4a7b-8d82-37f11f977c75'
$internalKey = 'dev-monolith-internal-key'
$results = @()
$case1MemberId = $null
$case1UserId = $null

function Add-Result($case, $status, $detail) {
    $script:results += [pscustomobject]@{ Case = $case; Status = $status; Detail = $detail }
    Write-Host "[$status] $case - $detail"
}

function Get-OwnerToken($userId) {
    $creds = Invoke-RestMethod -Method Post -Uri "$base/api/internal/monolith/users/$userId/reset-web-credentials" `
        -Headers @{ 'X-Internal-Key' = $internalKey } -ContentType 'application/json'
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
        -Body (@{ login = $creds.login; password = $creds.temporaryPassword } | ConvertTo-Json)
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
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        return @{ Ok = $true; Status = $r.StatusCode; Body = ($r.Content | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
            $reader.Close()
            try { $json = $text | ConvertFrom-Json } catch { $json = @{ raw = $text } }
            return @{ Ok = $false; Status = [int]$resp.StatusCode; Body = $json }
        }
        throw
    }
}

function Db-Query($sql) {
    (docker exec -i delivery_dev-db-1 psql -U postgres -d delivery -t -A -c $sql).Trim()
}

Write-Host '=== Auth owner A ==='
$tokenA = Get-OwnerToken 1000000000000
Add-Result 'auth' 'OK' 'owner A token'

Write-Host ''
Write-Host '=== Case 1 ==='
$case1 = Invoke-Delivery POST '/couriers' $tokenA @{
    courierServiceId = $serviceId
    fullName         = 'Petrov Petr'
    phone            = '+78945645645'
    email            = '11@mail.ru'
}
if ($case1.Ok -and $case1.Status -eq 201) {
    $case1MemberId = $case1.Body.courier.memberId
    $case1UserId = $case1.Body.courier.userId
    $hasMember = Db-Query "select count(*) from delivery.organization_members where organization_id='$serviceId' and user_id=$case1UserId and role='courier'"
    $hasProfile = Db-Query "select count(*) from delivery.courier_profiles where member_id='$case1MemberId'"
    $inList = Invoke-Delivery GET "/couriers?courierServiceId=$serviceId" $tokenA $null
    $listed = @($inList.Body | Where-Object { $_.memberId -eq $case1MemberId }).Count -gt 0
    if ($hasMember -eq '1' -and $hasProfile -eq '1' -and $listed) {
        Add-Result 'Case 1' 'PASS' "memberId=$case1MemberId userId=$case1UserId"
    } else {
        Add-Result 'Case 1' 'FAIL' "member=$hasMember profile=$hasProfile listed=$listed"
    }
} else {
    Add-Result 'Case 1' 'FAIL' "HTTP $($case1.Status)"
}

Write-Host ''
Write-Host '=== Case 2 ==='
$case2 = Invoke-Delivery POST '/couriers' $tokenA @{
    courierServiceId = $serviceId
    fullName         = 'Petrov Petr'
    phone            = '+78945645645'
    email            = '11@mail.ru'
}
if (-not $case2.Ok -and $case2.Status -eq 409 -and $case2.Body.error -eq 'courier_already_in_service' -and $case2.Body.existingCourierId) {
    Add-Result 'Case 2' 'PASS' "existingCourierId=$($case2.Body.existingCourierId) msg=$($case2.Body.message)"
} else {
    Add-Result 'Case 2' 'FAIL' ($case2.Body | ConvertTo-Json -Compress)
}

Write-Host ''
Write-Host '=== Case 3 ==='
$case3 = Invoke-Delivery POST '/couriers' $tokenA @{
    courierServiceId = $serviceId
    fullName         = 'Owner Test'
    phone            = '+79375231909'
    email            = 'liya1999.19@yandex.ru'
}
if (-not $case3.Ok -and $case3.Status -eq 409 -and $case3.Body.error -eq 'member_other_role') {
    Add-Result 'Case 3' 'PASS' $case3.Body.message
} else {
    Add-Result 'Case 3' 'FAIL' ($case3.Body | ConvertTo-Json -Compress)
}

Write-Host ''
Write-Host '=== Case 4 ==='
if ($case1MemberId) {
    $card = Invoke-Delivery GET "/couriers/$case1MemberId" $tokenA $null
    $profileId = Db-Query "select id from delivery.courier_profiles where member_id='$case1MemberId'"
    $route = (Invoke-WebRequest -Uri "http://localhost:5173/service/couriers/$case1MemberId" -UseBasicParsing).StatusCode
    if ($card.Ok -and $card.Body.memberId -eq $case1MemberId -and $card.Body.userId -eq $case1UserId -and $profileId -and $route -eq 200) {
        Add-Result 'Case 4' 'PASS' "memberId=$case1MemberId userId=$case1UserId profileId=$profileId route=$route"
    } else {
        Add-Result 'Case 4' 'FAIL' "card=$($card.Ok) route=$route"
    }
} else {
    Add-Result 'Case 4' 'SKIP' 'no case1 member'
}

Write-Host ''
Write-Host '=== Case 5 ==='
$ownerB = Db-Query "select user_id from delivery.organization_members where organization_id='$serviceId' and role='manager' limit 1"
if (-not $ownerB) {
    $prov = Invoke-RestMethod -Method Post -Uri "$base/api/internal/monolith/users/provision/web-employee" `
        -Headers @{ 'X-Internal-Key' = $internalKey } -ContentType 'application/json' `
        -Body (@{
            fullName            = 'Manager B'
            phone               = '+79990001122'
            email               = 'managerb-e2e@test.local'
            generateCredentials = $true
            source              = 'e2e'
            loginProfile        = 'courier'
        } | ConvertTo-Json)
    Invoke-RestMethod -Method Post -Uri "$base/api/delivery/organizations/$serviceId/members" `
        -Headers @{ Authorization = "Bearer $tokenA" } -ContentType 'application/json' `
        -Body (@{ role = 'manager'; userId = $prov.userId } | ConvertTo-Json) | Out-Null
    $ownerB = $prov.userId
}
$tokenB = Get-OwnerToken $ownerB
$listB = Invoke-Delivery GET "/couriers?courierServiceId=$serviceId" $tokenB $null
$seen = @($listB.Body | Where-Object { $_.memberId -eq $case1MemberId }).Count -gt 0
$cardB = Invoke-Delivery GET "/couriers/$case1MemberId" $tokenB $null
if ($seen -and $cardB.Ok) {
    Add-Result 'Case 5' 'PASS' "manager B userId=$ownerB sees courier"
} else {
    Add-Result 'Case 5' 'FAIL' "seen=$seen card=$($cardB.Ok)"
}

Write-Host ''
Write-Host '=== Summary ==='
$results | Format-Table -AutoSize
