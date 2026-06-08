import {
  getRestaurantChannels,
  listOrders,
  listPickupPoints,
} from '../api/deliveryService.js'

export async function loadObjectStats(objectId, courierServiceId) {
  const [channelsRes, points, orders] = await Promise.all([
    getRestaurantChannels(objectId).catch(() => ({ channels: [] })),
    listPickupPoints(objectId).catch(() => []),
    courierServiceId
      ? listOrders({
          scope: 'service',
          courierServiceId,
          restaurantId: objectId,
        }).catch(() => [])
      : Promise.resolve([]),
  ])

  const channels = channelsRes?.channels || []
  return {
    channels,
    channelCount: channels.length,
    pickupCount: (points || []).length,
    orderCount: (orders || []).length,
  }
}
