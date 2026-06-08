import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { DEV_AUTH_ENABLED } from '../config.js'
import {
  capturePendingOrderDeeplink,
  peekPendingOrderDeeplinkPath,
  readMyOrdersDeeplinkFromEnvironment,
  readStartParamFromEnvironment,
  resolveCourierOrderDeeplinkPath,
} from '../utils/deeplink.js'
import {
  getSwitchableMemberships,
  INTERFACE_MODES,
  isBlockedCourier,
  isNoAccess,
  isPendingCourier,
  resolveInterfaceMode,
  routeForInterfaceMode,
} from '../utils/deliverySession.js'

export default function HomeRedirect() {
  const { loading, deliveryMe, usableMemberships, interfaceMode, devUiRole } = useAuth()

  if (loading) {
    return <div className="card" style={{ margin: 16 }}>Загрузка…</div>
  }

  if (DEV_AUTH_ENABLED && devUiRole) {
    return <Navigate to={`/${devUiRole}`} replace />
  }

  if (isBlockedCourier(deliveryMe) || isPendingCourier(deliveryMe)) {
    return <Navigate to="/courier" replace />
  }

  if (!deliveryMe || isNoAccess(deliveryMe)) {
    return <Navigate to="/no-access" replace />
  }

  const switchableMemberships = getSwitchableMemberships(deliveryMe?.memberships)

  if (switchableMemberships.length > 1 && !deliveryMe.activeOrganizationId) {
    return <Navigate to="/select-organization" replace />
  }

  let resolved = interfaceMode
  if (!resolved && usableMemberships.length === 1) {
    resolved = resolveInterfaceMode(usableMemberships[0])
  }

  if (!resolved) {
    return <Navigate to="/select-organization" replace />
  }

  if (resolved === INTERFACE_MODES.COURIER && !isPendingCourier(deliveryMe)) {
    capturePendingOrderDeeplink()
    if (readMyOrdersDeeplinkFromEnvironment()) {
      return <Navigate to="/courier/my-orders" replace />
    }
    const orderPath = peekPendingOrderDeeplinkPath()
      || resolveCourierOrderDeeplinkPath(readStartParamFromEnvironment())
    if (orderPath) {
      return <Navigate to={orderPath} replace />
    }
  }

  return <Navigate to={routeForInterfaceMode(resolved, deliveryMe)} replace />
}
