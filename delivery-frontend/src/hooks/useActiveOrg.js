import { useAuth } from '../context/AuthContext.jsx'

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
