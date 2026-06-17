const STORAGE_KEY = 'delivery_pending_order_id'
const STORAGE_KIND_KEY = 'delivery_pending_order_kind'
const HANDLED_KEY = 'delivery_deeplink_handled'
export const MY_ORDERS_START_PARAM = 'delivery_my_orders'

function shouldLogDeeplink() {
  if (typeof window === 'undefined') return false
  try {
    if (import.meta.env?.DEV) return true
    if (window.localStorage?.getItem('debug_deeplink') === '1') return true
  } catch {
    // ignore
  }
  if (window.WebApp) return true
  const search = window.location.search || ''
  return /tgWebAppPlatform=max|WebAppStartParam|startapp=/i.test(search)
}

/**
 * Диагностика deeplink: raw start_param, orderId, маршрут.
 * @param {string} phase
 * @param {Record<string, unknown>} [extra]
 */
export function logDeeplink(phase, extra = {}) {
  if (!shouldLogDeeplink()) return
  const rawStartParam = readRawStartParamFromEnvironment()
  const resolvedOrderId = readOrderIdFromStartParam(rawStartParam)
  const resolvedPath = resolvedOrderId
    ? resolveCourierOrderDeeplinkPath(resolvedOrderId, rawStartParam)
    : null
  const pending = {
    orderId: typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(STORAGE_KEY) : null,
    kind: typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(STORAGE_KIND_KEY) : null,
  }
  console.info('[delivery deeplink]', phase, {
    rawStartParam,
    resolvedOrderId,
    resolvedPath,
    pending,
    ...extra,
  })
}

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

export function markDeeplinkHandled() {
  try {
    sessionStorage.setItem(HANDLED_KEY, '1')
    sessionStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KIND_KEY)
  } catch {
    // ignore
  }
}

export function isDeeplinkHandled() {
  try {
    return sessionStorage.getItem(HANDLED_KEY) === '1'
  } catch {
    return false
  }
}

export function normalizePathname(pathname) {
  if (!pathname) return '/'
  if (pathname.length > 1 && pathname.endsWith('/')) {
    return pathname.slice(0, -1)
  }
  return pathname
}

/** Уже на карточке заказа (свободный или мой) — не дёргать deeplink повторно. */
export function isOnCourierOrderRoute(pathname, orderId) {
  if (!orderId) return false
  const path = normalizePathname(pathname)
  return path === `/courier/orders/${orderId}` || path === `/courier/my-orders/${orderId}`
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
  if (value.startsWith('delivery_assign_order_')) {
    return value.replace('delivery_assign_order_', '')
  }
  if (value.startsWith('delivery_order_')) {
    return value.replace('delivery_order_', '')
  }
  if (value.startsWith('delivery_my_order_')) {
    return value.replace('delivery_my_order_', '')
  }
  return null
}

export function isAssignOrderStartParam(value) {
  return typeof value === 'string' && value.startsWith('delivery_assign_order_')
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

export function peekPendingOrderDeeplinkKind() {
  return sessionStorage.getItem(STORAGE_KIND_KEY)
}

export function isPendingAssignOrderDeeplink() {
  return peekPendingOrderDeeplinkKind() === 'assign'
}

export function capturePendingOrderDeeplink() {
  if (isDeeplinkHandled()) {
    logDeeplink('capture', { stored: false, reason: 'already_handled' })
    return null
  }
  const raw = readRawStartParamFromEnvironment()
  const orderId = readOrderIdFromStartParam(raw)
  if (orderId) {
    let kind = 'free'
    if (isMyOrderStartParam(raw)) {
      kind = 'my'
    } else if (isAssignOrderStartParam(raw)) {
      kind = 'assign'
    }
    sessionStorage.setItem(STORAGE_KEY, orderId)
    sessionStorage.setItem(STORAGE_KIND_KEY, kind)
    logDeeplink('capture', { stored: true })
  } else {
    logDeeplink('capture', { stored: false })
  }
  return orderId
}

export function consumePendingOrderDeeplink() {
  const orderId = sessionStorage.getItem(STORAGE_KEY)
  const kind = sessionStorage.getItem(STORAGE_KIND_KEY)
  if (orderId) {
    sessionStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KIND_KEY)
    const path = kind === 'my'
      ? `/courier/my-orders/${orderId}`
      : `/courier/orders/${orderId}`
    logDeeplink('consume', { orderId, kind, navigatedPath: path })
    return { orderId, kind }
  }
  logDeeplink('consume', { consumed: false })
  return null
}

export function peekPendingOrderDeeplink() {
  return sessionStorage.getItem(STORAGE_KEY)
}

export function peekPendingOrderDeeplinkPath() {
  const orderId = peekPendingOrderDeeplink()
  if (!orderId) return null
  const kind = sessionStorage.getItem(STORAGE_KIND_KEY)
  if (kind === 'my') return `/courier/my-orders/${orderId}`
  return `/courier/orders/${orderId}`
}

export function readMyOrdersDeeplinkFromEnvironment() {
  const raw = readRawStartParamFromEnvironment()
  if (isMyOrdersStartParam(raw)) return true
  return false
}
