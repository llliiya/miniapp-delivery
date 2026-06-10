const STORAGE_PREFIX = 'delivery_last_restaurant_'

export function getLastSelectedRestaurant(courierServiceId) {
  if (!courierServiceId) return ''
  try {
    return localStorage.getItem(`${STORAGE_PREFIX}${courierServiceId}`) || ''
  } catch {
    return ''
  }
}

export function saveLastSelectedRestaurant(courierServiceId, restaurantId) {
  if (!courierServiceId || !restaurantId) return
  try {
    localStorage.setItem(`${STORAGE_PREFIX}${courierServiceId}`, restaurantId)
  } catch {
    // ignore quota / private mode
  }
}
