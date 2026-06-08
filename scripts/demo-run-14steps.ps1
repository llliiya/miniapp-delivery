# Final demo run - API layer (gateway http://localhost:8080)
$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
$report = [System.Collections.Generic.List[string]]::new()

function Log($msg, $level = "INFO") {
  $line = "[$level] $msg"
  Write-Host $line
  $script:report.Add($line)
}

function Invoke-Api {
  param(
    [string]$Method = "GET",
    [string]$Path,
    [object]$Body = $null,
    [string]$Token = $null,
    [hashtable]$ExtraHeaders = @{}
  )
  $headers = @{ "Content-Type" = "application/json" }
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }
  foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }
  $uri = "$base$Path"
  try {
    if ($null -ne $Body) {
      return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body ($Body | ConvertTo-Json -Compress -Depth 8) -TimeoutSec 90
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -TimeoutSec 90
  } catch {
    $ex = $_.Exception
    $detail = $ex.Message
    if ($ex.Response) {
      try {
        $reader = [System.IO.StreamReader]::new($ex.Response.GetResponseStream())
        $detail = $reader.ReadToEnd()
        $reader.Close()
      } catch {}
    }
    throw "HTTP $Method $Path failed: $detail"
  }
}

function Register-ServiceOwner {
  param([string]$Phone, [string]$Password, [string]$Email)
  $req = Invoke-Api -Method POST -Path "/api/auth/register/phone/request" -Body @{ phone = $Phone }
  Invoke-Api -Method POST -Path "/api/auth/register/phone/verify" -Body @{ challengeId = $req.challengeId; pin = "1234" } | Out-Null
  $sp = Invoke-Api -Method POST -Path "/api/auth/register/set-password" -Body @{ phone = $Phone; password = $Password }
  $er = Invoke-Api -Method POST -Path "/api/auth/register/email/request" -Body @{
    registrationToken = $sp.registrationToken
    email = $Email
  }
  $done = Invoke-Api -Method POST -Path "/api/auth/register/email/verify" -Body @{
    registrationToken = $sp.registrationToken
    email = $Email
    code = "000000"
  }
  return $done.accessToken
}

function Login-User {
  param([string]$Login, [string]$Password)
  $data = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ login = $Login; password = $Password }
  if ($data.status -eq "EMAIL_CODE_REQUIRED") { throw "EMAIL_CODE_REQUIRED for $Login" }
  return $data
}

function Change-Password {
  param([string]$Token, [string]$Current, [string]$New)
  Invoke-Api -Method POST -Path "/api/profile/security/password" -Body @{
    currentPassword = $Current
    newPassword = $New
  } -Token $Token | Out-Null
}

function Assert-OrderFields($order, $ctx) {
  $names = $order | Get-Member -MemberType NoteProperty | Select-Object -ExpandProperty Name
  $need = @("courierDisplayName", "courierPublicId", "restaurantName")
  foreach ($n in $need) {
    if ($names -notcontains $n) {
      Log "Step $ctx : OrderDto missing field '$n' (props: $($names -join ', '))" "FAIL"
      return $false
    }
  }
  Log "Step $ctx : OrderDto has courierDisplayName, courierPublicId, restaurantName" "OK"
  return $true
}

Log "=== Demo run started $(Get-Date -Format o) ==="

# Bootstrap service
$tail = "{0:D4}" -f ((Get-Date).Millisecond + (Get-Random -Maximum 9000))
$svcPhone = "+7937911$tail"
$ownerPhone = "+7937912$tail"
$courier1Phone = "+7937913$tail"
$courier2Phone = "+7937914$tail"
$svcPass = "ServicePass1!"
$channelExternalId = "-1001234567890"

try {
  $svcToken = Register-ServiceOwner -Phone $svcPhone -Password $svcPass -Email "svc-demo-$tail@mvp.test"
  Log "Service owner registered" "OK"
} catch {
  Log "Bootstrap service owner FAILED: $_" "FAIL"
  $report | ForEach-Object { $_ }
  exit 1
}

try {
  $org = Invoke-Api -Method POST -Path "/api/delivery/organizations" -Body @{
    type = "courier_service"
    name = "Demo Courier Service $tail"
  } -Token $svcToken
  $serviceId = $org.id
  Log "Courier service org: $serviceId" "OK"
} catch {
  Log "Create service org FAILED: $_" "FAIL"
  exit 1
}

