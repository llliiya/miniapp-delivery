import { Link, useNavigate } from 'react-router-dom'
import CreateOrderForm from '../../components/orders/CreateOrderForm.jsx'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import { orderCreatedMessage } from '../../utils/orderPublicationMessages.js'

export default function RestaurantNewOrderPage() {
  const restaurantId = useRestaurantId()
  const navigate = useNavigate()

  const onCreated = (res) => {
    const createdMessage = orderCreatedMessage(res?.warnings)
    navigate(`/restaurant/orders/${res.order.id}`, {
      replace: true,
      state: { createdMessage },
    })
  }

  if (!restaurantId) {
    return <div className="card">Выберите объект в профиле.</div>
  }

  return (
    <div className="restaurant-new-order-page">
      <Link to="/restaurant/orders" className="restaurant-page__back muted">
        ← Заказы
      </Link>

      <header className="restaurant-new-order-page__header">
        <h1 className="restaurant-new-order-page__title">Новый заказ</h1>
        <p className="restaurant-new-order-page__subtitle">
          Заполните данные доставки, чтобы опубликовать заказ для курьеров
        </p>
      </header>

      <CreateOrderForm
        restaurantId={restaurantId}
        pickupPointsPath="/restaurant/pickup"
        onCreated={onCreated}
      />
    </div>
  )
}
