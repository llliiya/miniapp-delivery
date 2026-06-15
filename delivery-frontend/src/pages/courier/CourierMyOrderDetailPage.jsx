import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { changeOrderStatus, getOrder } from '../../api/deliveryService.js'
import BlockedCourierScreen from '../../components/courier/BlockedCourierScreen.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import OrderDetailView from '../shared/OrderDetailView.jsx'
import {
  isActiveCourierOrder,
  nextCourierStatus,
  nextCourierStatusButtonLabel,
} from '../../utils/orderStatus.js'

export default function CourierMyOrderDetailPage() {
  const { isPendingCourier, isBlockedCourier } = useAuth()
  const { orderId } = useParams()
  const navigate = useNavigate()

  const [order, setOrder] = useState(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState(false)

  const reload = useCallback(async () => {
    if (isPendingCourier || isBlockedCourier) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setOrder(await getOrder(orderId))
    } catch (e) {
      setMessage(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [orderId, isPendingCourier, isBlockedCourier])

  useEffect(() => {
    reload()
  }, [reload])

  const onStatusChange = async () => {
    const next = nextCourierStatus(order)
    if (!next) return
    setMessage('')
    setUpdating(true)
    try {
      const updated = await changeOrderStatus(orderId, next)
      setOrder(updated)
      if (updated.status === 'completed') {
        navigate('/courier/my-orders', { replace: true, state: { tab: 'completed' } })
      }
    } catch (e) {
      setMessage(e?.message || 'Не удалось обновить статус')
    } finally {
      setUpdating(false)
    }
  }

  if (isBlockedCourier) {
    return <BlockedCourierScreen />
  }

  if (isPendingCourier) {
    return <Navigate to="/courier/my-orders" replace />
  }

  if (loading) {
    return (
      <div className="order-detail-page">
        <p className="muted">Загрузка…</p>
      </div>
    )
  }

  if (!order) {
    return (
      <div className="order-detail-page">
        <Link to="/courier/my-orders" className="order-detail-page__back">
          ← Мои заказы
        </Link>
        <p className="text-error">{message || 'Заказ не найден'}</p>
      </div>
    )
  }

  const actionLabel = nextCourierStatusButtonLabel(order)

  return (
    <div className="order-detail-page">
      <Link to="/courier/my-orders" className="order-detail-page__back">
        ← Мои заказы
      </Link>

      <div className="order-detail-page__banner order-detail-page__banner--success">
        <strong>Заказ закреплён за вами</strong>
        <p>Ниже — полный адрес, телефон клиента и комментарий.</p>
      </div>

      <OrderDetailView order={order} showRouteMap />

      {isActiveCourierOrder(order) && actionLabel && (
        <div className="order-detail-page__footer">
          <button type="button" className="btn order-detail-page__btn" disabled={updating} onClick={onStatusChange}>
            {updating ? 'Сохраняем…' : actionLabel}
          </button>
        </div>
      )}

      {message && <p className="text-error">{message}</p>}
    </div>
  )
}
