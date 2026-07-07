const getEnvironment = () => {
  if (import.meta.env.VITE_ENV) {
    return import.meta.env.VITE_ENV
  }
  return import.meta.env.PROD ? 'prod' : 'dev'
}

export const ENV = getEnvironment()
export const IS_DEV = ENV === 'dev'

const devApiBase = import.meta.env.VITE_API_BASE_URL || ''
const useLocalProxy =
  typeof devApiBase === 'string' &&
  (devApiBase === '' || devApiBase.includes('localhost') || devApiBase.startsWith('http://127.0.0.1'))

function resolveApiBaseUrl() {
  if (IS_DEV && useLocalProxy) {
    // В dev API идёт напрямую на gateway :8080 (не через nginx :5173).
    if (devApiBase.includes(':8080')) {
      return devApiBase.replace(/\/$/, '')
    }
    return ''
  }
  if (!devApiBase) {
    return ''
  }
  if (typeof window !== 'undefined') {
    try {
      const configured = new URL(devApiBase)
      if (configured.origin !== window.location.origin) {
        // UI отдаётся с тем же хостом, nginx проксирует /api — не уходим на другой поддомен.
        return window.location.origin
      }
    } catch {
      // ignore invalid VITE_API_BASE_URL
    }
  }
  return devApiBase
}

export const API_BASE_URL = resolveApiBaseUrl()
export const API_URL = `${API_BASE_URL}/api`
export const DELIVERY_API_URL = `${API_URL}/delivery`
export const USE_NGROK_HEADER = false

export const DEV_AUTH_ENABLED = import.meta.env.VITE_DEV_AUTH_ENABLED === 'true'
