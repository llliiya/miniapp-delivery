import { ACTIVE_COURIER_STATUSES } from './orderStatus.js'

function startOfLocalDay(date = new Date()) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

export function computeServiceOrderStats(orders) {
  const todayStart = startOfLocalDay()
  const tomorrowStart = new Date(todayStart)
  tomorrowStart.setDate(tomorrowStart.getDate() + 1)

  let waiting = 0
  let inWork = 0
  let completedToday = 0
  let cancelled = 0

  for (const order of orders || []) {
    if (order.status === 'waiting_for_courier') waiting += 1
    if (ACTIVE_COURIER_STATUSES.includes(order.status)) inWork += 1
    if (order.status === 'cancelled') cancelled += 1
    if (order.status === 'completed' && order.completedAt) {
      const completed = new Date(order.completedAt)
      if (completed >= todayStart && completed < tomorrowStart) {
        completedToday += 1
      }
    }
  }

  return { waiting, inWork, completedToday, cancelled }
}

export function matchesQuickStatusFilter(order, filterKey) {
  if (!filterKey) return true
  if (filterKey === 'in_work') {
    return ACTIVE_COURIER_STATUSES.includes(order.status)
  }
  return order.status === filterKey
}
