import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cancelOrder, listOrders, listPickupPoints } from '../../api/deliveryService.js'
import RestaurantOrdersEmptyState from '../../components/orders/RestaurantOrdersEmptyState.jsx'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import OrderListItem from '../shared/OrderListItem.jsx'
import { canCancelOrder, canEditOrder } from '../../utils/orderStatus.js'

const FILTERS = [
  { key: '', label: 'Все' },
  { key: 'waiting_for_courier', label: 'Ожидают курьера' },
  { key: 'courier_heading_to_pickup', label: 'Курьер едет за заказом' },
  { key: 'courier_delivering', label: 'Курьер везёт заказ' },
  { key: 'completed', label: 'Выполнены' },
  { key: 'cancelled', label: 'Отменены' },
]

export default function RestaurantOrdersPage() {
  const location = useLocation()
  const restaurantId = useRestaurantId()
  const [orders, setOrders] = useState([])
  const [pickupPoints, setPickupPoints] = useState([])
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')

  const pickupNameById = useMemo(
    () => Object.fromEntries(pickupPoints.map((p) => [p.id, p.name])),
    [pickupPoints],
  )

  const reload = useCallback(async () => {
    if (!restaurantId) return
    setLoading(true)
    setMessage('')
    try {
      const params = { scope: 'restaurant', restaurantId }
      if (statusFilter) params.status = statusFilter
      setOrders((await listOrders(params)) || [])
    } catch (e) {
      setMessage(e?.message || 'Ошибка загрузки')
      setOrders([])
    } finally {
      setLoading(false)
    }
  }, [restaurantId, statusFilter])

  useEffect(() => {
    reload()
  }, [reload, location.key])

  useEffect(() => {
    if (!restaurantId) {
      setPickupPoints([])
      return
    }
    listPickupPoints(restaurantId)
      .then((list) => setPickupPoints(list || []))
      .catch(() => setPickupPoints([]))
  }, [restaurantId])

  const onCancel = async (order) => {
    if (!window.confirm(`Отменить заказ №${order.publicNumber}?`)) return
    try {
      await cancelOrder(order.id)
      await reload()
    } catch (e) {
      setMessage(e?.message || 'Не удалось отменить')
    }
  }

  const filteredOut = Boolean(statusFilter)

  return (
    <div className="restaurant-orders-page">
      <header className="restaurant-orders-page__header">
        <div>
          <h1 className="restaurant-orders-page__title">Заказы</h1>
          <p className="restaurant-orders-page__subtitle">Заказы вашего объекта для курьеров</p>
        </div>
        <Link to="/restaurant/orders/new" className="btn restaurant-orders-page__add-btn">
          + Новый заказ
        </Link>
      </header>

      <div className="restaurant-orders-chips" role="group" aria-label="Фильтр по статусу">
        {FILTERS.map((f) => (
          <button
            key={f.key || 'all'}
            type="button"
            className={
              statusFilter === f.key
                ? 'restaurant-orders-chips__btn restaurant-orders-chips__btn--active'
                : 'restaurant-orders-chips__btn'
            }
            onClick={() => setStatusFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {message && (
        <section className="card restaurant-orders-page__error">
          <p>{message}</p>
        </section>
      )}

      {loading && (
        <section className="card restaurant-orders-page__loading">
          <p className="muted">Загрузка заказов…</p>
        </section>
      )}

      {!loading && !message && orders.length === 0 && (
        <RestaurantOrdersEmptyState
          filteredOut={filteredOut}
          onResetFilters={() => setStatusFilter('')}
        />
      )}

      {!loading && orders.length > 0 && (
        <div className="restaurant-orders-list">
          {orders.map((order) => (
            <OrderListItem
              key={order.id}
              order={order}
              basePath="/restaurant/orders"
              pickupPointName={pickupNameById[order.pickupPointId]}
              actions={
                canEditOrder(order) || canCancelOrder(order) ? (
                  <>
                    {canEditOrder(order) && (
                      <Link to={`/restaurant/orders/${order.id}/edit`} className="btn btn-secondary">
                        Редактировать
                      </Link>
                    )}
                    {canCancelOrder(order) && (
                      <button type="button" className="btn btn-secondary" onClick={() => onCancel(order)}>
                        Отменить
                      </button>
                    )}
                  </>
                ) : null
              }
            />
          ))}
        </div>
      )}
    </div>
  )
}
