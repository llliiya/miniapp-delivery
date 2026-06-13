import { useEffect } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
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
      capturePendingOrderDeeplink()
      if (readMyOrdersDeeplinkFromEnvironment()) {
        navigate('/courier/my-orders', { replace: true })
        return true
      }
      if (isPendingCourier) return true

      const rawStartParam = readRawStartParamFromEnvironment()
      let path = peekPendingOrderDeeplinkPath()
      if (!path) {
        const orderId = readStartParamFromEnvironment()
        if (orderId) {
          path = resolveCourierOrderDeeplinkPath(orderId, rawStartParam)
        }
      }

      if (path) {
        consumePendingOrderDeeplink()
        navigate(path, { replace: true })
        return true
      }
      return false
    }

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
  }, [navigate, isAuthenticated, interfaceMode, isPendingCourier, isBlockedCourier])

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
