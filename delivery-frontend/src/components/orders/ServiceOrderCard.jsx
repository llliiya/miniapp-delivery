import OrderCard from './OrderCard.jsx'

export default function ServiceOrderCard({ order, restaurantName, basePath, pickupPointName }) {
  return (
    <OrderCard
      order={order}
      restaurantName={restaurantName}
      pickupPointName={pickupPointName}
      linkTo={`${basePath}/${order.id}`}
      className="service-order-card"
      showCourier
    />
  )
}
