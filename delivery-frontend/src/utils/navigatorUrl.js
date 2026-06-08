function hasCoords(lat, lon) {
  return Number.isFinite(lat) && Number.isFinite(lon)
}

function encodeRoutePoint(value) {
  return encodeURIComponent(value)
}

/**
 * @param {number|null|undefined} lat
 * @param {number|null|undefined} lon
 * @param {string|null|undefined} address
 * @param {{ lat: number, lon: number } | null | undefined} resolved
 */
export function formatRoutePoint(lat, lon, address, resolved) {
  if (hasCoords(lat, lon)) {
    return `${lat},${lon}`
  }
  if (resolved && hasCoords(resolved.lat, resolved.lon)) {
    return `${resolved.lat},${resolved.lon}`
  }
  const text = typeof address === 'string' ? address.trim() : ''
  return text || null
}

/**
 * @param {object} order
 * @param {{ pickup?: { lat: number, lon: number } | null, delivery?: { lat: number, lon: number } | null } | null} resolvedPoints
 * @returns {string | null}
 */
export function buildYandexMapsRouteUrl(order, resolvedPoints = null) {
  const origin = formatRoutePoint(
    order.pickupLat,
    order.pickupLon,
    order.pickupAddress,
    resolvedPoints?.pickup,
  )
  const destination = formatRoutePoint(
    order.deliveryLat,
    order.deliveryLon,
    order.deliveryAddress,
    resolvedPoints?.delivery,
  )

  if (origin && destination) {
    return `https://yandex.ru/maps/?rtext=${encodeRoutePoint(origin)}~${encodeRoutePoint(destination)}&rtt=auto`
  }
  if (destination) {
    return `https://yandex.ru/maps/?rtext=~${encodeRoutePoint(destination)}&rtt=auto`
  }
  if (origin) {
    return `https://yandex.ru/maps/?rtext=~${encodeRoutePoint(origin)}&rtt=auto`
  }
  return null
}
