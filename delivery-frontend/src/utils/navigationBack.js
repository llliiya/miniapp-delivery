const TAB_ROOT_PATHS = new Set([
  '/courier/orders',
  '/courier/my-orders',
  '/courier/profile',
  '/courier/map',
  '/restaurant/orders',
  '/restaurant/pickup',
  '/restaurant/channels',
  '/restaurant/staff',
  '/restaurant/profile',
  '/service/orders',
  '/service/restaurants',
  '/service/couriers',
  '/service/channels',
  '/service/profile',
])

const AUTH_ENTRY_PATHS = new Set([
  '/login',
  '/join',
  '/join-courier',
  '/messenger/link',
])

const ALWAYS_NO_BACK_PATHS = new Set([
  '/',
  '/no-access',
  '/select-organization',
  '/courier',
  '/restaurant',
  '/service',
])

export function normalizePathname(pathname) {
  if (!pathname) return '/'
  if (pathname.length > 1 && pathname.endsWith('/')) {
    return pathname.slice(0, -1)
  }
  return pathname
}

export function isMobileViewport() {
  if (typeof window === 'undefined') return false
  return window.innerWidth <= 768 || 'ontouchstart' in window
}

export function canNavigateBack(pathname) {
  const path = normalizePathname(pathname)

  if (ALWAYS_NO_BACK_PATHS.has(path) || TAB_ROOT_PATHS.has(path)) {
    return false
  }

  if (AUTH_ENTRY_PATHS.has(path)) {
    return typeof window !== 'undefined' && window.history.length > 1
  }

  return true
}

export function navigateBack(navigate) {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    navigate(-1)
    return
  }

  const path = normalizePathname(window.location?.pathname || '/')
  if (path.startsWith('/courier/')) {
    navigate('/courier/orders', { replace: true })
    return
  }
  if (path.startsWith('/restaurant/')) {
    navigate('/restaurant/orders', { replace: true })
    return
  }
  if (path.startsWith('/service/')) {
    navigate('/service/orders', { replace: true })
    return
  }

  navigate('/login', { replace: true })
}
