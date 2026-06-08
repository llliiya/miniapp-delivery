import { formatShortAddress } from './formatShortAddress.js'

/** Короткая строка адреса доставки (для старых заказов — из полного текста). */
export function formatDeliveryAddressMain(order) {
  return formatShortAddress(order?.deliveryAddress || '')
}

/** Квартира / подъезд / этаж / домофон одной строкой. */
export function formatDeliveryAddressDetails(order) {
  const parts = []
  const apartment = order?.apartment?.trim()
  const entrance = order?.entrance?.trim()
  if (apartment) parts.push(apartment)
  if (entrance) parts.push(entrance)
  return parts.join(', ')
}

/** Строка курьера в карточке заказа: имя или publicId, не account userId. */
export function formatCourierLine(order) {
  if (order?.courierUserId == null && order?.courierPublicId == null) return null
  const name = order?.courierDisplayName?.trim()
  if (name) return `Курьер: ${name}`
  if (order?.courierPublicId != null) return `Курьер ID: ${order.courierPublicId}`
  return null
}
