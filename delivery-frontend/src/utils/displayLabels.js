const ROLE_LABELS = {
  owner: 'Собственник',
  manager: 'Менеджер',
  courier: 'Курьер',
}

const STATUS_LABELS = {
  active: 'Активен',
  inactive: 'Неактивен',
  blocked: 'Заблокирован',
}

const ORG_STATUS_LABELS = {
  active: 'Активна',
  inactive: 'Неактивна',
  blocked: 'Заблокирована',
}

const ORG_TYPE_LABELS = {
  courier_service: 'Служба доставки',
  client_restaurant: 'Объект',
}

export function labelForRole(role) {
  if (!role) return '—'
  const normalized = String(role).toLowerCase()
  return ROLE_LABELS[normalized] || '—'
}

export function labelForStatus(status) {
  if (!status) return '—'
  return STATUS_LABELS[status] || '—'
}

export function labelForOrgStatus(status) {
  if (!status) return '—'
  return ORG_STATUS_LABELS[status] || '—'
}

export function labelForOrgType(type) {
  if (!type) return '—'
  return ORG_TYPE_LABELS[type] || '—'
}

/** Имя организации; подставляет fallback при битой кодировке в БД (????). */
export function displayOrganizationName(name, fallback = 'Служба доставки') {
  const trimmed = name?.trim()
  if (!trimmed) return fallback
  if (/^[\uFFFD?]+$/.test(trimmed)) return fallback
  return trimmed
}

/** Текущая курьерская служба для экрана /service (прямое членство или через объект). */
export function resolveServiceMembership(activeMembership, memberships) {
  if (!activeMembership) return null
  if (activeMembership.organizationType === 'courier_service') {
    return activeMembership
  }
  const serviceId = activeMembership.courierServiceId
  if (!serviceId) return null
  return (
    (memberships || []).find(
      (m) => m.organizationType === 'courier_service' && m.organizationId === serviceId,
    ) || {
      organizationId: serviceId,
      organizationPublicId: null,
      organizationName: null,
      organizationType: 'courier_service',
      role: activeMembership.role,
      status: activeMembership.status,
    }
  )
}

export function isCurrentOrganization(membership, activeOrganizationId) {
  return membership?.organizationId === activeOrganizationId
}