# Step 1: Object + owner
Log "--- Step 1: Create object with owner ---"
try {
  $objectName = "Andryusha Sushi $tail"
  $rest = Invoke-Api -Method POST -Path "/api/delivery/restaurants" -Body @{
    name = $objectName
    courierServiceId = $serviceId
    owner = @{
      fullName = "Owner Demo"
      phone = $ownerPhone
      email = "owner-$tail@mvp.test"
    }
  } -Token $svcToken
  $restId = $rest.object.id
  $oLogin = $rest.ownerCredentials.login
  $oTemp = $rest.ownerCredentials.temporaryPassword
  Log "Object created: $objectName id=$restId" "OK"
  Log "Owner login=$oLogin tempPwd present=$([bool]$oTemp)" "OK"
  if ($oLogin -notmatch $objectName.Replace(" ", "").Substring(0, [Math]::Min(8, $objectName.Length))) {
    Log "Owner login may not match object name pattern (login=$oLogin)" "WARN"
  }
} catch {
  Log "Step 1 FAILED: $_" "FAIL"
}

# Step 2: Owner login
Log "--- Step 2: Owner login + password change ---"
try {
  $oLoginData = Login-User -Login $oLogin -Password $oTemp
  if (-not $oLoginData.mustChangePassword) { Log "Expected mustChangePassword for owner" "WARN" }
  Change-Password -Token $oLoginData.accessToken -Current $oTemp -New "OwnerPass1!"
  $oAfter = Login-User -Login $oLogin -Password "OwnerPass1!"
  $oToken = $oAfter.accessToken
  $oMe = Invoke-Api -Path "/api/delivery/me" -Token $oToken
  Log "Owner interfaceMode=$($oMe.interfaceMode) activeOrg=$($oMe.activeOrganizationId)" "OK"
  if ($oMe.interfaceMode -ne "restaurant") { Log "Owner expected interfaceMode=restaurant" "WARN" }
} catch {
  Log "Step 2 FAILED: $_" "FAIL"
  $oToken = $null
}

# Step 3: Pickup point
Log "--- Step 3: Owner creates pickup point ---"
try {
  $pp = Invoke-Api -Method POST -Path "/api/delivery/restaurants/$restId/pickup-points" -Body @{
    name = "Main pickup"
    address = "Demo street 1"
    isDefault = $true
  } -Token $oToken
  $pickupId = $pp.id
  $points = Invoke-Api -Path "/api/delivery/restaurants/$restId/pickup-points" -Token $oToken
  $def = $points | Where-Object { $_.isDefault -eq $true }
  Log "Pickup created id=$pickupId default count=$($def.Count)" "OK"
} catch {
  Log "Step 3 FAILED: $_" "FAIL"
  $pickupId = $null
}

# Step 4: Channel
Log "--- Step 4: Service creates channel ---"
try {
  $ch = Invoke-Api -Method POST -Path "/api/delivery/channels" -Body @{
    courierServiceId = $serviceId
    type = "telegram"
    chatType = "channel"
    name = "Demo channel $tail"
    externalId = $channelExternalId
    isActive = $true
  } -Token $svcToken
  $channelId = $ch.id
  Log "Channel created id=$channelId active=$($ch.isActive)" "OK"
} catch {
  Log "Step 4 FAILED: $_" "FAIL"
  $channelId = $null
}

# Step 5: Bind channel
Log "--- Step 5: Bind object to channel ---"
try {
  Invoke-Api -Method PUT -Path "/api/delivery/restaurants/$restId/channels" -Body @{
    channelIds = @($channelId)
  } -Token $svcToken | Out-Null
  $bound = Invoke-Api -Path "/api/delivery/restaurants/$restId/channels" -Token $svcToken
  Log "Bound channels: $($bound.channels.Count)" "OK"
} catch {
  Log "Step 5 FAILED: $_" "FAIL"
}

# Step 6: Courier 1
Log "--- Step 6: Create courier 1 ---"
try {
  $cr1 = Invoke-Api -Method POST -Path "/api/delivery/couriers" -Body @{
    courierServiceId = $serviceId
    fullName = "Ivan Courier"
    phone = $courier1Phone
  } -Token $svcToken
  $c1Login = $cr1.credentials.login
  $c1Temp = $cr1.credentials.temporaryPassword
  $c1PublicId = $cr1.courier.publicId
  Log "Courier1 login=$c1Login publicId=$c1PublicId" "OK"
  if ($c1Login -match '^courier_(\d+)$') {
    $num = [long]$Matches[1]
    if ($num -ne $c1PublicId) { Log "Login number $num != publicId $c1PublicId" "FAIL" }
    else { Log "Login matches publicId" "OK" }
  } else { Log "Login not courier_N format" "WARN" }
  $c1MemberId = $cr1.courier.memberId
} catch {
  Log "Step 6 FAILED: $_" "FAIL"
}

