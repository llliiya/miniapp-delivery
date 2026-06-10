import { API_URL, DELIVERY_API_URL } from '../config.js'
import { getToken, setToken, clearToken } from './tokenStorage.js'

async function parseJson(res) {
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

function friendlyApiErrorMessage(status, data, fallback = 'Не удалось выполнить действие') {
  const fromApi =
    (typeof data?.error === 'string' && data.error.trim()) ||
    (typeof data?.detail === 'string' && data.detail.trim()) ||
    (typeof data?.message === 'string' && data.message.trim()) ||
    ''
  if (fromApi && !/^HTTP\s\d+/i.test(fromApi)) {
    return fromApi
  }
  if (status === 403) return 'Недостаточно прав'
  if (status === 409) return 'Не удалось выполнить действие'
  return fallback
}

async function fetchWithAuth(url, method, body) {
  const buildInit = (token) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    return {
      method,
      headers,
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }
  }

  let res = await fetch(url, buildInit(getToken()))
  if (res.status === 401) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      res = await fetch(url, buildInit(refreshed))
    }
  }
  return res
}

export async function accountApi(path, method = 'GET', body) {
  const url = `${API_URL}${path.startsWith('/') ? path : `/${path}`}`
  const res = await fetchWithAuth(url, method, body)
  if (!res.ok) {
    let data = null
    try {
      data = await parseJson(res)
    } catch {
      data = null
    }
    const message = friendlyApiErrorMessage(res.status, data)
    const err = new Error(message)
    err.status = res.status
    err.error = data?.error
    throw err
  }
  return parseJson(res)
}

export async function deliveryApi(path, method = 'GET', body) {
  const url = `${DELIVERY_API_URL}${path.startsWith('/') ? path : `/${path}`}`
  const res = await fetchWithAuth(url, method, body)
  if (!res.ok) {
    let data = null
    try {
      data = await parseJson(res)
    } catch {
      data = null
    }
    const message = friendlyApiErrorMessage(res.status, data)
    const err = new Error(message)
    err.status = res.status
    err.error = data?.error
    throw err
  }
  return parseJson(res)
}

export async function refreshAccessToken() {
  const res = await fetch(`${API_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
  if (!res.ok) {
    return null
  }
  const data = await parseJson(res)
  const token = data?.accessToken || data?.token
  if (token) {
    setToken(token)
    return token
  }
  return null
}

export async function fetchAuthMe() {
  return accountApi('/auth/me')
}

export async function fetchDeliveryMe() {
  return deliveryApi('/me')
}

export { setToken, clearToken, getToken }
