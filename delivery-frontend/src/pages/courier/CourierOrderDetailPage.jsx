import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { assignOrder, getOrder } from '../../api/deliveryService.js'
import { resolvePendingUserId } from '../../utils/pendingCourier.js'
import BlockedCourierScreen from '../../components/courier/BlockedCourierScreen.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import OrderDetailView from '../shared/OrderDetailView.jsx'
import { markDeeplinkHandled } from '../../utils/deeplink.js'
import { isFreeOrder } from '../../utils/orderStatus.js'

export default function CourierOrderDetailPage() {
  const { isPendingCourier, isBlockedCourier, deliveryMe, accountUser } = useAuth()
  const courierId = resolvePendingUserId(deliveryMe, accountUser)
  const { orderId } = useParams()
  const navigate = useNavigate()

  const [order, setOrder] = useState(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [accepting, setAccepting] = useState(false)

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
      setOrder(null)
    } finally {
      setLoading(false)
    }
  }, [orderId, isPendingCourier, isBlockedCourier])

  useEffect(() => {
    reload()
  }, [reload])

  const onAccept = async () => {
    if (!courierId) {
      setMessage('Не удалось определить курьера')
      return
    }
    setMessage('')
    setAccepting(true)
    try {
      await assignOrder(orderId, courierId)
      markDeeplinkHandled()
      navigate(`/courier/my-orders/${orderId}`, { replace: true })
    } catch (e) {
      if (e?.status === 409 || e?.error === 'order_already_taken') {
        setMessage(e?.message || 'Заказ уже взят другим курьером')
        reload()
      } else if (e?.error === 'order_not_available') {
        setMessage(e?.message || 'Заказ недоступен')
      } else {
        setMessage(e?.message || 'Не удалось взять заказ')
      }
    } finally {
      setAccepting(false)
    }
  }

  if (isBlockedCourier) {
    return <BlockedCourierScreen />
  }

  if (isPendingCourier) {
    return <Navigate to="/courier/orders" replace />
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
        <Link to="/courier/orders" className="order-detail-page__back">
          ← К списку
        </Link>
        <p className="text-error">{message || 'Заказ не найден'}</p>
      </div>
    )
  }

  if (order.courierUserId && !isFreeOrder(order)) {
    return <Navigate to={`/courier/my-orders/${orderId}`} replace />
  }

  const canAccept = isFreeOrder(order)

  return (
    <div className="order-detail-page">
      <Link to="/courier/orders" className="order-detail-page__back">
        ← К списку
      </Link>

      <OrderDetailView order={order} showCustomerDetails={false} showRouteMap />

      {canAccept && (
        <div className="order-detail-page__footer">
          <p className="order-detail-page__notice">
            Телефон клиента, полный адрес и комментарий откроются после принятия заказа.
          </p>
          <button type="button" className="btn order-detail-page__btn" disabled={accepting} onClick={onAccept}>
            {accepting ? 'Берём…' : 'Взять заказ'}
          </button>
        </div>
      )}

      {!canAccept && order.status === 'waiting_for_courier' && order.courierUserId && (
        <p className="order-detail-page__alert">Заказ уже взят другим курьером</p>
      )}

      {message && <p className="text-error">{message}</p>}
    </div>
  )
}
