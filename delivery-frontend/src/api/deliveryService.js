import { DELIVERY_API_URL } from '../config.js'
import { deliveryApi } from './http.js'

export function fetchMe() {
  return deliveryApi('/me')
}

export function setActiveOrganization(organizationId) {
  return deliveryApi('/me/active-organization', 'PATCH', { organizationId })
}

export function createCourierService(name) {
  return deliveryApi('/organizations', 'POST', { type: 'courier_service', name })
}

/** Legacy: createRestaurant(name, courierServiceId). New: createRestaurant({ name, courierServiceId, owner }). */
export function createRestaurant(nameOrBody, courierServiceId) {
  if (typeof nameOrBody === 'object' && nameOrBody !== null) {
    return deliveryApi('/restaurants', 'POST', nameOrBody)
  }
  return deliveryApi('/restaurants', 'POST', {
    name: nameOrBody,
    courierServiceId,
  })
}

export function listOrganizationMembers(organizationId) {
  return deliveryApi(`/organizations/${organizationId}/members`)
}

export function listOrganizations() {
  return deliveryApi('/organizations')
}

export function listRestaurants() {
  return deliveryApi('/restaurants')
}

export function listCouriers(courierServiceId) {
  return deliveryApi(`/couriers?courierServiceId=${courierServiceId}`)
}

export function getCourier(memberId) {
  return deliveryApi(`/couriers/${memberId}`)
}

export function addOrganizationMember(organizationId, body) {
  return deliveryApi(`/organizations/${organizationId}/members`, 'POST', body)
}

export function patchOrganizationMember(organizationId, userId, body) {
  return deliveryApi(`/organizations/${organizationId}/members/${userId}`, 'PATCH', body)
}

export function addCourier(body) {
  return deliveryApi('/couriers', 'POST', body)
}

export function patchCourier(memberId, body) {
  return deliveryApi(`/couriers/${memberId}`, 'PATCH', body)
}

export function resetCourierAccess(memberId) {
  return deliveryApi(`/couriers/${memberId}/reset-access`, 'POST')
}

export function resetOrganizationMemberAccess(organizationId, userId) {
  return deliveryApi(`/organizations/${organizationId}/members/${userId}/reset-access`, 'POST')
}

export function removeOrganizationMember(organizationId, userId) {
  return deliveryApi(`/organizations/${organizationId}/members/${userId}`, 'DELETE')
}

export function patchRestaurant(restaurantId, body) {
  return deliveryApi(`/restaurants/${restaurantId}`, 'PATCH', body)
}

export function listPickupPoints(restaurantId) {
  return deliveryApi(`/restaurants/${restaurantId}/pickup-points`)
}

export function createPickupPoint(restaurantId, body) {
  return deliveryApi(`/restaurants/${restaurantId}/pickup-points`, 'POST', body)
}

export function patchPickupPoint(pointId, body) {
  return deliveryApi(`/pickup-points/${pointId}`, 'PATCH', body)
}

export function deletePickupPoint(pointId) {
  return deliveryApi(`/pickup-points/${pointId}`, 'DELETE')
}

export function listChannels(courierServiceId) {
  return deliveryApi(`/channels?courierServiceId=${courierServiceId}`)
}

export function createChannel(body) {
  return deliveryApi('/channels', 'POST', body)
}

export function patchChannel(channelId, body) {
  return deliveryApi(`/channels/${channelId}`, 'PATCH', body)
}

export function deactivateChannel(channelId) {
  return deliveryApi(`/channels/${channelId}`, 'DELETE')
}

export function getRestaurantChannels(restaurantId) {
  return deliveryApi(`/restaurants/${restaurantId}/channels`)
}

export function replaceRestaurantChannels(restaurantId, channelIds) {
  return deliveryApi(`/restaurants/${restaurantId}/channels`, 'PUT', { channelIds })
}

export function createOrder(body) {
  return deliveryApi('/orders', 'POST', body)
}

export function listOrders(params) {
  const q = new URLSearchParams()
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v != null && v !== '') q.set(k, v)
  })
  const qs = q.toString()
  return deliveryApi(`/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(orderId) {
  return deliveryApi(`/orders/${orderId}`)
}

export function patchOrder(orderId, body) {
  return deliveryApi(`/orders/${orderId}`, 'PATCH', body)
}

export function cancelOrder(orderId) {
  return deliveryApi(`/orders/${orderId}/cancel`, 'POST')
}

export function republishOrder(orderId) {
  return deliveryApi(`/orders/${orderId}/republish`, 'POST')
}

export function assignOrder(orderId, courierId) {
  return deliveryApi(`/orders/${orderId}/assign`, 'POST', { courierId })
}

/** @deprecated используйте assignOrder */
export function acceptOrder(orderId) {
  return deliveryApi(`/orders/${orderId}/accept`, 'POST')
}

export function changeOrderStatus(orderId, status) {
  return deliveryApi(`/orders/${orderId}/status`, 'POST', { status })
}

async function publicDeliveryFetch(path, options = {}) {
  const url = `${DELIVERY_API_URL}${path.startsWith('/') ? path : `/${path}`}`
  const res = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  if (!res.ok) {
    let data = null
    try {
      const text = await res.text()
      data = text ? JSON.parse(text) : null
    } catch {
      data = null
    }
    const message =
      (typeof data?.detail === 'string' && data.detail.trim()) ||
      (typeof data?.message === 'string' && data.message.trim()) ||
      (typeof data?.error === 'string' && data.error.trim()) ||
      'Не удалось выполнить действие'
    const err = new Error(message)
    err.status = res.status
    err.error = data?.error
    throw err
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export function fetchMessengerRegistrationStatus(provider, externalId) {
  const params = new URLSearchParams({ provider, externalId })
  return publicDeliveryFetch(`/public/courier-requests/messenger-status?${params}`)
}

/** Единая публичная заявка на доступ (веб и мессенджер). */
export function submitCourierRequest(body) {
  return publicDeliveryFetch('/public/courier-requests', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function listCourierRequests(courierServiceId) {
  return deliveryApi(`/courier-requests?courierServiceId=${courierServiceId}`)
}

export function rejectCourierRequest(requestId, courierServiceId) {
  return deliveryApi(
    `/courier-requests/${requestId}/reject?courierServiceId=${courierServiceId}`,
    'PATCH',
  )
}

export function approveCourierRequest(requestId, courierServiceId) {
  return deliveryApi(
    `/courier-requests/${requestId}/approve?courierServiceId=${courierServiceId}`,
    'PATCH',
  )
}
