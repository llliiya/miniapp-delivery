import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { listRestaurants } from '../../api/deliveryService.js'
import CreateOrderForm from '../../components/orders/CreateOrderForm.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { orderCreatedMessage } from '../../utils/orderPublicationMessages.js'

export default function ServiceNewOrderPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const courierServiceId = useCourierServiceId()
  const initialObjectId = searchParams.get('object') || ''

  const [restaurants, setRestaurants] = useState([])
  const [restaurantId, setRestaurantId] = useState(initialObjectId)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listRestaurants()
      .then((list) => {
        const scoped = (list || []).filter((r) => r.courierServiceId === courierServiceId && r.active !== false)
        setRestaurants(scoped)
        if (initialObjectId && scoped.some((r) => r.id === initialObjectId)) {
          setRestaurantId(initialObjectId)
        } else if (!restaurantId && scoped.length === 1) {
          setRestaurantId(scoped[0].id)
        }
      })
      .catch(() => setRestaurants([]))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial object from URL once
  }, [courierServiceId, initialObjectId])

  const restaurantName = useMemo(
    () => restaurants.find((r) => r.id === restaurantId)?.name,
    [restaurants, restaurantId],
  )

  const pickupPointsPath = restaurantId
    ? `/service/restaurants/${restaurantId}/pickup`
    : '/service/restaurants'

  const onCreated = (res) => {
    const createdMessage = orderCreatedMessage(res?.warnings)
    navigate(`/service/orders/${res.order.id}`, {
      replace: true,
      state: { createdMessage },
    })
  }

  const backTo = restaurantId ? `/service/orders?object=${restaurantId}` : '/service/orders'

  return (
    <div className="restaurant-new-order-page service-new-order-page">
      <Link to={backTo} className="restaurant-page__back muted">
        ← Заказы
      </Link>

      <header className="restaurant-new-order-page__header">
        <h1 className="restaurant-new-order-page__title">Новый заказ</h1>
        <p className="restaurant-new-order-page__subtitle">
          Создание заказа от имени объекта
        </p>
      </header>

      {loading ? (
        <div className="card">
          <p className="muted">Загрузка…</p>
        </div>
      ) : (
        <CreateOrderForm
          restaurantId={restaurantId}
          restaurantName={restaurantName}
          showRestaurantSelect={!initialObjectId}
          restaurants={restaurants}
          onRestaurantChange={setRestaurantId}
          pickupPointsPath={pickupPointsPath}
          onCreated={onCreated}
        />
      )}
    </div>
  )
}
