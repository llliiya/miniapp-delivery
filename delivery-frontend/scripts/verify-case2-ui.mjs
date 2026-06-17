/**
 * UI contract test for Case 2: duplicate courier add flow.
 * Simulates deliveryApi error -> ServiceCouriersPage handling.
 */
import { mapDeliveryApiError } from '../src/utils/mapApiError.js'

const BASE = 'http://localhost:8080'
const SERVICE_ID = '759ee600-1004-4a7b-8d82-37f11f977c75'
const INTERNAL_KEY = 'dev-monolith-internal-key'
const OWNER_ID = 1000000000000
const EXISTING = {
  fullName: 'Petrov Petr',
  phone: '+78945645645',
  email: '11@mail.ru',
}

async function getOwnerToken() {
  const creds = await fetch(`${BASE}/api/internal/monolith/users/${OWNER_ID}/reset-web-credentials`, {
    method: 'POST',
    headers: { 'X-Internal-Key': INTERNAL_KEY },
  }).then((r) => r.json())
  const login = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ login: creds.login, password: creds.temporaryPassword }),
  }).then((r) => r.json())
  const verify = await fetch(`${BASE}/api/auth/login/verify-email-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challengeId: login.challengeId, code: '000000' }),
  }).then((r) => r.json())
  return verify.accessToken
}

async function addCourier(token, body) {
  const res = await fetch(`${BASE}/api/delivery/couriers`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  const data = await res.json().catch(() => ({}))
  if (res.ok) return { ok: true, status: res.status, data }
  const message =
    (typeof data.message === 'string' && data.message.trim()) ||
    (typeof data.detail === 'string' && data.detail.trim()) ||
    (typeof data.error === 'string' && data.error.trim()) ||
    'error'
  const err = new Error(message)
  err.status = res.status
  err.error = data.error
  err.existingCourierId = data.existingCourierId
  err.existingCourier = data.existingCourier
  throw err
}

function simulateUi(err) {
  const message = mapDeliveryApiError(err, 'Не удалось добавить курьера')
  const existingId = err?.existingCourierId || err?.existingCourier?.memberId
  const showButton = err?.error === 'courier_already_in_service' && Boolean(existingId)
  const href = showButton ? `/service/couriers/${existingId}` : null
  return { message, showButton, href, existingId }
}

async function main() {
  const token = await getOwnerToken()
  let err
  try {
    await addCourier(token, {
      courierServiceId: SERVICE_ID,
      ...EXISTING,
    })
    console.error('FAIL: expected 409 conflict')
    process.exit(1)
  } catch (e) {
    err = e
  }

  const ui = simulateUi(err)
  const routeRes = await fetch(`http://localhost:5173${ui.href}`)
  const bundleHasButton = await fetch('http://localhost:5173/')
    .then((r) => r.text())
    .then((html) => {
      const m = html.match(/src="(\/assets\/[^"]+\.js)"/)
      if (!m) return false
      return fetch(`http://localhost:5173${m[1]}`)
        .then((r) => r.text())
        .then((js) => js.includes('Открыть карточку курьера'))
    })

  const checks = [
    ['status 409', err.status === 409],
    ['error code', err.error === 'courier_already_in_service'],
    ['message human', ui.message.includes('Курьер уже есть в вашей службе')],
    ['button shown', ui.showButton === true],
    ['href memberId', ui.href === `/service/couriers/${ui.existingId}`],
    ['route 200', routeRes.status === 200],
    ['bundle has button text', bundleHasButton],
  ]

  let failed = false
  for (const [name, ok] of checks) {
    console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`)
    if (!ok) failed = true
  }
  if (failed) process.exit(1)
  console.log('Case 2 UI contract: ALL PASS')
  console.log(`link=${ui.href} memberId=${ui.existingId}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
