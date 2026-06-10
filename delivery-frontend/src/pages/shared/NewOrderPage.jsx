import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { listRestaurants } from '../../api/deliveryService.js'
import OrderForm from '../../components/orders/OrderForm.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { useCourierServiceId, useRestaurantId } from '../../hooks/useActiveOrg.js'
import { INTERFACE_MODES, resolveInterfaceMode } from '../../utils/deliverySession.js'
import {
  getLastSelectedRestaurant,
  saveLastSelectedRestaurant,
} from '../../utils/lastSelectedRestaurant.js'
import { ORDER_CREATED_MESSAGE } from '../../utils/orderPublicationMessages.js'

function resolveInitialRestaurantId({ scopedRestaurants, urlObjectId, courierServiceId }) {
  if (urlObjectId && scopedRestaurants.some((item) => item.id === urlObjectId)) {
    return urlObjectId
  }
  const savedId = getLastSelectedRestaurant(courierServiceId)
  if (savedId && scopedRestaurants.some((item) => item.id === savedId)) {
    return savedId
  }
  if (scopedRestaurants.length === 1) {
    return scopedRestaurants[0].id
  }
  return ''
}

export default function NewOrderPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { activeMembership } = useAuth()
  const fixedRestaurantId = useRestaurantId()
  const courierServiceId = useCourierServiceId()

  const mode = useMemo(() => {
    const resolved = resolveInterfaceMode(activeMembership)
    if (resolved === INTERFACE_MODES.SERVICE) return 'service'
    return 'restaurant'
  }, [activeMembership])

  const routePrefix = location.pathname.startsWith('/service') ? '/service' : '/restaurant'
  const urlObjectId = searchParams.get('object') || ''

  const [restaurants, setRestaurants] = useState([])
  const [restaurantId, setRestaurantId] = useState(mode === 'restaurant' ? fixedRestaurantId || '' : '')
  const [loading, setLoading] = useState(mode === 'service')

  useEffect(() => {
    if (mode !== 'service') {
      setRestaurantId(fixedRestaurantId || '')
      return
    }
    setLoading(true)
    listRestaurants()
      .then((list) => {
        const scoped = (list || []).filter(
          (item) => item.courierServiceId === courierServiceId && item.active !== false,
        )
        setRestaurants(scoped)
        setRestaurantId((current) => {
          if (current && scoped.some((item) => item.id === current)) {
            return current
          }
          return resolveInitialRestaurantId({
            scopedRestaurants: scoped,
            urlObjectId,
            courierServiceId,
          })
        })
      })
      .catch(() => {
        setRestaurants([])
        setRestaurantId('')
      })
      .finally(() => setLoading(false))
  }, [mode, fixedRestaurantId, courierServiceId, urlObjectId])

  const restaurantName = useMemo(() => {
    if (mode === 'restaurant') {
      return activeMembership?.organizationName || ''
    }
    return restaurants.find((item) => item.id === restaurantId)?.name || ''
  }, [mode, activeMembership, restaurants, restaurantId])

  const pickupPointsPath =
    mode === 'service'
      ? restaurantId
        ? `/service/restaurants/${restaurantId}/pickup`
        : '/service/restaurants'
      : '/restaurant/pickup'

  const backTo =
    mode === 'service'
      ? restaurantId
        ? `/service/orders?object=${restaurantId}`
        : '/service/orders'
      : '/restaurant/orders'

  const handleRestaurantChange = (id) => {
    setRestaurantId(id)
    if (courierServiceId && id) {
      saveLastSelectedRestaurant(courierServiceId, id)
    }
  }

  const onCreated = (res) => {
    navigate(`${routePrefix}/orders/${res.order.id}`, {
      replace: true,
      state: { createdMessage: ORDER_CREATED_MESSAGE },
    })
  }

  if (mode === 'restaurant' && !fixedRestaurantId) {
    return <div className="card">Выберите объект в профиле.</div>
  }

  return (
    <div className="restaurant-new-order-page">
      <Link to={backTo} className="restaurant-page__back muted">
        ← Заказы
      </Link>

      <header className="restaurant-new-order-page__header">
        <h1 className="restaurant-new-order-page__title">Новый заказ</h1>
        <p className="restaurant-new-order-page__subtitle">
          {mode === 'service'
            ? 'Создание заказа от имени объекта'
            : 'Заполните данные доставки — заказ создаётся сразу, публикация для курьеров идёт в фоне'}
        </p>
      </header>

      {loading ? (
        <div className="card">
          <p className="muted">Загрузка…</p>
        </div>
      ) : (
        <OrderForm
          mode={mode}
          restaurantId={restaurantId}
          restaurantName={restaurantName}
          restaurants={restaurants}
          onRestaurantChange={handleRestaurantChange}
          pickupPointsPath={pickupPointsPath}
          onCreated={onCreated}
        />
      )}
    </div>
  )
}
