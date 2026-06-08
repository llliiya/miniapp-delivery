import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { cancelOrder, getOrder, patchOrder } from '../../api/deliveryService.js'
import OrderEditForm, { normalizeOrderPhone } from '../../components/orders/OrderEditForm.jsx'
import OrderPublicationFailureBlock from '../../components/orders/OrderPublicationFailureBlock.jsx'
import OrderDetailView from '../shared/OrderDetailView.jsx'
import {
  isOrderPublicationSuccessMessage,
  orderUpdatedMessage,
  shouldShowPublicationFailureBlock,
} from '../../utils/orderPublicationMessages.js'
import { canCancelOrder, canEditOrder, toDatetimeLocalValue } from '../../utils/orderStatus.js'

export default function ServiceOrderDetailPage({ editMode = false }) {
  const { orderId } = useParams()
  const navigate = useNavigate()
  const skipNextLoadRef = useRef(false)
  const [banner, setBanner] = useState('')
  const [order, setOrder] = useState(null)
  const [form, setForm] = useState(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const applyOrder = useCallback((freshOrder) => {
    setOrder(freshOrder)
    setForm({
      deliveryAddress: freshOrder.deliveryAddress,
      deliveryTime: toDatetimeLocalValue(freshOrder.deliveryTime),
      price: String(freshOrder.price),
      customerPhone: freshOrder.customerPhone,
      comment: freshOrder.comment || '',
    })
  }, [])

  const loadOrder = useCallback(async () => {
    if (!orderId) return
    setLoading(true)
    setMessage('')
    try {
      const freshOrder = await getOrder(orderId)
      applyOrder(freshOrder)
    } catch (e) {
      setOrder(null)
      setForm(null)
      setMessage(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [orderId, applyOrder])

  useEffect(() => {
    if (skipNextLoadRef.current) {
      skipNextLoadRef.current = false
      return
    }
    loadOrder()
  }, [orderId, editMode, loadOrder])

  const onSave = async (e) => {
    e.preventDefault()
    setMessage('')
    setSaving(true)
    try {
      const res = await patchOrder(orderId, {
        deliveryAddress: form.deliveryAddress,
        deliveryTime: new Date(form.deliveryTime).toISOString(),
        price: Number(form.price),
        customerPhone: normalizeOrderPhone(form.customerPhone),
        comment: form.comment || null,
      })
      const freshOrder = await getOrder(orderId)
      applyOrder(freshOrder)
      if (freshOrder?.publishedAt) {
        setBanner(orderUpdatedMessage(res?.warnings))
      }
      skipNextLoadRef.current = true
      navigate(`/service/orders/${orderId}`, { replace: true })
    } catch (err) {
      setMessage(err?.message || 'Ошибка сохранения')
    } finally {
      setSaving(false)
    }
  }

  const onCancel = async () => {
    if (!window.confirm('Отменить заказ?')) return
    try {
      await cancelOrder(orderId)
      await loadOrder()
    } catch (e) {
      setMessage(e?.message || 'Не удалось отменить')
    }
  }

  if (loading && !form) return <div style={{ padding: 16 }}>Загрузка…</div>
  if (!order) return <div style={{ padding: 16 }}>{message || 'Заказ не найден'}</div>

  if (editMode) {
    if (!canEditOrder(order)) {
      return (
        <div style={{ padding: 16 }}>
          <p>Заказ нельзя редактировать</p>
          <Link to={`/service/orders/${orderId}`}>Назад</Link>
        </div>
      )
    }
    return (
      <div className="restaurant-order-edit-page" style={{ padding: 16 }}>
        <h2 className="restaurant-order-edit-page__title">Редактирование №{order.publicNumber}</h2>
        <OrderEditForm
          form={form}
          setForm={setForm}
          onSubmit={onSave}
          saving={saving}
          message={message}
          priceLabel="Стоимость доставки"
        />
      </div>
    )
  }

  return (
    <div style={{ padding: 16 }}>
      <Link to="/service/orders">← К списку</Link>
      {banner && (
        <div
          className={`card objects-banner${isOrderPublicationSuccessMessage(banner) ? '' : ' objects-banner--warn'}`}
          style={{ marginTop: 12 }}
          role="status"
        >
          {banner}
        </div>
      )}
      {shouldShowPublicationFailureBlock(order) ? (
        <div style={{ marginTop: 12 }}>
          <OrderPublicationFailureBlock
            order={order}
            onRepublished={async (_updated, bannerMessage) => {
              await loadOrder()
              if (bannerMessage) setBanner(bannerMessage)
            }}
          />
        </div>
      ) : null}
      <div style={{ marginTop: 12 }}>
        <OrderDetailView
          order={order}
          actions={
            canEditOrder(order) || canCancelOrder(order) ? (
              <>
                {canEditOrder(order) && (
                  <Link to={`/service/orders/${orderId}/edit`} className="btn btn-secondary">
                    Редактировать
                  </Link>
                )}
                {canCancelOrder(order) && (
                  <button type="button" className="btn btn-secondary" onClick={onCancel}>
                    Отменить
                  </button>
                )}
              </>
            ) : null
          }
        />
      </div>
      {message && <p style={{ color: 'crimson' }}>{message}</p>}
    </div>
  )
}
