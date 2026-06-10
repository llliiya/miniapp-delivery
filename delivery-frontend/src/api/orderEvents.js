import { DELIVERY_API_URL } from '../config.js'
import { getToken } from './tokenStorage.js'

/**
 * Подписка на SSE события заказов (fetch + Authorization header).
 * @param {((payload: object) => void) | { onAssigned?: (payload: object) => void, onPublicationUpdated?: (payload: object) => void }} handlers
 */
export function subscribeOrderEvents(handlers, signal) {
  const token = getToken()
  if (!token) {
    return Promise.resolve(() => {})
  }

  const onAssigned = typeof handlers === 'function' ? handlers : handlers?.onAssigned
  const onPublicationUpdated =
    typeof handlers === 'object' ? handlers?.onPublicationUpdated : undefined

  let cancelled = false
  const abort = () => {
    cancelled = true
  }

  const dispatchEvent = (chunk, eventName, callback) => {
    if (!callback || !chunk.includes(`event:${eventName}`)) {
      return
    }
    const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
    if (!dataLine) {
      return
    }
    try {
      const payload = JSON.parse(dataLine.slice(5).trim())
      callback(payload)
    } catch {
      /* ignore malformed event */
    }
  }

  const run = async () => {
    const res = await fetch(`${DELIVERY_API_URL}/orders/events`, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
      credentials: 'include',
      signal,
    })
    if (!res.ok || !res.body) {
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (!cancelled) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() || ''
      for (const chunk of chunks) {
        dispatchEvent(chunk, 'ORDER_ASSIGNED', onAssigned)
        dispatchEvent(chunk, 'ORDER_PUBLICATION_UPDATED', onPublicationUpdated)
      }
    }
  }

  run().catch(() => {})
  return abort
}
