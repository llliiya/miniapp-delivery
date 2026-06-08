import { formatDeliveryAddressDetails, formatDeliveryAddressMain } from '../utils/orderDisplay.js'

/**
 * @param {{ order: object, className?: string }} props
 */
export default function DeliveryAddressText({ order, className = '' }) {
  const main = formatDeliveryAddressMain(order)
  const details = formatDeliveryAddressDetails(order)
  if (!main && !details) return null

  return (
    <span className={className}>
      {main ? <span className="delivery-address__main">{main}</span> : null}
      {details ? (
        <>
          {main ? <br /> : null}
          <span className="delivery-address__details muted">{details}</span>
        </>
      ) : null}
    </span>
  )
}
