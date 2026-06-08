import { API_URL, USE_NGROK_HEADER } from '../config'
import { getClientChannelHeaderValue } from './clientChannel'

const authBase = `${API_URL}/auth/messenger`

function baseHeaders() {
  const h = {
    'Content-Type': 'application/json',
    'X-Client-Channel': getClientChannelHeaderValue(),
  }
  if (USE_NGROK_HEADER) h['ngrok-skip-browser-warning'] = '1'
  return h
}

async function parseResponse(res) {
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    const message = data.message || data.error || `Ошибка ${res.status}`
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return data
}

/**
 * POST /api/auth/messenger/login
 * @returns {Promise<{ token?: string, accessToken?: string, needLink?: boolean }>}
 */
export async function messengerLogin({ platform, messengerUserId, initData }) {
  const res = await fetch(`${authBase}/login`, {
    method: 'POST',
    headers: baseHeaders(),
    body: JSON.stringify({ platform, messengerUserId, initData }),
    credentials: 'include',
  })
  return parseResponse(res)
}

/**
 * POST /api/auth/messenger/link
 * @returns {Promise<{ token?: string, accessToken?: string, userProfile?: object }>}
 */
export async function messengerLink({ login, password, platform, messengerUserId, messengerUsername, initData }) {
  const res = await fetch(`${authBase}/link`, {
    method: 'POST',
    headers: baseHeaders(),
    body: JSON.stringify({
      login,
      password,
      platform,
      messengerUserId,
      messengerUsername,
      initData,
    }),
    credentials: 'include',
  })
  return parseResponse(res)
}
