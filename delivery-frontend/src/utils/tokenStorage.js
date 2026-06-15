/**
 * Хранение JWT (тот же ключ `token`, что и в miniapp-frontend).
 */

const KEY = 'token'
let memoryToken = null

export function getToken() {
  try {
    const t = localStorage.getItem(KEY)
    if (t) return t
  } catch (_) {}

  try {
    const t = sessionStorage.getItem(KEY)
    if (t) return t
  } catch (_) {}

  return memoryToken
}

export function setToken(token) {
  if (!token) {
    removeToken()
    return
  }

  try {
    localStorage.setItem(KEY, token)
    memoryToken = null
    return
  } catch (_) {}

  try {
    sessionStorage.setItem(KEY, token)
    memoryToken = null
    return
  } catch (_) {}

  memoryToken = token
}

export function removeToken() {
  memoryToken = null
  try {
    localStorage.removeItem(KEY)
  } catch (_) {}
  try {
    sessionStorage.removeItem(KEY)
  } catch (_) {}
}

export function clearAuthClientStorage() {
  removeToken()
}

/** @param {string | null | undefined} token @param {number} [skewSec] */
export function isAccessTokenExpired(token, skewSec = 60) {
  if (!token) return true
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    if (!payload?.exp) return false
    return payload.exp <= Math.floor(Date.now() / 1000) + skewSec
  } catch {
    return true
  }
}