# Courier 2 for step 12
Log "--- Step 6b: Create courier 2 ---"
try {
  $cr2 = Invoke-Api -Method POST -Path "/api/delivery/couriers" -Body @{
    courierServiceId = $serviceId
    fullName = "Petr Courier"
    phone = $courier2Phone
  } -Token $svcToken
  $c2Login = $cr2.credentials.login
  $c2Temp = $cr2.credentials.temporaryPassword
  Log "Courier2 login=$c2Login" "OK"
} catch {
  Log "Courier2 create FAILED: $_" "FAIL"
}

# Step 7: Courier 1 login
Log "--- Step 7: Courier 1 login ---"
try {
  $c1LoginData = Login-User -Login $c1Login -Password $c1Temp
  Change-Password -Token $c1LoginData.accessToken -Current $c1Temp -New "CourierPass1!"
  $c1After = Login-User -Login $c1Login -Password "CourierPass1!"
  $c1Token = $c1After.accessToken
  $c1Me = Invoke-Api -Path "/api/delivery/me" -Token $c1Token
  Log "Courier1 interfaceMode=$($c1Me.interfaceMode)" "OK"
} catch {
  Log "Step 7 FAILED: $_" "FAIL"
  $c1Token = $null
}

# Step 8: Create order
Log "--- Step 8: Object creates order ---"
try {
  $deliveryTime = (Get-Date).AddHours(2).ToUniversalTime().ToString("o")
  $createRes = Invoke-Api -Method POST -Path "/api/delivery/orders" -Body @{
    restaurantId = $restId
    pickupPointId = $pickupId
    deliveryAddress = "Client street 10"
    deliveryTime = $deliveryTime
    price = 450
    customerPhone = "+79001234567"
    comment = "Demo order"
  } -Token $oToken
  $orderId = $createRes.order.id
  $warnings = $createRes.warnings
  Log "Order created id=$orderId publicNumber=$($createRes.order.publicNumber) warnings=$($warnings -join ',')" "OK"
  Assert-OrderFields $createRes.order "8-create" | Out-Null
  if ($createRes.order.restaurantName) {
    Log "restaurantName on create=$($createRes.order.restaurantName)" "OK"
  } else { Log "restaurantName empty on create (may be ok before accept)" "WARN" }
} catch {
  Log "Step 8 FAILED: $_" "FAIL"
  $orderId = $null
}

# Step 9: Publication
Log "--- Step 9: Channel publication ---"
if ($warnings -contains "no_active_channels") {
  Log "BLOCKER: no_active_channels - Telegram/MAX not published" "FAIL"
} else {
  $pub = $createRes.order.publishedAt
  if ($pub) { Log "publishedAt=$pub (API says published)" "OK" }
  else { Log "publishedAt null - check TG_TOKEN / real chat id" "WARN" }
  Log "Manual check: message in Telegram channel with button (not verifiable in script)" "INFO"
}

# Step 10: Free orders list
Log "--- Step 10: Courier sees free order ---"
try {
  $free = Invoke-Api -Path "/api/delivery/orders?scope=courier" -Token $c1Token
  $found = $free | Where-Object { $_.id -eq $orderId }
  if ($found) {
    Assert-OrderFields $found "10-list" | Out-Null
    Log "Free order restaurantName=$($found.restaurantName) price=$($found.price)" "OK"
  } else {
    Log "Order not in courier free list (count=$($free.Count))" "FAIL"
  }
} catch {
  Log "Step 10 FAILED: $_" "FAIL"
}

# Step 11: Accept
Log "--- Step 11: Courier accepts order ---"
try {
  $accepted = Invoke-Api -Method POST -Path "/api/delivery/orders/$orderId/accept" -Token $c1Token
  Log "Accepted status=$($accepted.status) courierDisplayName=$($accepted.courierDisplayName) courierPublicId=$($accepted.courierPublicId)" "OK"
  Assert-OrderFields $accepted "11-accept" | Out-Null

  $ownerOrder = Invoke-Api -Path "/api/delivery/orders/$orderId" -Token $oToken
  Log "Owner view courier: display=$($ownerOrder.courierDisplayName) publicId=$($ownerOrder.courierPublicId)" "OK"

  $svcOrder = Invoke-Api -Path "/api/delivery/orders/$orderId" -Token $svcToken
  Log "Service view courier: display=$($svcOrder.courierDisplayName) publicId=$($svcOrder.courierPublicId)" "OK"

  $freeAfter = Invoke-Api -Path "/api/delivery/orders?scope=courier" -Token $c1Token
  $stillFree = $freeAfter | Where-Object { $_.id -eq $orderId }
  if ($stillFree) { Log "Order still in free list after accept" "FAIL" }
  else { Log "Order removed from free list" "OK" }

  $myActive = Invoke-Api -Path '/api/delivery/orders?scope=courier&status=courier_heading_to_pickup' -Token $c1Token
  $inMy = $myActive | Where-Object { $_.id -eq $orderId }
  if ($inMy) { Log "Order in my active (heading_to_pickup)" "OK" }
  else { Log "Order not in my active list" "WARN" }
} catch {
  Log "Step 11 FAILED: $_" "FAIL"
}

