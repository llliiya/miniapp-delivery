export const INTERFACE_MODES = {
  COURIER: 'courier',
  RESTAURANT: 'restaurant',
  SERVICE: 'service',
}

export const USER_STATUS = {
  ACTIVE: 'ACTIVE',
  PENDING: 'PENDING',
  BLOCKED: 'BLOCKED',
  NO_ACCESS: 'NO_ACCESS',
}

/** Статус приходит с backend /me — фронт не вычисляет самостоятельно. */
export function isPendingCourier(me) {
  return me?.status === USER_STATUS.PENDING
}

export function isBlockedCourier(me) {
  return me?.status === USER_STATUS.BLOCKED
}

export function isNoAccess(me) {
  return me?.status === USER_STATUS.NO_ACCESS
}

export function isDeliveryBlocked(me) {
  return isBlockedCourier(me)
}

export function isActiveUser(me) {
  return me?.status === USER_STATUS.ACTIVE
}

export function resolveInterfaceMode(membership) {
  if (!membership || membership.status !== 'active') {
    return null
  }
  if (membership.accessKind === 'service_scope') {
    return INTERFACE_MODES.SERVICE
  }
  if (membership.organizationType === 'courier_service') {
    if (membership.role === 'courier') return INTERFACE_MODES.COURIER
    if (membership.role === 'owner' || membership.role === 'manager') return INTERFACE_MODES.SERVICE
  }
  if (membership.organizationType === 'client_restaurant') {
    if (membership.role === 'owner' || membership.role === 'manager') return INTERFACE_MODES.RESTAURANT
  }
  return null
}

export function getUsableMemberships(memberships) {
  if (!Array.isArray(memberships)) return []
  return memberships.filter((m) => resolveInterfaceMode(m) != null)
}

/** Организации для UI переключения (без синтетических service_scope). */
export function getSwitchableMemberships(memberships) {
  return getUsableMemberships(memberships).filter(
    (m) => m.accessKind !== 'service_scope',
  )
}

export function findMembership(memberships, organizationId) {
  return (memberships || []).find((m) => m.organizationId === organizationId)
}

export function routeForInterfaceMode(mode, me) {
  if (mode === INTERFACE_MODES.COURIER) return '/courier'
  if (mode === INTERFACE_MODES.RESTAURANT) return '/restaurant'
  if (mode === INTERFACE_MODES.SERVICE) return '/service'
  if (isBlockedCourier(me) || isPendingCourier(me)) return '/courier'
  return '/no-access'
}

export function labelForMembership(m) {
  const id = m.organizationPublicId != null ? `ID ${m.organizationPublicId}` : ''
  const type =
    m.organizationType === 'courier_service'
      ? 'Служба'
      : m.organizationType === 'client_restaurant'
        ? 'Объект'
        : m.organizationType
  return `${m.organizationName} · ${type} ${id}`.trim()
}
