import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import {
  INTERFACE_MODES,
  isBlockedCourier,
  isNoAccess,
  isPendingCourier,
} from '../utils/deliverySession.js'

export default function RequireDeliveryAccess({ mode, children }) {
  const { loading, deliveryMe, usableMemberships, interfaceMode, devUiRole } = useAuth()

  if (loading) {
    return <div className="card" style={{ margin: 16 }}>Загрузка…</div>
  }

  if (import.meta.env.DEV && devUiRole === mode) {
    return children
  }

  if (!deliveryMe || isNoAccess(deliveryMe)) {
    return <Navigate to="/no-access" replace />
  }

  if (
    mode === INTERFACE_MODES.COURIER &&
    (isPendingCourier(deliveryMe) || isBlockedCourier(deliveryMe))
  ) {
    return children
  }

  if (interfaceMode !== mode) {
    if (interfaceMode === INTERFACE_MODES.COURIER) return <Navigate to="/courier" replace />
    if (interfaceMode === INTERFACE_MODES.RESTAURANT) return <Navigate to="/restaurant" replace />
    if (interfaceMode === INTERFACE_MODES.SERVICE) return <Navigate to="/service" replace />
    return <Navigate to="/select-organization" replace />
  }

  const active = usableMemberships.find((m) => m.organizationId === deliveryMe.activeOrganizationId)
  if (active?.status === 'blocked') {
    return <Navigate to="/no-access" replace />
  }

  return children
}
