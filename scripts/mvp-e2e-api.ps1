# MVP E2E via gateway (http://localhost:8080). Requires rebuilt dev stack.
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

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
  $params = @{ Method = $Method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 60 }
  if ($null -ne $Body) {
    $params["Body"] = ($Body | ConvertTo-Json -Compress -Depth 6)
  }
  try {
    if ($null -ne $Body) {
      return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body ($Body | ConvertTo-Json -Compress -Depth 6) -TimeoutSec 60
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -TimeoutSec 60
  } catch {
    throw "HTTP $Path : $($_.Exception.Message)"
  }
}

function Register-ServiceOwner {
  param([string]$Phone, [string]$Password, [string]$Email)
  $req = Invoke-Api -Method POST -Path "/api/auth/register/phone/request" -Body @{ phone = $Phone }
  $cid = $req.challengeId
  Invoke-Api -Method POST -Path "/api/auth/register/phone/verify" -Body @{ challengeId = $cid; pin = "1234" } | Out-Null
  $sp = Invoke-Api -Method POST -Path "/api/auth/register/set-password" -Body @{ phone = $Phone; password = $Password }
  $regToken = $sp.registrationToken
  $er = Invoke-Api -Method POST -Path "/api/auth/register/email/request" -Body @{ registrationToken = $regToken; email = $Email }
  $ecid = $er.challengeId
  $done = Invoke-Api -Method POST -Path "/api/auth/register/email/verify" -Body @{ registrationToken = $regToken; email = $Email; code = "000000" }
  return $done.accessToken
}

function Login-User {
  param([string]$Login = $null, [string]$Phone = $null, [string]$Password)
  $body = @{ password = $Password }
  if ($Login) { $body["login"] = $Login } else { $body["phone"] = $Phone }
  $data = Invoke-Api -Method POST -Path "/api/auth/login" -Body $body
  if ($data.status -eq "EMAIL_CODE_REQUIRED") { throw "Login requires email 2FA" }
  return $data
}

function Change-Password {
  param([string]$Token, [string]$Current, [string]$New)
  Invoke-Api -Method POST -Path "/api/profile/security/password" -Body @{
    currentPassword = $Current
    newPassword = $New
  } -Token $Token | Out-Null
}

Write-Host "=== Bootstrap service owner ==="
$tail = "{0:D4}" -f ((Get-Date).Millisecond + (Get-Random -Maximum 9000))
$svcPhone = "+7937901$tail"
$courierPhone = "+7937902$tail"
$ownerPhone = "+7937903$tail"
$mgrPhone = "+7937904$tail"
$svcPass = "ServicePass1!"
$svcToken = Register-ServiceOwner -Phone $svcPhone -Password $svcPass -Email "svc-owner@mvp.test"
Write-Host "Service owner token OK"

Write-Host "=== Create courier service org ==="
$org = Invoke-Api -Method POST -Path "/api/delivery/organizations" -Body @{
  type = "courier_service"
  name = "MVP Service"
} -Token $svcToken
$serviceId = $org.id
Write-Host "Service id: $serviceId"

Write-Host "=== Scenario 1: Courier ==="
$cr = Invoke-Api -Method POST -Path "/api/delivery/couriers" -Body @{
  courierServiceId = $serviceId
  fullName = "Ivanov Ivan Courier"
  phone = $courierPhone
  email = "courier@mvp.test"
} -Token $svcToken
$cLogin = $cr.credentials.login
$cTemp = $cr.credentials.temporaryPassword
Write-Host "Courier credentials: $cLogin / $cTemp"

$cLoginData = Login-User -Login $cLogin -Password $cTemp
if (-not $cLoginData.mustChangePassword) { throw "Expected mustChangePassword=true" }
$cToken = $cLoginData.accessToken
$newCourierPass = "CourierPass1!"
Change-Password -Token $cToken -Current $cTemp -New $newCourierPass
$cAfter = Login-User -Login $cLogin -Password $newCourierPass
if ($cAfter.mustChangePassword) { throw "mustChangePassword still true after change" }
$cMe = Invoke-Api -Path "/api/delivery/me" -Token $cAfter.accessToken
Write-Host "Courier me: interfaceMode=$($cMe.interfaceMode) deliveryRole=$($cMe.deliveryRole)"

Write-Host "=== Scenario 2: Restaurant + owner ==="
$rest = Invoke-Api -Method POST -Path "/api/delivery/restaurants" -Body @{
  name = "MVP Cafe"
  courierServiceId = $serviceId
  owner = @{
    fullName = "Petrov Owner"
    phone = $ownerPhone
    email = "owner@mvp.test"
  }
} -Token $svcToken
$oLogin = $rest.ownerCredentials.login
$oTemp = $rest.ownerCredentials.temporaryPassword
$restId = $rest.object.id
Write-Host "Owner credentials: $oLogin / $oTemp restaurant=$restId"

$oLoginData = Login-User -Login $oLogin -Password $oTemp
$oToken = $oLoginData.accessToken
Change-Password -Token $oToken -Current $oTemp -New "OwnerPass1!"
$oAfter = Login-User -Login $oLogin -Password "OwnerPass1!"
$oMe = Invoke-Api -Path "/api/delivery/me" -Token $oAfter.accessToken
Write-Host "Owner me: interfaceMode=$($oMe.interfaceMode) activeOrg=$($oMe.activeOrganizationId)"

Write-Host "=== Scenario 3: Restaurant manager ==="
$mem = Invoke-Api -Method POST -Path "/api/delivery/organizations/$restId/members" -Body @{
  role = "manager"
  fullName = "Sidorov Manager"
  phone = $mgrPhone
  email = "manager@mvp.test"
} -Token $svcToken
$mLogin = $mem.credentials.login
$mTemp = $mem.credentials.temporaryPassword
Write-Host "Manager credentials: $mLogin / $mTemp"

$mLoginData = Login-User -Login $mLogin -Password $mTemp
$mToken = $mLoginData.accessToken
Change-Password -Token $mToken -Current $mTemp -New "ManagerPass1!"
$mAfter = Login-User -Login $mLogin -Password "ManagerPass1!"
$mMe = Invoke-Api -Path "/api/delivery/me" -Token $mAfter.accessToken
Write-Host "Manager me: interfaceMode=$($mMe.interfaceMode)"

Write-Host "=== Priority 2: self-reg block (delivery channel) ==="
try {
  Invoke-Api -Method POST -Path "/api/auth/register/phone/request" -Body @{ phone = "+79379007099" } -ExtraHeaders @{ "X-Client-Channel" = "delivery" }
  Write-Host "WARN: register/phone/request with delivery channel was NOT blocked"
} catch {
  if ($_.Exception.Message -match "403|FORBIDDEN|administrator") {
    Write-Host "OK: register blocked via delivery channel"
  } else {
    Write-Host "register error: $($_.Exception.Message)"
  }
}

try {
  $intent = Invoke-Api -Method POST -Path "/api/auth/phone/intent" -Body @{ identifier = "+79379007098" } -ExtraHeaders @{ "X-Client-Channel" = "delivery" }
  Write-Host "phone/intent delivery: $($intent | ConvertTo-Json -Compress)"
} catch {
  Write-Host "phone/intent delivery blocked: $($_.Exception.Message)"
}

Write-Host "=== ALL E2E API CHECKS DONE ==="
