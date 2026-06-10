import OrderCard from '../../components/orders/OrderCard.jsx'
import OrderRouteMap from '../../components/orders/OrderRouteMap.jsx'
import { formatOrderDateTime } from '../../utils/formatOrderDate.js'
import { formatOrderCreatorLabel } from '../../utils/orderCreatorLabel.js'
import {
  isPublicationInProgress,
  publicationStatusLabel,
  PUBLICATION_STATUS,
} from '../../utils/publicationStatus.js'

function fmtInstant(iso) {
  if (!iso) return '—'
  return formatOrderDateTime(iso)
}

export default function OrderDetailView({
  order,
  pickupPoints,
  actions,
  showCustomerDetails = true,
  showRouteMap = false,
}) {
  const pickupPointName = pickupPoints?.find((p) => p.id === order.pickupPointId)?.name
  const creatorLabel = formatOrderCreatorLabel(order)

  const extra = (
    <>
      {showRouteMap ? (
        <div className="order-card__route">
          <div className="order-card__route-title">Маршрут</div>
          <OrderRouteMap order={order} />
        </div>
      ) : null}
      <div className="order-card__meta">
      {creatorLabel ? (
        <div className="order-card__block">
          <div className="order-card__label">Кем создан:</div>
          <div className="order-card__value">{creatorLabel}</div>
        </div>
      ) : null}
      {showCustomerDetails && order.comment ? (
        <div className="order-card__block">
          <div className="order-card__label">Комментарий:</div>
          <div className="order-card__value">{order.comment}</div>
        </div>
      ) : null}
      <div className="order-card__block">
        <div className="order-card__label">Создан:</div>
        <div className="order-card__value">{fmtInstant(order.createdAt)}</div>
      </div>
      {order.publicationStatus ? (
        <div className="order-card__block">
          <div className="order-card__label">Публикация:</div>
          <div
            className={`order-card__value${
              isPublicationInProgress(order.publicationStatus)
                ? ' order-card__value--pending'
                : order.publicationStatus === PUBLICATION_STATUS.FAILED
                  ? ' order-card__value--warn'
                  : ''
            }`}
          >
            {publicationStatusLabel(order.publicationStatus)}
            {order.publicationStatus === PUBLICATION_STATUS.PUBLISHED && order.publishedAt
              ? ` · ${fmtInstant(order.publishedAt)}`
              : null}
          </div>
        </div>
      ) : null}
      {order.acceptedAt ? (
        <div className="order-card__block">
          <div className="order-card__label">Принят:</div>
          <div className="order-card__value">{fmtInstant(order.acceptedAt)}</div>
        </div>
      ) : null}
      {order.completedAt ? (
        <div className="order-card__block">
          <div className="order-card__label">Выполнен:</div>
          <div className="order-card__value">{fmtInstant(order.completedAt)}</div>
        </div>
      ) : null}
      </div>
    </>
  )

  return (
    <OrderCard
      order={order}
      pickupPointName={pickupPointName}
      showCourier
      showCustomerDetails={showCustomerDetails}
      extra={extra}
      actions={actions}
      className="order-card--detail"
    />
  )
}
