import { useAuth } from '../context/AuthContext.jsx'

const SERVICE_STAFF_ROLES = new Set(['owner', 'manager'])

function normalizeRole(role) {
  if (!role) return null
  return String(role).toLowerCase()
}

function isDirectServiceStaffMembership(membership) {
  if (!membership) return false
  if (membership.organizationType !== 'courier_service') return false
  if (membership.status !== 'active') return false
  if (membership.accessKind === 'service_scope') return false
  return SERVICE_STAFF_ROLES.has(normalizeRole(membership.role))
}

/** Прямое членство owner/manager в курьерской службе (как requireServiceStaff на backend). */
export function findServiceStaffMembership(activeMembership, memberships) {
  if (isDirectServiceStaffMembership(activeMembership)) {
    return activeMembership
  }
  return (memberships || []).find(isDirectServiceStaffMembership) || null
}

export function canManageServiceSettings(activeMembership, memberships) {
  return findServiceStaffMembership(activeMembership, memberships) != null
}

export function useCourierServiceId() {
  const { activeMembership } = useAuth()
  if (!activeMembership) return null
  if (activeMembership.organizationType === 'courier_service') {
    return activeMembership.organizationId
  }
  return activeMembership.courierServiceId || null
}

export function useRestaurantId() {
  const { activeMembership } = useAuth()
  if (!activeMembership) return null
  if (activeMembership.organizationType === 'client_restaurant') {
    return activeMembership.organizationId
  }
  return null
}

export function canManageChannelBindings(activeMembership) {
  if (!activeMembership) return false
  if (activeMembership.organizationType === 'courier_service') {
    return activeMembership.role === 'owner' || activeMembership.role === 'manager'
  }
  return activeMembership.accessKind === 'service_scope'
}
