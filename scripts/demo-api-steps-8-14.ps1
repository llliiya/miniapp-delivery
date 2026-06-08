# API smoke for steps 8-14 (legacy restaurant; service user creates order)
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$tail = Get-Random -Maximum 9999
$pass = "ServicePass1!"

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

$svcPhone = "+79379{0:D4}" -f $tail
$c1Phone = "+79377{0:D4}" -f $tail
$c2Phone = "+79376{0:D4}" -f ($tail + 1)

$req = Curl-Json POST "/api/auth/register/phone/request" (@{ phone = $svcPhone } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/auth/register/phone/verify" (@{ challengeId = $req.challengeId; pin = "1234" } | ConvertTo-Json -Compress) $null | Out-Null
$sp = Curl-Json POST "/api/auth/register/set-password" (@{ phone = $svcPhone; password = $pass } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/auth/register/email/request" (@{ registrationToken = $sp.registrationToken; email = "svc$tail@mvp.test" } | ConvertTo-Json -Compress) $null | Out-Null
$done = Curl-Json POST "/api/auth/register/email/verify" (@{ registrationToken = $sp.registrationToken; email = "svc$tail@mvp.test"; code = "000000" } | ConvertTo-Json -Compress) $null
$svcTok = $done.accessToken
$org = Curl-Json POST "/api/delivery/organizations" (@{ type = "courier_service"; name = "Demo $tail" } | ConvertTo-Json -Compress) $svcTok
$sid = $org.id
$rest = Curl-Json POST "/api/delivery/restaurants" (@{ name = "Obj $tail"; courierServiceId = $sid } | ConvertTo-Json -Compress) $svcTok
$rid = $rest.object.id
$pp = Curl-Json POST "/api/delivery/restaurants/$rid/pickup-points" (@{ name = "Main"; address = "Pick 1"; isDefault = $true } | ConvertTo-Json -Compress) $svcTok
$cr1 = Curl-Json POST "/api/delivery/couriers" (@{ courierServiceId = $sid; fullName = "Ivan"; phone = $c1Phone } | ConvertTo-Json -Compress) $svcTok
$c1Login = $cr1.credentials.login
$c1Temp = $cr1.credentials.temporaryPassword
$c1Mid = $cr1.courier.memberId
$c1Pid = $cr1.courier.publicId
$cr2 = Curl-Json POST "/api/delivery/couriers" (@{ courierServiceId = $sid; fullName = "Petr"; phone = $c2Phone } | ConvertTo-Json -Compress) $svcTok
$c2Login = $cr2.credentials.login
$c2Temp = $cr2.credentials.temporaryPassword
$dt = (Get-Date).AddHours(2).ToUniversalTime().ToString("o")
$created = Curl-Json POST "/api/delivery/orders" (@{
  restaurantId = $rid; pickupPointId = $pp.id; deliveryAddress = "Client 10"
  deliveryTime = $dt; price = 450; customerPhone = "+79001112233"; comment = "demo"
} | ConvertTo-Json -Compress) $svcTok
$oid = $created.order.id
Write-Host "[OK] order $oid restaurantName=$($created.order.restaurantName)"

$cl = Curl-Json POST "/api/auth/login" (@{ login = $c1Login; password = $c1Temp } | ConvertTo-Json -Compress) $null
Curl-Json POST "/api/profile/security/password" (@{ currentPassword = $c1Temp; newPassword = "CourierPass1!" } | ConvertTo-Json -Compress) $cl.accessToken | Out-Null
$c1Tok = (Curl-Json POST "/api/auth/login" (@{ login = $c1Login; password = "CourierPass1!" } | ConvertTo-Json -Compress) $null).accessToken

$acc = Curl-Json POST "/api/delivery/orders/$oid/accept" "{}" $c1Tok
Write-Host "[OK] accept courierDisplayName=$($acc.courierDisplayName) courierPublicId=$($acc.courierPublicId)"
$det = Curl-Json GET "/api/delivery/orders/$oid" $null $svcTok
Write-Host "[OK] GET/{id} restaurantName=$($det.restaurantName) courierDisplayName=$($det.courierDisplayName) courierPublicId=$($det.courierPublicId)"
$list = Curl-Json GET "/api/delivery/orders?courierServiceId=$sid" $null $svcTok
$lo = $list | Where-Object { $_.id -eq $oid } | Select-Object -First 1
Write-Host "[OK] GET list restaurantName=$($lo.restaurantName) courierPublicId=$($lo.courierPublicId)"

$c2l = Curl-Json POST "/api/auth/login" (@{ login = $c2Login; password = $c2Temp } | ConvertTo-Json -Compress) $null
try {
  Curl-Json POST "/api/delivery/orders/$oid/accept" "{}" $c2l.accessToken
  Write-Host "[FAIL] courier2 accepted"
} catch {
  if ($_ -match "409|order_already") { Write-Host "[OK] courier2 conflict 409" } else { Write-Host "[WARN] courier2: $_" }
}

Curl-Json POST "/api/delivery/orders/$oid/status" (@{ status = "courier_delivering" } | ConvertTo-Json -Compress) $c1Tok | Out-Null
Curl-Json POST "/api/delivery/orders/$oid/status" (@{ status = "completed" } | ConvertTo-Json -Compress) $c1Tok | Out-Null
$before = (Curl-Json GET "/api/delivery/couriers/$c1Mid" $null $svcTok).completedOrdersCount
$after = (Curl-Json GET "/api/delivery/couriers/$c1Mid" $null $svcTok).completedOrdersCount
Write-Host "[OK] completedOrders $before -> $after login=$c1Login publicId=$c1Pid match=$($c1Login -eq "courier_$c1Pid")"

Curl-Json PATCH "/api/delivery/couriers/$c1Mid" (@{ status = "blocked" } | ConvertTo-Json -Compress) $svcTok | Out-Null
try {
  Curl-Json GET "/api/delivery/orders?scope=courier" $null $c1Tok
  Write-Host "[FAIL] blocked courier still lists orders"
} catch {
  if ($_ -match "403") { Write-Host "[OK] blocked courier 403 on orders" } else { Write-Host "[WARN] blocked: $_" }
}
Curl-Json PATCH "/api/delivery/couriers/$c1Mid" (@{ status = "active" } | ConvertTo-Json -Compress) $svcTok | Out-Null
Write-Host "[OK] unblocked"
