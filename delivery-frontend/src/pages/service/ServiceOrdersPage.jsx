import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { listOrders, listRestaurants } from '../../api/deliveryService.js'
import ServiceOrderCard from '../../components/orders/ServiceOrderCard.jsx'
import ServiceOrdersEmptyState from '../../components/orders/ServiceOrdersEmptyState.jsx'
import ServiceOrdersFilters from '../../components/orders/ServiceOrdersFilters.jsx'
import ServiceOrdersStats from '../../components/orders/ServiceOrdersStats.jsx'
import { useServiceCity } from '../../context/ServiceCityContext.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import {
  computeServiceOrderStats,
  matchesQuickStatusFilter,
} from '../../utils/orderStats.js'

const OBJECT_SEARCH_THRESHOLD = 6

export default function ServiceOrdersPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const courierServiceId = useCourierServiceId()
  const { cityQueryParam } = useServiceCity()
  const [orders, setOrders] = useState([])
  const [restaurants, setRestaurants] = useState([])
  const [restaurantFilter, setRestaurantFilter] = useState(() => searchParams.get('object') || '')
  const [statusFilter, setStatusFilter] = useState('')
  const [objectSearch, setObjectSearch] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const objectId = searchParams.get('object')
    if (objectId) {
      setRestaurantFilter(objectId)
    }
  }, [searchParams])

  useEffect(() => {
    listRestaurants(
      courierServiceId
        ? { courierServiceId, ...(cityQueryParam ? { city: cityQueryParam } : {}) }
        : undefined,
    )
      .then((list) => setRestaurants(list || []))
      .catch(() => {})
  }, [courierServiceId, cityQueryParam])

  const restaurantNameById = useMemo(
    () => Object.fromEntries(restaurants.map((r) => [r.id, r.name])),
    [restaurants],
  )

  const filteredRestaurants = useMemo(() => {
    const q = objectSearch.trim().toLowerCase()
    if (!q) return restaurants
    return restaurants.filter((r) => r.name?.toLowerCase().includes(q))
  }, [restaurants, objectSearch])

  const reload = useCallback(async () => {
    if (!courierServiceId) return
    setLoading(true)
    setMessage('')
    try {
      const params = { scope: 'service', courierServiceId }
      if (restaurantFilter) params.restaurantId = restaurantFilter
      if (dateFrom) params.dateFrom = new Date(dateFrom).toISOString()
      if (dateTo) {
        const end = new Date(dateTo)
        end.setHours(23, 59, 59, 999)
        params.dateTo = end.toISOString()
      }
      if (cityQueryParam) params.city = cityQueryParam
      setOrders((await listOrders(params)) || [])
    } catch (e) {
      setMessage(e?.message || 'Не удалось загрузить заказы')
      setOrders([])
    } finally {
      setLoading(false)
    }
  }, [courierServiceId, restaurantFilter, dateFrom, dateTo, cityQueryParam])

  useEffect(() => {
    reload()
  }, [reload, location.key])

  const stats = useMemo(() => computeServiceOrderStats(orders), [orders])

  const visibleOrders = useMemo(
    () => orders.filter((order) => matchesQuickStatusFilter(order, statusFilter)),
    [orders, statusFilter],
  )

  const sortedOrders = useMemo(
    () =>
      [...visibleOrders].sort((a, b) => {
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
        return tb - ta
      }),
    [visibleOrders],
  )

  const resetFilters = () => {
    setStatusFilter('')
    setRestaurantFilter('')
    setObjectSearch('')
    setDateFrom('')
    setDateTo('')
  }

  return (
    <div className="service-orders-page">
      <header className="service-orders-page__header">
        <div>
          <h1 className="service-orders-page__title">Заказы</h1>
          <p className="service-orders-page__subtitle">Текущая ситуация по доставкам</p>
        </div>
        <Link
          to={
            restaurantFilter
              ? `/service/orders/new?object=${restaurantFilter}`
              : '/service/orders/new'
          }
          className="btn service-orders-page__new-btn"
        >
          + Новый заказ
        </Link>
      </header>

      <ServiceOrdersStats stats={stats} loading={loading} />

      <ServiceOrdersFilters
        statusFilter={statusFilter}
        onStatusFilter={setStatusFilter}
        filteredRestaurants={filteredRestaurants}
        restaurantFilter={restaurantFilter}
        onRestaurantFilter={setRestaurantFilter}
        objectSearch={objectSearch}
        onObjectSearch={setObjectSearch}
        showObjectSearch={restaurants.length > OBJECT_SEARCH_THRESHOLD}
        dateFrom={dateFrom}
        dateTo={dateTo}
        onDateFrom={setDateFrom}
        onDateTo={setDateTo}
      />

      {message && (
        <section className="card service-orders-page__error">
          <p>{message}</p>
        </section>
      )}

      {loading && (
        <section className="card service-orders-page__loading">
          <p className="muted">Загрузка заказов…</p>
        </section>
      )}

      {!loading && !message && sortedOrders.length === 0 && (
        <ServiceOrdersEmptyState
          filteredOut={orders.length > 0}
          restaurantFilter={restaurantFilter}
          restaurantName={restaurantNameById[restaurantFilter]}
          onResetFilters={resetFilters}
        />
      )}

      {!loading && sortedOrders.length > 0 && (
        <div className="service-orders-list">
          {sortedOrders.map((order) => (
            <ServiceOrderCard
              key={order.id}
              order={order}
              basePath="/service/orders"
              restaurantName={restaurantNameById[order.restaurantId]}
            />
          ))}
        </div>
      )}
    </div>
  )
}
