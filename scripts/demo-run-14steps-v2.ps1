# Final demo - reliable JSON via curl.exe
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$report = New-Object System.Collections.Generic.List[string]
function Log($m, $l = "INFO") { $line = "[$l] $m"; Write-Host $line; $report.Add($line) }

function Curl-Json($Method, $Path, $JsonBody, $Token) {
  $tmp = [System.IO.Path]::GetTempFileName() + ".json"
  if ($JsonBody) { [System.IO.File]::WriteAllText($tmp, $JsonBody, [System.Text.UTF8Encoding]::new($false)) }
  $args = @("-s", "-w", "`nHTTP_CODE:%{http_code}", "-X", $Method, "$base$Path", "-H", "Content-Type: application/json")
  if ($Token) { $args += @("-H", "Authorization: Bearer $Token") }
  if ($JsonBody) { $args += @("--data-binary", "@$tmp") }
  $out = & curl.exe @args
  if ($JsonBody) { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
  $lines = $out -split "`n"
  $codeLine = $lines | Where-Object { $_ -match "^HTTP_CODE:" } | Select-Object -Last 1
  $code = [int]($codeLine -replace "HTTP_CODE:", "")
  $body = ($lines | Where-Object { $_ -notmatch "^HTTP_CODE:" }) -join "`n"
  if ($code -ge 400) { throw "HTTP $code $Path : $body" }
  if ($body) { return $body | ConvertFrom-Json }
  return $null
}

Log "=== Demo v2 $(Get-Date -Format o) ==="
$tail = Get-Random -Maximum 9999
$svcPhone = "+79379{0:D4}" -f $tail
$ownerPhone = "+79378{0:D4}" -f ($tail + 1)
$c1Phone = "+79377{0:D4}" -f ($tail + 2)
$c2Phone = "+79376{0:D4}" -f ($tail + 3)
$pass = "ServicePass1!"

# Register service owner
$req = Curl-Json POST "/api/auth/register/phone/request" (@{ phone = $svcPhone } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/auth/register/phone/verify" (@{ challengeId = $req.challengeId; pin = "1234" } | ConvertTo-Json -Compress) $null | Out-Null
$sp = Curl-Json POST "/api/auth/register/set-password" (@{ phone = $svcPhone; password = $pass } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/auth/register/email/request" (@{ registrationToken = $sp.registrationToken; email = "svc$tail@mvp.test" } | ConvertTo-Json -Compress) $null | Out-Null
$done = Curl-Json POST "/api/auth/register/email/verify" (@{ registrationToken = $sp.registrationToken; email = "svc$tail@mvp.test"; code = "000000" } | ConvertTo-Json -Compress) $null
$svcTok = $done.accessToken
Log "Service owner OK" "OK"

$org = Curl-Json POST "/api/delivery/organizations" (@{ type = "courier_service"; name = "Demo Service $tail" } | ConvertTo-Json -Compress) $svcTok
$serviceId = $org.id
Log "Service org $serviceId" "OK"

# Step 1
Log "--- Step 1 ---"
$restBody = @{
  name = "Andryusha Sushi $tail"
  courierServiceId = $serviceId
  owner = @{ fullName = "Owner Demo"; phone = $ownerPhone }
} | ConvertTo-Json -Compress -Depth 5
Curl-Json GET "/api/delivery/restaurants" $null $svcTok | Out-Null
Log "GET /restaurants OK before POST" "OK"
$rest = Curl-Json POST "/api/delivery/restaurants" $restBody $svcTok
$restId = $rest.object.id
$oLogin = $rest.ownerCredentials.login
$oTemp = $rest.ownerCredentials.temporaryPassword
Log "Object $restId owner login=$oLogin" "OK"

# Step 2
Log "--- Step 2 ---"
$ol = Curl-Json POST "/api/auth/login" (@{ login = $oLogin; password = $oTemp } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/profile/security/password" (@{ currentPassword = $oTemp; newPassword = "OwnerPass1!" } | ConvertTo-Json -Compress) $ol.accessToken | Out-Null
$oa = Curl-Json POST "/api/auth/login" (@{ login = $oLogin; password = "OwnerPass1!" } | ConvertTo-Json -Compress) $null
$oTok = $oa.accessToken
$oMe = Curl-Json GET "/api/delivery/me" $null $oTok
Log "Owner mode=$($oMe.interfaceMode)" "OK"

# Step 3
Log "--- Step 3 ---"
$pp = Curl-Json POST "/api/delivery/restaurants/$restId/pickup-points" (@{ name = "Main"; address = "St 1"; isDefault = $true } | ConvertTo-Json -Compress) $oTok
Log "Pickup $($pp.id) default=$($pp.isDefault)" "OK"

# Step 4-5
Log "--- Step 4-5 ---"
$ch = Curl-Json POST "/api/delivery/channels" (@{
  courierServiceId = $serviceId; type = "telegram"; chatType = "channel"
  name = "Demo Ch"; externalId = "-1001234567890"; isActive = $true
} | ConvertTo-Json -Compress) $svcTok
Curl-Json PUT "/api/delivery/restaurants/$restId/channels" (@{ channelIds = @($ch.id) } | ConvertTo-Json -Compress) $svcTok | Out-Null
Log "Channel $($ch.id) bound" "OK"

# Step 6-7
Log "--- Step 6-7 ---"
$cr1 = Curl-Json POST "/api/delivery/couriers" (@{ courierServiceId = $serviceId; fullName = "Ivan Courier"; phone = $c1Phone } | ConvertTo-Json -Compress) $svcTok
$c1Login = $cr1.credentials.login
$c1Temp = $cr1.credentials.temporaryPassword
$c1Pid = $cr1.courier.publicId
$c1Mid = $cr1.courier.memberId
Log "Courier1 $c1Login publicId=$c1Pid match=$($c1Login -eq "courier_$c1Pid")" "OK"
$cl = Curl-Json POST "/api/auth/login" (@{ login = $c1Login; password = $c1Temp } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/profile/security/password" (@{ currentPassword = $c1Temp; newPassword = "CourierPass1!" } | ConvertTo-Json -Compress) $cl.accessToken | Out-Null
$c1a = Curl-Json POST "/api/auth/login" (@{ login = $c1Login; password = "CourierPass1!" } | ConvertTo-Json -Compress) $null
$c1Tok = $c1a.accessToken
Log "Courier1 logged in" "OK"

$cr2 = Curl-Json POST "/api/delivery/couriers" (@{ courierServiceId = $serviceId; fullName = "Petr Courier"; phone = $c2Phone } | ConvertTo-Json -Compress) $svcTok
$c2Login = $cr2.credentials.login
$c2Temp = $cr2.credentials.temporaryPassword
Log "Courier2 $c2Login" "OK"

# Step 8-9
Log "--- Step 8-9 ---"
$dt = (Get-Date).AddHours(2).ToUniversalTime().ToString("o")
$created = Curl-Json POST "/api/delivery/orders" (@{
  restaurantId = $restId; pickupPointId = $pp.id; deliveryAddress = "Client 10"
  deliveryTime = $dt; price = 450; customerPhone = "+79001112233"; comment = "demo"
} | ConvertTo-Json -Compress) $oTok
$orderId = $created.order.id
$warn = $created.warnings
$o = $created.order
$props = $o.PSObject.Properties.Name
foreach ($f in @("restaurantName","courierDisplayName","courierPublicId")) {
  if ($props -contains $f) { Log "create has $f=$($o.$f)" "OK" } else { Log "create MISSING $f" "FAIL" }
}
if ($warn -contains "no_active_channels") { Log "Step9 BLOCKER no_active_channels (fake chat id)" "FAIL" }
elseif ($o.publishedAt) { Log "Step9 publishedAt=$($o.publishedAt) (TG may still fail with fake id)" "WARN" }
else { Log "Step9 publishedAt null" "WARN" }

# Step 10
Log "--- Step 10 ---"
$free = Curl-Json GET "/api/delivery/orders?scope=courier" $null $c1Tok
$fo = $free | Where-Object { $_.id -eq $orderId } | Select-Object -First 1
if ($fo) { Log "Free list restaurantName=$($fo.restaurantName)" "OK" } else { Log "Not in free list" "FAIL" }

# Step 11
Log "--- Step 11 ---"
$acc = Curl-Json POST "/api/delivery/orders/$orderId/accept" "{}" $c1Tok
foreach ($f in @("courierDisplayName","courierPublicId")) { Log "accept $f=$($acc.$f)" "OK" }
$ownV = Curl-Json GET "/api/delivery/orders/$orderId" $null $oTok
$svcV = Curl-Json GET "/api/delivery/orders/$orderId" $null $svcTok
Log "Owner sees courier=$($ownV.courierDisplayName) id=$($ownV.courierPublicId)" "OK"
Log "Service sees courier=$($svcV.courierDisplayName)" "OK"
$free2 = Curl-Json GET "/api/delivery/orders?scope=courier" $null $c1Tok
if (($free2 | Where-Object { $_.id -eq $orderId }).Count -eq 0) { Log "Removed from free" "OK" }

# Step 12
Log "--- Step 12 ---"
$cl2 = Curl-Json POST "/api/auth/login" (@{ login = $c2Login; password = $c2Temp } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/profile/security/password" (@{ currentPassword = $c2Temp; newPassword = "CourierPass2!" } | ConvertTo-Json -Compress) $cl2.accessToken | Out-Null
$c2a = Curl-Json POST "/api/auth/login" (@{ login = $c2Login; password = "CourierPass2!" } | ConvertTo-Json -Compress) $null
try {
  Curl-Json POST "/api/delivery/orders/$orderId/accept" "{}" $c2a.accessToken
  Log "Courier2 accepted - FAIL" "FAIL"
} catch {
  if ($_ -match "409|already|Conflict") { Log "Courier2 blocked 409" "OK" } else { Log "Courier2 err: $_" "WARN" }
}

# Step 13
Log "--- Step 13 ---"
$before = (Curl-Json GET "/api/delivery/couriers/$c1Mid" $null $svcTok).completedOrdersCount
$s1 = Curl-Json POST "/api/delivery/orders/$orderId/status" (@{ status = "courier_delivering" } | ConvertTo-Json -Compress) $c1Tok
Log "Status $($s1.status)" "OK"
$s2 = Curl-Json POST "/api/delivery/orders/$orderId/status" (@{ status = "completed" } | ConvertTo-Json -Compress) $c1Tok
$of = Curl-Json GET "/api/delivery/orders/$orderId" $null $oTok
$sf = Curl-Json GET "/api/delivery/orders/$orderId" $null $svcTok
Log "Final owner=$($of.status) service=$($sf.status)" $(if ($of.status -eq "completed") { "OK" } else { "FAIL" })
$hist = Curl-Json GET "/api/delivery/orders?scope=courier&status=completed" $null $c1Tok
if ($hist | Where-Object { $_.id -eq $orderId }) { Log "In completed history" "OK" }
$after = (Curl-Json GET "/api/delivery/couriers/$c1Mid" $null $svcTok).completedOrdersCount
Log "completedOrders $before -> $after" $(if ($after -gt $before) { "OK" } else { "WARN" })

# Step 14
Log "--- Step 14 ---"
Curl-Json PATCH "/api/delivery/couriers/$c1Mid" (@{ status = "blocked" } | ConvertTo-Json -Compress) $svcTok | Out-Null
try {
  Curl-Json GET "/api/delivery/orders?scope=courier" $null $c1Tok
  Log "Blocked still lists orders" "FAIL"
} catch {
  if ($_ -match "403") { Log "Blocked API 403" "OK" }
}
Curl-Json PATCH "/api/delivery/couriers/$c1Mid" (@{ status = "active" } | ConvertTo-Json -Compress) $svcTok | Out-Null
Log "Unblocked" "OK"

Log "=== DONE ==="
$report | ForEach-Object { $_ }
