/**
 * Case 5: manager B sees courier in list (not only direct card link).
 */
const BASE = 'http://localhost:8080'
const SERVICE_ID = '759ee600-1004-4a7b-8d82-37f11f977c75'
const INTERNAL_KEY = 'dev-monolith-internal-key'
const MANAGER_B_ID = 1000000000003
const EXPECTED_MEMBER_ID = 'bb6af131-b9f9-49ac-9af7-88eb6cdba865'

async function getToken(userId) {
  const creds = await fetch(`${BASE}/api/internal/monolith/users/${userId}/reset-web-credentials`, {
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

async function main() {
  const tokenB = await getToken(MANAGER_B_ID)
  const list = await fetch(`${BASE}/api/delivery/couriers?courierServiceId=${SERVICE_ID}`, {
    headers: { Authorization: `Bearer ${tokenB}` },
  }).then((r) => r.json())

  const inList = Array.isArray(list) && list.some((c) => c.memberId === EXPECTED_MEMBER_ID)
  const card = await fetch(`${BASE}/api/delivery/couriers/${EXPECTED_MEMBER_ID}`, {
    headers: { Authorization: `Bearer ${tokenB}` },
  })
  const cardOk = card.status === 200

  console.log(`list count=${list?.length ?? 0}`)
  console.log(`${inList ? 'PASS' : 'FAIL'} manager B sees courier in list`)
  console.log(`${cardOk ? 'PASS' : 'FAIL'} manager B opens courier card`)
  if (!inList || !cardOk) process.exit(1)
  console.log('Case 5: ALL PASS')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
