import { DELIVERY_API_URL } from '../config.js'
import { getToken } from './tokenStorage.js'

/**
 * Подписка на SSE ORDER_ASSIGNED (fetch + Authorization header).
 */
export function subscribeOrderEvents(onAssigned, signal) {
  const token = getToken()
  if (!token) {
    return Promise.resolve(() => {})
  }

  let cancelled = false
  const abort = () => {
    cancelled = true
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
        if (!chunk.includes('event:ORDER_ASSIGNED')) {
          continue
        }
        const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
        if (!dataLine) {
          continue
        }
        try {
          const payload = JSON.parse(dataLine.slice(5).trim())
          onAssigned(payload)
        } catch {
          /* ignore malformed event */
        }
      }
    }
  }

  run().catch(() => {})
  return abort
}
