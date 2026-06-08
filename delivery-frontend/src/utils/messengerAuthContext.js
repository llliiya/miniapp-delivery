import { getPlatform } from './platform.js'
import { parseTelegramUserFromInitData } from './messengerIdentity.js'

/**
 * @returns {{ platform: 'telegram'|'max', messengerUserId: string, messengerUsername: string|null, initData: string|null } | null}
 */
export function readMessengerAuthContext() {
  const initData = window.Telegram?.WebApp?.initData
  if (typeof initData === 'string' && initData.length > 0) {
    const user = window.Telegram?.WebApp?.initDataUnsafe?.user
    if (user?.id) {
      const username = user.username ? String(user.username).trim().replace(/^@/, '') : null
      return {
        platform: 'telegram',
        messengerUserId: String(user.id),
        messengerUsername: username,
        initData,
      }
    }
    const parsed = parseTelegramUserFromInitData(initData)
    if (parsed) {
      return {
        platform: 'telegram',
        messengerUserId: parsed.id,
        messengerUsername: parsed.username,
        initData,
      }
    }
  }

  const platform = getPlatform()
  if (platform === 'max') {
    const maxInitData = window.WebApp?.initData || window.Telegram?.WebApp?.initData || null
    if (typeof maxInitData !== 'string' || maxInitData.length === 0) return null
    const user = window.WebApp?.initDataUnsafe?.user || window.Telegram?.WebApp?.initDataUnsafe?.user || {}
    if (!user?.id) return null
    const username = user.username ? String(user.username).trim().replace(/^@/, '') : null
    return {
      platform: 'max',
      messengerUserId: String(user.id),
      messengerUsername: username,
      initData: maxInitData,
    }
  }

  return null
}
