export function formatOrderDateTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}`
}

const ASAP_THRESHOLD_MS = 2 * 60 * 1000

export function isAsapDelivery(order) {
  if (!order?.deliveryTime || !order?.createdAt) return false
  const deliveryMs = new Date(order.deliveryTime).getTime()
  const createdMs = new Date(order.createdAt).getTime()
  if (Number.isNaN(deliveryMs) || Number.isNaN(createdMs)) return false
  return Math.abs(deliveryMs - createdMs) <= ASAP_THRESHOLD_MS
}

export function formatOrderDeliveryTime(order) {
  const formatted = formatOrderDateTime(order?.deliveryTime)
  if (isAsapDelivery(order)) {
    return `Как можно скорее / ${formatted}`
  }
  return formatted
}

export function formatOrderPrice(price) {
  if (price == null || price === '') return '—'
  const n = Number(price)
  if (Number.isNaN(n)) return `${price} ₽`
  return `${new Intl.NumberFormat('ru-RU').format(n)} ₽`
}
