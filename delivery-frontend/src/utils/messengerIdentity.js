import { resolveClientPlatform } from './clientPlatform.js'
import { getPlatform, isTelegramAuthContext } from './platform.js'
import { getMaxUserIdWithSource, waitForMaxUserIdWithSource } from './maxEnv.js'

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

export function parseTelegramUserFromInitData(initData) {
  if (!initData || typeof initData !== 'string') return null
  try {
    const params = new URLSearchParams(initData)
    const userJson = params.get('user')
    if (!userJson) return null
    const user = JSON.parse(userJson)
    if (!user?.id) return null
    const username = user.username ? String(user.username).trim().replace(/^@/, '') : null
    return {
      id: String(user.id),
      username: username || null,
    }
  } catch {
    return null
  }
}

function readTelegramIdentity() {
  const user = window.Telegram?.WebApp?.initDataUnsafe?.user
  if (user?.id) {
    const username = user.username ? String(user.username).trim().replace(/^@/, '') : null
    return {
      provider: 'TELEGRAM',
      externalId: String(user.id),
      username: username || null,
    }
  }
  const parsed = parseTelegramUserFromInitData(window.Telegram?.WebApp?.initData)
  if (parsed) {
    return {
      provider: 'TELEGRAM',
      externalId: parsed.id,
      username: parsed.username,
    }
  }
  return null
}

function readMaxIdentity() {
  const { userId } = getMaxUserIdWithSource()
  if (!userId) return null
  const user = window.WebApp?.initDataUnsafe?.user || window.Telegram?.WebApp?.initDataUnsafe?.user || {}
  const username = user.username ? String(user.username).trim().replace(/^@/, '') : null
  return {
    provider: 'MAX',
    externalId: String(userId),
    username: username || null,
  }
}

/**
 * @returns {{ provider: 'TELEGRAM'|'MAX', externalId: string, username: string|null } | null}
 */
export function readMessengerIdentity() {
  const telegram = readTelegramIdentity()
  if (telegram) return telegram
  if (getPlatform() === 'max') {
    return readMaxIdentity()
  }
  return null
}

/**
 * Ожидает готовности bridge и возвращает identity (для регистрации и messenger auth).
 */
export async function resolveMessengerIdentity(options = {}) {
  const timeoutMs = options.timeoutMs ?? 8000
  const pollMs = options.pollMs ?? 100

  await resolveClientPlatform()

  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const telegram = readTelegramIdentity()
    if (telegram) return telegram
    if (getPlatform() === 'max') {
      const maxIdentity = readMaxIdentity()
      if (maxIdentity) return maxIdentity
    }
    await sleep(pollMs)
  }

  const telegram = readTelegramIdentity()
  if (telegram) return telegram
  if (getPlatform() === 'max') {
    await waitForMaxUserIdWithSource(Math.min(timeoutMs, 2000), pollMs)
    return readMaxIdentity()
  }
  return null
}

export function isMessengerContext() {
  if (isTelegramAuthContext()) return true
  return getPlatform() === 'telegram' || getPlatform() === 'max'
}
