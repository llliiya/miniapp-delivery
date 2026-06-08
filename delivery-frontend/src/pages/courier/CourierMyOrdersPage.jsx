import { useCallback, useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { listOrders } from '../../api/deliveryService.js'
import BlockedCourierScreen from '../../components/courier/BlockedCourierScreen.jsx'
import PendingSectionMessage from '../../components/courier/PendingSectionMessage.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import EmptyState, { EmptyStateIcon } from '../../components/EmptyState.jsx'
import OrderListItem from '../shared/OrderListItem.jsx'
import { ACTIVE_COURIER_STATUSES } from '../../utils/orderStatus.js'

const TABS = [
  { key: 'active', label: 'Активные' },
  { key: 'completed', label: 'Выполненные' },
  { key: 'cancelled', label: 'Отмененные' },
]

export default function CourierMyOrdersPage() {
  const { isPendingCourier, isBlockedCourier } = useAuth()
  const location = useLocation()
  const initialTab = location.state?.tab || 'active'
  const [tab, setTab] = useState(initialTab)
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setMessage('')
    try {
      if (tab === 'active') {
        const lists = await Promise.all(
          ACTIVE_COURIER_STATUSES.map((status) => listOrders({ scope: 'courier', status }))
        )
        setOrders(lists.flat().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)))
      } else {
        setOrders((await listOrders({ scope: 'courier', status: tab })) || [])
      }
    } catch (e) {
      if (e?.status !== 403) {
        setMessage(e?.message || 'Не удалось загрузить заказы')
      }
      setOrders([])
    } finally {
      setLoading(false)
    }
  }, [tab])

  useEffect(() => {
    if (isPendingCourier || isBlockedCourier) {
      setLoading(false)
      setOrders([])
      return
    }
    reload()
  }, [isPendingCourier, isBlockedCourier, reload])

  if (isBlockedCourier) {
    return <BlockedCourierScreen compactTitle="Мои заказы" />
  }

  if (isPendingCourier) {
    return (
      <PendingSectionMessage
        title="Мои заказы"
        message="История заказов появится после активации аккаунта администратором службы доставки."
      />
    )
  }

  return (
    <div className="courier-page">
      <h2 className="courier-page__title">Мои заказы</h2>
      <div className="courier-tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={tab === t.key ? 'btn' : 'btn btn-secondary'}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>
      {message && <p className="courier-page__hint">{message}</p>}
      {loading && <p className="muted">Загрузка…</p>}
      {!loading && orders.length === 0 && (
        <EmptyState
          icon={
            <EmptyStateIcon>
              <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 8h16l6 8v20a2 2 0 0 1-2 2H12a2 2 0 0 1-2-2V16l6-8z" />
                <path d="M22 8v6h8" />
              </svg>
            </EmptyStateIcon>
          }
          title="Заказов нет"
          description={
            tab === 'active'
              ? 'Примите заказ из раздела «Заказы», чтобы он появился здесь.'
              : 'В этой категории пока нет заказов.'
          }
        />
      )}
      <div className="courier-orders-list">
        {orders.map((order) => (
          <OrderListItem key={order.id} order={order} basePath="/courier/my-orders" />
        ))}
      </div>
    </div>
  )
}
