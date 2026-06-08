const STORAGE_KEY = 'delivery_pending_order_id'
const STORAGE_KIND_KEY = 'delivery_pending_order_kind'
export const MY_ORDERS_START_PARAM = 'delivery_my_orders'

function decodeStartParam(value) {
  if (value == null || value === '') return null
  try {
    return decodeURIComponent(String(value))
  } catch {
    return String(value)
  }
}

function collectUrlSearchParams() {
  const result = []
  try {
    result.push(new URLSearchParams(window.location.search || ''))
  } catch {
    // ignore
  }
  const hash = window.location.hash || ''
  if (!hash) return result
  const withoutHash = hash.startsWith('#') ? hash.slice(1) : hash
  const queryPart = withoutHash.includes('?') ? withoutHash.split('?').pop() : withoutHash
  if (!queryPart || !queryPart.includes('=')) return result
  try {
    result.push(new URLSearchParams(queryPart))
  } catch {
    // ignore
  }
  return result
}

export function readRawStartParamFromEnvironment() {
  if (typeof window === 'undefined') return null

  const tg = window.Telegram?.WebApp
  const fromTg =
    tg?.initDataUnsafe?.start_param
    ?? (typeof tg?.getStartParam === 'function' ? tg.getStartParam() : null)
    ?? tg?.startParam
  if (fromTg) return decodeStartParam(fromTg)

  const fromMax = window.WebApp?.initDataUnsafe?.start_param
  if (fromMax) return decodeStartParam(fromMax)

  for (const params of collectUrlSearchParams()) {
    for (const key of ['start_param', 'startapp', 'tgWebAppStartParam', 'WebAppStartParam']) {
      const value = params.get(key)
      if (value) return decodeStartParam(value)
    }
  }

  return null
}

export function readOrderIdFromStartParam(value) {
  if (typeof value !== 'string') return null
  if (value.startsWith('delivery_order_')) {
    return value.replace('delivery_order_', '')
  }
  if (value.startsWith('delivery_my_order_')) {
    return value.replace('delivery_my_order_', '')
  }
  return null
}

export function isMyOrdersStartParam(value) {
  return value === MY_ORDERS_START_PARAM
}

export function isMyOrderStartParam(value) {
  return typeof value === 'string' && value.startsWith('delivery_my_order_')
}

export function readStartParamFromEnvironment() {
  return readOrderIdFromStartParam(readRawStartParamFromEnvironment())
}

export function resolveCourierOrderDeeplinkPath(orderId, rawStartParam = readRawStartParamFromEnvironment()) {
  if (!orderId) return null
  if (isMyOrderStartParam(rawStartParam)) {
    return `/courier/my-orders/${orderId}`
  }
  return `/courier/orders/${orderId}`
}

export function capturePendingOrderDeeplink() {
  const raw = readRawStartParamFromEnvironment()
  const orderId = readOrderIdFromStartParam(raw)
  if (orderId) {
    sessionStorage.setItem(STORAGE_KEY, orderId)
    sessionStorage.setItem(STORAGE_KIND_KEY, isMyOrderStartParam(raw) ? 'my' : 'free')
  }
  return orderId
}

export function consumePendingOrderDeeplink() {
  const orderId = sessionStorage.getItem(STORAGE_KEY)
  const kind = sessionStorage.getItem(STORAGE_KIND_KEY)
  if (orderId) {
    sessionStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KIND_KEY)
    return { orderId, kind }
  }
  return null
}

export function peekPendingOrderDeeplink() {
  return sessionStorage.getItem(STORAGE_KEY)
}

export function peekPendingOrderDeeplinkPath() {
  const orderId = peekPendingOrderDeeplink()
  if (!orderId) return null
  const kind = sessionStorage.getItem(STORAGE_KIND_KEY)
  return kind === 'my' ? `/courier/my-orders/${orderId}` : `/courier/orders/${orderId}`
}

export function readMyOrdersDeeplinkFromEnvironment() {
  const raw = readRawStartParamFromEnvironment()
  if (isMyOrdersStartParam(raw)) return true
  return false
}
