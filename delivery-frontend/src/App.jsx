import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { useSwipeBackDisabledOnRoots } from './hooks/useSwipeBack.js'
import { useTelegramBackButton } from './hooks/useTelegramBackButton.js'
import { AuthProvider, useAuth } from './context/AuthContext.jsx'
import LoginPage from './pages/LoginPage.jsx'
import HomeRedirect from './pages/HomeRedirect.jsx'
import NoAccessPage from './pages/NoAccessPage.jsx'
import OrganizationPickerPage from './pages/OrganizationPickerPage.jsx'
import RequireDeliveryAccess from './components/RequireDeliveryAccess.jsx'
import CourierRoutes from './routes/CourierRoutes.jsx'
import RestaurantRoutes from './routes/RestaurantRoutes.jsx'
import ServiceRoutes from './routes/ServiceRoutes.jsx'
import { INTERFACE_MODES } from './utils/deliverySession.js'
import MessengerLinkPage from './pages/auth/MessengerLinkPage.jsx'
import JoinRestaurantPage from './pages/auth/JoinRestaurantPage.jsx'
import JoinCourierPage from './pages/auth/JoinCourierPage.jsx'
import {
  capturePendingOrderDeeplink,
  consumePendingOrderDeeplink,
  isAssignOrderStartParam,
  isPendingAssignOrderDeeplink,
  logDeeplink,
  markDeeplinkHandled,
  isDeeplinkHandled,
  isOnCourierOrderRoute,
  peekPendingOrderDeeplinkKind,
  peekPendingOrderDeeplinkPath,
  readMyOrdersDeeplinkFromEnvironment,
  readRawStartParamFromEnvironment,
  readStartParamFromEnvironment,
  resolveCourierOrderDeeplinkPath,
} from './utils/deeplink.js'
import { isMessengerContext } from './utils/messengerIdentity.js'
import { getMessengerNeedLink } from './pages/auth/messengerLinkSession.js'

function RequireAuth({ children }) {
  const { loading, isAuthenticated } = useAuth()
  if (loading) {
    return <div className="card" style={{ margin: 16 }}>Загрузка…</div>
  }
  if (!isAuthenticated) {
    if (isMessengerContext() && getMessengerNeedLink()) {
      return <Navigate to="/messenger/link" replace />
    }
    return <Navigate to="/login" replace />
  }
  return children
}

function DeeplinkHandler() {
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, interfaceMode, isPendingCourier, isBlockedCourier } = useAuth()

  useEffect(() => {
    capturePendingOrderDeeplink()
  }, [])

  useEffect(() => {
    if (!isAuthenticated || !interfaceMode || isBlockedCourier) return
    if (interfaceMode !== INTERFACE_MODES.COURIER) return

    let cancelled = false

    const tryNavigate = () => {
      if (cancelled) return true
      if (isDeeplinkHandled()) {
        logDeeplink('navigate_skipped', { reason: 'deeplink_handled' })
        return true
      }

      const rawStartParam = readRawStartParamFromEnvironment()
      const envOrderId = readStartParamFromEnvironment()
      if (envOrderId && isOnCourierOrderRoute(location.pathname, envOrderId)) {
        if (!isAssignOrderStartParam(rawStartParam) && !isPendingAssignOrderDeeplink()) {
          markDeeplinkHandled()
        }
        logDeeplink('navigate_skipped', { reason: 'already_on_order_route', path: location.pathname })
        return true
      }

      capturePendingOrderDeeplink()
      if (readMyOrdersDeeplinkFromEnvironment()) {
        logDeeplink('navigate', { target: '/courier/my-orders', reason: 'my_orders_start_param' })
        markDeeplinkHandled()
        navigate('/courier/my-orders', { replace: true })
        return true
      }
      if (isPendingCourier) {
        const hasOrderDeeplink = peekPendingOrderDeeplinkPath() || envOrderId
        if (!hasOrderDeeplink) {
          logDeeplink('navigate_skipped', { reason: 'pending_courier' })
          return true
        }
      }

      let path = peekPendingOrderDeeplinkPath()
      if (!path) {
        if (envOrderId) {
          path = resolveCourierOrderDeeplinkPath(envOrderId, rawStartParam)
        }
      }

      if (path) {
        const assignDeeplink =
          isAssignOrderStartParam(rawStartParam) || peekPendingOrderDeeplinkKind() === 'assign'
        if (!assignDeeplink) {
          consumePendingOrderDeeplink()
          markDeeplinkHandled()
        }
        logDeeplink('navigate', { target: path, rawStartParam, assignDeeplink })
        navigate(path, { replace: true })
        return true
      }
      return false
    }

    logDeeplink('handler_start', { isAuthenticated, interfaceMode, isPendingCourier, isBlockedCourier, path: location.pathname })

    if (tryNavigate()) return undefined

    const deadline = Date.now() + 4000
    const timer = window.setInterval(() => {
      if (tryNavigate() || Date.now() > deadline) {
        window.clearInterval(timer)
      }
    }, 150)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [navigate, location.pathname, isAuthenticated, interfaceMode, isPendingCourier, isBlockedCourier])

  return null
}

function MobileNavigationHandlers() {
  useSwipeBackDisabledOnRoots()
  useTelegramBackButton()
  return null
}

export default function App() {
  return (
    <AuthProvider>
      <DeeplinkHandler />
      <MobileNavigationHandlers />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/join" element={<JoinRestaurantPage />} />
        <Route path="/join-courier" element={<JoinCourierPage />} />
        <Route path="/messenger/link" element={<MessengerLinkPage />} />
        <Route path="/register" element={<Navigate to="/login?apply=1" replace />} />
        <Route
          path="/no-access"
          element={
            <RequireAuth>
              <NoAccessPage />
            </RequireAuth>
          }
        />
        <Route
          path="/select-organization"
          element={
            <RequireAuth>
              <OrganizationPickerPage />
            </RequireAuth>
          }
        />
        <Route
          path="/"
          element={
            <RequireAuth>
              <HomeRedirect />
            </RequireAuth>
          }
        />
        <Route
          path="/courier/*"
          element={
            <RequireAuth>
              <RequireDeliveryAccess mode={INTERFACE_MODES.COURIER}>
                <CourierRoutes />
              </RequireDeliveryAccess>
            </RequireAuth>
          }
        />
        <Route
          path="/restaurant/*"
          element={
            <RequireAuth>
              <RequireDeliveryAccess mode={INTERFACE_MODES.RESTAURANT}>
                <RestaurantRoutes />
              </RequireDeliveryAccess>
            </RequireAuth>
          }
        />
        <Route
          path="/service/*"
          element={
            <RequireAuth>
              <RequireDeliveryAccess mode={INTERFACE_MODES.SERVICE}>
                <ServiceRoutes />
              </RequireDeliveryAccess>
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
