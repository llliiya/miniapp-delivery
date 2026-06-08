import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listOrders } from '../../api/deliveryService.js'
import { subscribeOrderEvents } from '../../api/orderEvents.js'
import BlockedCourierScreen from '../../components/courier/BlockedCourierScreen.jsx'
import PendingActivationScreen from '../../components/courier/PendingActivationScreen.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import EmptyState, { EmptyStateIcon } from '../../components/EmptyState.jsx'
import OrderListItem from '../shared/OrderListItem.jsx'
import {
  capturePendingOrderDeeplink,
  consumePendingOrderDeeplink,
  readStartParamFromEnvironment,
  resolveCourierOrderDeeplinkPath,
} from '../../utils/deeplink.js'

export default function CourierOrdersPage() {
  const navigate = useNavigate()
  const { isPendingCourier, isBlockedCourier } = useAuth()
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setMessage('')
    try {
      setOrders((await listOrders({ scope: 'courier' })) || [])
    } catch (e) {
      if (e?.status !== 403) {
        setMessage(e?.message || 'Не удалось загрузить заказы')
      }
      setOrders([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isPendingCourier || isBlockedCourier) return
    capturePendingOrderDeeplink()
    const orderId = readStartParamFromEnvironment()
    const path = resolveCourierOrderDeeplinkPath(orderId)
    if (path) {
      consumePendingOrderDeeplink()
      navigate(path, { replace: true })
    }
  }, [isPendingCourier, isBlockedCourier, navigate])

  useEffect(() => {
    if (isPendingCourier || isBlockedCourier) {
      setLoading(false)
      setOrders([])
      return
    }
    reload()
  }, [isPendingCourier, isBlockedCourier, reload])

  useEffect(() => {
    if (isPendingCourier || isBlockedCourier) {
      return undefined
    }
    const controller = new AbortController()
    subscribeOrderEvents(() => {
      reload()
    }, controller.signal)
    return () => controller.abort()
  }, [isPendingCourier, isBlockedCourier, reload])

  if (isBlockedCourier) {
    return <BlockedCourierScreen compactTitle="Заказы" />
  }

  if (isPendingCourier) {
    return <PendingActivationScreen />
  }

  return (
    <div className="courier-page">
      <h2 className="courier-page__title">Свободные заказы</h2>
      {message && <p className="courier-page__hint">{message}</p>}
      {loading && <p className="muted">Загрузка…</p>}
      {!loading && orders.length === 0 && (
        <EmptyState
          icon={
            <EmptyStateIcon>
              <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="24" cy="24" r="16" />
                <path d="M24 16v8l5 3" />
              </svg>
            </EmptyStateIcon>
          }
          title="Свободных заказов нет"
          description="Новые заказы появятся здесь, когда объекты опубликуют доставку."
        />
      )}
      <div className="courier-orders-list">
        {orders.map((order) => (
          <OrderListItem
            key={order.id}
            order={order}
            basePath="/courier/orders"
            showRestaurantName
            showCustomerDetails={false}
          />
        ))}
      </div>
    </div>
  )
}
