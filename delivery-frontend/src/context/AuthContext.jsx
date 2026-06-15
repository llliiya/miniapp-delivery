import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  fetchMe as fetchDeliveryMeApi,
  setActiveOrganization as setActiveOrganizationApi,
} from '../api/deliveryService.js'
import { clearToken, fetchAuthMe, getToken, refreshAccessToken } from '../api/http.js'
import { tryMessengerAuth } from '../auth/authBootstrap.js'
import { isAccessTokenExpired } from '../utils/tokenStorage.js'
import { DEV_AUTH_ENABLED } from '../config.js'
import {
  findMembership,
  getUsableMemberships,
  isBlockedCourier as checkBlockedCourier,
  isPendingCourier as checkPendingCourier,
  resolveInterfaceMode,
  routeForInterfaceMode,
} from '../utils/deliverySession.js'

const DEV_UI_ROLE_KEY = 'delivery_dev_ui_role'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [loading, setLoading] = useState(true)
  const [accountUser, setAccountUser] = useState(null)
  const [deliveryMe, setDeliveryMe] = useState(null)
  const [devUiRole, setDevUiRoleState] = useState(() => {
    if (!DEV_AUTH_ENABLED) return ''
    try {
      return localStorage.getItem(DEV_UI_ROLE_KEY) || ''
    } catch {
      return ''
    }
  })

  const bootstrap = useCallback(async () => {
    setLoading(true)
    try {
      let token = getToken()
      if (token && isAccessTokenExpired(token)) {
        token = await refreshAccessToken()
        if (!token) {
          clearToken()
        }
      }
      if (!getToken()) {
        await tryMessengerAuth()
      }
      token = getToken()
      if (!token) {
        token = await refreshAccessToken()
      }
      if (!token) {
        setAccountUser(null)
        setDeliveryMe(null)
        return
      }
      const [authMe, me] = await Promise.all([
        fetchAuthMe().catch(() => null),
        fetchDeliveryMeApi().catch(() => null),
      ])
      setAccountUser(authMe)
      setDeliveryMe(me)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    bootstrap()
  }, [bootstrap])

  useEffect(() => {
    const onReauth = () => {
      bootstrap()
    }
    window.addEventListener('reauth', onReauth)
    return () => window.removeEventListener('reauth', onReauth)
  }, [bootstrap])

  const usableMemberships = useMemo(
    () => getUsableMemberships(deliveryMe?.memberships),
    [deliveryMe],
  )

  const activeMembership = useMemo(() => {
    if (!deliveryMe?.activeOrganizationId) return null
    return findMembership(deliveryMe.memberships, deliveryMe.activeOrganizationId)
  }, [deliveryMe])

  const interfaceMode = useMemo(() => {
    if (deliveryMe?.interfaceMode) {
      return deliveryMe.interfaceMode
    }
    if (activeMembership) {
      return resolveInterfaceMode(activeMembership)
    }
    return null
  }, [deliveryMe, activeMembership])

  const isPendingCourier = useMemo(
    () => checkPendingCourier(deliveryMe),
    [deliveryMe],
  )

  const isBlockedCourier = useMemo(
    () => checkBlockedCourier(deliveryMe),
    [deliveryMe],
  )

  const selectOrganization = useCallback(async (organizationId) => {
    const me = await setActiveOrganizationApi(organizationId)
    setDeliveryMe(me)
    const membership = findMembership(me.memberships, organizationId)
    const mode = resolveInterfaceMode(membership) || me.interfaceMode
    return routeForInterfaceMode(mode, me)
  }, [])

  const setDevUiRole = useCallback((role) => {
    if (!DEV_AUTH_ENABLED) return
    setDevUiRoleState(role || '')
    try {
      if (role) {
        localStorage.setItem(DEV_UI_ROLE_KEY, role)
      } else {
        localStorage.removeItem(DEV_UI_ROLE_KEY)
      }
    } catch {
      /* ignore */
    }
  }, [])

  const logout = useCallback(() => {
    clearToken()
    setAccountUser(null)
    setDeliveryMe(null)
  }, [])

  const refreshDeliveryStatus = useCallback(async () => {
    const me = await fetchDeliveryMeApi().catch(() => null)
    if (me) {
      setDeliveryMe(me)
    }
    return me
  }, [])

  const value = useMemo(
    () => ({
      loading,
      isAuthenticated: !!accountUser,
      accountUser,
      deliveryMe,
      usableMemberships,
      activeMembership,
      interfaceMode,
      isPendingCourier,
      isBlockedCourier,
      devUiRole,
      setDevUiRole,
      selectOrganization,
      logout,
      refresh: bootstrap,
      refreshDeliveryStatus,
    }),
    [
      loading,
      accountUser,
      deliveryMe,
      usableMemberships,
      activeMembership,
      interfaceMode,
      isPendingCourier,
      isBlockedCourier,
      devUiRole,
      setDevUiRole,
      selectOrganization,
      logout,
      bootstrap,
      refreshDeliveryStatus,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
