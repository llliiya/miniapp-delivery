import { messengerLogin } from '../api/messengerAuthService.js'
import { getToken, setToken } from '../utils/tokenStorage.js'
import { resolveClientPlatform } from '../utils/clientPlatform.js'
import { getPlatform, isTelegramAuthContext } from '../utils/platform.js'
import { getMaxUserIdWithSource, waitForMaxUserIdWithSource } from '../utils/maxEnv.js'
import { readMessengerAuthContext } from '../utils/messengerAuthContext.js'
import { resolveMessengerIdentity } from '../utils/messengerIdentity.js'
import { setMessengerNeedLink } from '../pages/auth/messengerLinkSession.js'

/**
 * Тихий вход в Telegram / MAX через единый messenger API.
 * @returns {Promise<boolean>} true, если получен access token
 */
export async function tryMessengerAuth() {
  if (getToken()) {
    return true
  }

  await resolveClientPlatform()
  const platform = getPlatform()

  if (platform === 'max') {
    let ctx = readMessengerAuthContext()
    if (!ctx?.messengerUserId) {
      const waited = await waitForMaxUserIdWithSource(8000, 100)
      if (waited.userId) {
        ctx = readMessengerAuthContext()
      }
    }
    if (!ctx?.messengerUserId) {
      const identity = await resolveMessengerIdentity()
      if (identity) {
        ctx = readMessengerAuthContext()
      }
    }
    if (!ctx?.messengerUserId) {
      return false
    }
    return runMessengerLogin(ctx)
  }

  if (isTelegramAuthContext()) {
    let ctx = readMessengerAuthContext()
    if (!ctx?.messengerUserId) {
      const deadline = Date.now() + 8000
      while (!ctx?.messengerUserId && Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 100))
        ctx = readMessengerAuthContext()
      }
    }
    if (!ctx?.messengerUserId) {
      return false
    }
    return runMessengerLogin(ctx)
  }

  return false
}

async function runMessengerLogin(ctx) {
  try {
    const data = await messengerLogin({
      platform: ctx.platform,
      messengerUserId: ctx.messengerUserId,
      initData: ctx.initData,
    })
    if (data?.needLink === true) {
      setMessengerNeedLink()
      return false
    }
    const token = data?.accessToken ?? data?.token
    if (token) {
      setToken(token)
      window.dispatchEvent(new Event('reauth'))
      return true
    }
  } catch (err) {
    console.warn('[delivery auth] messenger login failed:', err?.message ?? err)
  }
  return false
}
