import { useEffect } from 'react'
import { subscribeOrderEvents } from '../api/orderEvents.js'

/**
 * Подписка на обновление статуса публикации конкретного заказа через SSE.
 */
export function useOrderPublicationSse(orderId, onPublicationUpdated) {
  useEffect(() => {
    if (!orderId || !onPublicationUpdated) {
      return undefined
    }
    const controller = new AbortController()
    subscribeOrderEvents(
      {
        onPublicationUpdated: (payload) => {
          if (payload?.orderId === orderId) {
            onPublicationUpdated(payload)
          }
        },
      },
      controller.signal,
    )
    return () => controller.abort()
  }, [orderId, onPublicationUpdated])
}
