import OrderCard from '../../components/orders/OrderCard.jsx'

export default function OrderListItem({
  order,
  basePath,
  showRestaurantName = false,
  pickupPointName,
  actions,
  showCustomerDetails = true,
}) {
  const detailPath = `${basePath}/${order.id}`
  const hasActions = Boolean(actions)

  return (
    <OrderCard
      order={order}
      pickupPointName={pickupPointName}
      restaurantName={showRestaurantName ? order.restaurantName : undefined}
      linkTo={hasActions ? undefined : detailPath}
      detailLink={hasActions ? detailPath : undefined}
      showCustomerDetails={showCustomerDetails}
      actions={hasActions ? actions : undefined}
      compact={!hasActions}
    />
  )
}