# Step 12: Second courier
Log "--- Step 12: Second courier cannot accept ---"
try {
  $c2LoginData = Login-User -Login $c2Login -Password $c2Temp
  Change-Password -Token $c2LoginData.accessToken -Current $c2Temp -New "CourierPass2!"
  $c2After = Login-User -Login $c2Login -Password "CourierPass2!"
  $c2Token = $c2After.accessToken
  try {
    Invoke-Api -Method POST -Path "/api/delivery/orders/$orderId/accept" -Token $c2Token
    Log "Second courier accepted - should not happen" "FAIL"
  } catch {
    if ($_ -match "409|already_taken|Conflict|order_already") {
      Log "Second courier blocked with conflict (expected)" "OK"
    } else {
      Log "Second courier accept error: $_" "WARN"
    }
  }
} catch {
  Log "Step 12 setup FAILED: $_" "FAIL"
}

# Step 13: Complete order
Log "--- Step 13: Courier completes order ---"
try {
  $c1ProfileBefore = Invoke-Api -Path "/api/delivery/couriers/$c1MemberId" -Token $svcToken
  $completedBefore = $c1ProfileBefore.completedOrdersCount

  $s1 = Invoke-Api -Method POST -Path "/api/delivery/orders/$orderId/status" -Body @{
    status = "courier_delivering"
  } -Token $c1Token
  Log "Status after start delivery: $($s1.status)" "OK"

  $s2 = Invoke-Api -Method POST -Path "/api/delivery/orders/$orderId/status" -Body @{
    status = "completed"
  } -Token $c1Token
  Log "Final status: $($s2.status)" "OK"

  $ownerFinal = Invoke-Api -Path "/api/delivery/orders/$orderId" -Token $oToken
  $svcFinal = Invoke-Api -Path "/api/delivery/orders/$orderId" -Token $svcToken
  if ($ownerFinal.status -eq "completed") { Log "Owner sees completed" "OK" } else { Log "Owner status=$($ownerFinal.status)" "FAIL" }
  if ($svcFinal.status -eq "completed") { Log "Service sees completed" "OK" } else { Log "Service status=$($svcFinal.status)" "FAIL" }

  $completedList = Invoke-Api -Path '/api/delivery/orders?scope=courier&status=completed' -Token $c1Token
  $inHistory = $completedList | Where-Object { $_.id -eq $orderId }
  if ($inHistory) { Log "Order in courier completed history" "OK" } else { Log "Not in completed history" "WARN" }

  $c1ProfileAfter = Invoke-Api -Path "/api/delivery/couriers/$c1MemberId" -Token $svcToken
  if ($c1ProfileAfter.completedOrdersCount -gt $completedBefore) {
    Log "completedOrdersCount $($completedBefore) -> $($c1ProfileAfter.completedOrdersCount)" "OK"
  } else {
    Log "completedOrdersCount did not increase ($completedBefore)" "WARN"
  }
} catch {
  Log "Step 13 FAILED: $_" "FAIL"
}

# Step 14: Block courier
Log "--- Step 14: Block / unblock courier ---"
try {
  Invoke-Api -Method PATCH -Path "/api/delivery/couriers/$c1MemberId" -Body @{ status = "blocked" } -Token $svcToken | Out-Null
  $c1MeBlocked = Invoke-Api -Path "/api/delivery/me" -Token $c1Token
  Log "Courier me after block: interfaceMode=$($c1MeBlocked.interfaceMode) memberships=$($c1MeBlocked.memberships.Count)" "INFO"
  try {
    $freeBlocked = Invoke-Api -Path "/api/delivery/orders?scope=courier" -Token $c1Token
    Log "Blocked courier still got free orders list (count=$($freeBlocked.Count)) - API should 403" "FAIL"
  } catch {
    if ($_ -match "403|forbidden|rights") { Log "Blocked courier API orders forbidden" "OK" }
    else { Log "Blocked courier list error: $_" "WARN" }
  }

  Invoke-Api -Method PATCH -Path "/api/delivery/couriers/$c1MemberId" -Body @{ status = "active" } -Token $svcToken | Out-Null
  $freeUnblock = Invoke-Api -Path "/api/delivery/orders?scope=courier" -Token $c1Token
  Log "After unblock courier free list count=$($freeUnblock.Count); UI BlockedCourierScreen needs manual check" "INFO"
} catch {
  Log "Step 14 FAILED: $_" "FAIL"
}

Log "=== Demo run finished ==="
$report | ForEach-Object { $_ }
