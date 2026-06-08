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

export const API_BASE_URL = IS_DEV && useLocalProxy ? '' : devApiBase || ''
export const API_URL = `${API_BASE_URL}/api`
export const DELIVERY_API_URL = `${API_URL}/delivery`
export const USE_NGROK_HEADER = false

export const DEV_AUTH_ENABLED = import.meta.env.VITE_DEV_AUTH_ENABLED === 'true'
