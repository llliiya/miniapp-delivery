import { Link } from 'react-router-dom'
import { formatOrderDeliveryTime, formatOrderPrice } from '../../utils/formatOrderDate.js'
import { formatShortAddress } from '../../utils/formatShortAddress.js'
import {
  formatCourierLine,
  formatDeliveryAddressDetails,
  formatDeliveryAddressMain,
} from '../../utils/orderDisplay.js'
import { formatPhoneDisplay } from '../../utils/phone.js'
import { orderStatusBadgeClass, orderStatusLabel } from '../../utils/orderStatus.js'

export default function OrderCard({
  order,
  pickupPointName,
  restaurantName,
  actions,
  linkTo,
  detailLink,
  className = '',
  compact = false,
  showPhone = true,
  showCustomerDetails = true,
  showTime = true,
  showCourier = false,
  extra,
}) {
  const deliveryDetails = showCustomerDetails ? formatDeliveryAddressDetails(order) : null
  const showCustomerPhone = showPhone && showCustomerDetails
  const courierLine = formatCourierLine(order)
  const orderTitle = `Заказ №${order.publicNumber}`

  const body = (
    <>
      <div className="order-card__head">
        {linkTo ? (
          <h3 className="order-card__number">{orderTitle}</h3>
        ) : detailLink ? (
          <Link to={detailLink} className="order-card__number order-card__number-link">
            {orderTitle}
          </Link>
        ) : (
          <h3 className="order-card__number">{orderTitle}</h3>
        )}
        <span className={orderStatusBadgeClass(order.status)}>{orderStatusLabel(order.status)}</span>
      </div>

      {restaurantName ? <p className="order-card__restaurant">{restaurantName}</p> : null}

      <div className="order-card__block">
        <div className="order-card__label">Забрать:</div>
        <div className="order-card__value">
          {pickupPointName ? <div className="order-card__line">{pickupPointName}</div> : null}
          <div className="order-card__line">{formatShortAddress(order.pickupAddress) || '—'}</div>
        </div>
      </div>

      <div className="order-card__block">
        <div className="order-card__label">Доставить:</div>
        <div className="order-card__value">
          <div className="order-card__line">{formatDeliveryAddressMain(order) || '—'}</div>
          {deliveryDetails ? (
            <div className="order-card__line order-card__line--muted">{deliveryDetails}</div>
          ) : null}
        </div>
      </div>

      {compact && showTime ? (
        <div className="order-card__footer-row">
          <div className="order-card__block order-card__block--inline">
            <div className="order-card__label">Стоимость:</div>
            <div className="order-card__value order-card__value--strong">{formatOrderPrice(order.price)}</div>
          </div>
          <div className="order-card__block order-card__block--inline">
            <div className="order-card__label">Время:</div>
            <div className="order-card__value">{formatOrderDeliveryTime(order)}</div>
          </div>
        </div>
      ) : (
        <>
          <div className="order-card__block">
            <div className="order-card__label">Стоимость:</div>
            <div className="order-card__value order-card__value--strong">{formatOrderPrice(order.price)}</div>
          </div>
          {showTime ? (
            <div className="order-card__block">
              <div className="order-card__label">Время:</div>
              <div className="order-card__value">{formatOrderDeliveryTime(order)}</div>
            </div>
          ) : null}
        </>
      )}

      {showCustomerPhone ? (
        <div className="order-card__block">
          <div className="order-card__label">Телефон клиента:</div>
          <div className="order-card__value">{formatPhoneDisplay(order.customerPhone)}</div>
        </div>
      ) : null}

      {showCourier && courierLine ? (
        <p className="order-card__courier muted">{courierLine}</p>
      ) : null}

      {extra}

      {actions ? <div className="order-card__actions">{actions}</div> : null}
    </>
  )

  const classNames = `card order-card${compact ? ' order-card--compact' : ''} ${className}`.trim()

  if (linkTo) {
    return (
      <Link to={linkTo} className={`${classNames} card-link`}>
        {body}
      </Link>
    )
  }

  return <div className={classNames}>{body}</div>
}
