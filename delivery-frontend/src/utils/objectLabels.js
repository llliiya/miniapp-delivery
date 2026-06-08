import { platformLabel } from './channelLabels.js'

export function objectStatusLabel(active) {
  return active ? 'Активен' : 'Неактивен'
}

export function formatChannelLine(channel) {
  const place = channel.city?.trim() || channel.name?.trim() || '—'
  return `${platformLabel(channel.type)} • ${place}`
}

export function pluralPickupPoints(count) {
  const n = Math.abs(count) % 100
  const n1 = n % 10
  if (n > 10 && n < 20) return `${count} точек забора`
  if (n1 > 1 && n1 < 5) return `${count} точки забора`
  if (n1 === 1) return `${count} точка забора`
  return `${count} точек забора`
}

export function pluralOrders(count) {
  const n = Math.abs(count) % 100
  const n1 = n % 10
  if (n > 10 && n < 20) return `${count} заказов`
  if (n1 > 1 && n1 < 5) return `${count} заказа`
  if (n1 === 1) return `${count} заказ`
  return `${count} заказов`
}

export function pluralConnectedChannels(count) {
  const n = Math.abs(count) % 100
  const n1 = n % 10
  if (n > 10 && n < 20) return `Подключено: ${count} каналов`
  if (n1 > 1 && n1 < 5) return `Подключено: ${count} канала`
  if (n1 === 1) return `Подключено: ${count} канал`
  return `Подключено: ${count} каналов`
}
