const LABELS = {
  waiting_for_courier: 'Ожидает курьера',
  courier_heading_to_pickup: 'Едет за заказом',
  courier_delivering: 'Везёт заказ',
  completed: 'Выполнен',
  cancelled: 'Отменён',
}

export const ACTIVE_COURIER_STATUSES = ['courier_heading_to_pickup', 'courier_delivering']

export function orderStatusLabel(status) {
  return LABELS[status] || 'Неизвестный статус'
}

export function orderStatusBadgeClass(status) {
  const base = 'order-status-badge'
  switch (status) {
    case 'waiting_for_courier':
      return `${base} order-status-badge--waiting`
    case 'courier_heading_to_pickup':
      return `${base} order-status-badge--pickup`
    case 'courier_delivering':
      return `${base} order-status-badge--delivering`
    case 'completed':
      return `${base} order-status-badge--completed`
    case 'cancelled':
      return `${base} order-status-badge--cancelled`
    default:
      return base
  }
}

export function canEditOrder(order) {
  return order?.status === 'waiting_for_courier' && !order?.courierUserId
}

export function canCancelOrder(order) {
  return order?.status !== 'completed' && order?.status !== 'cancelled'
}

export function isActiveCourierOrder(order) {
  return ACTIVE_COURIER_STATUSES.includes(order?.status)
}

export function isFreeOrder(order) {
  return order?.status === 'waiting_for_courier' && !order?.courierUserId
}

export function nextCourierStatus(order) {
  if (order?.status === 'courier_heading_to_pickup') return 'courier_delivering'
  if (order?.status === 'courier_delivering') return 'completed'
  return null
}

export function nextCourierStatusButtonLabel(order) {
  if (order?.status === 'courier_heading_to_pickup') return 'Начать доставку'
  if (order?.status === 'courier_delivering') return 'Завершить заказ'
  return null
}

export function toDatetimeLocalValue(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
