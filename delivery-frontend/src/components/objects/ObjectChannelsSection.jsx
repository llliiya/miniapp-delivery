import { Link } from 'react-router-dom'
import { pluralConnectedChannels } from '../../utils/objectLabels.js'
import ObjectBoundChannelCard from './ObjectBoundChannelCard.jsx'

export default function ObjectChannelsSection({ restaurantId, channels = [] }) {
  const hasChannels = channels.length > 0

  return (
    <section className="card objects-detail-section object-channels-section">
      <h2 className="objects-detail-section__title">Каналы публикации</h2>

      {hasChannels ? (
        <>
          <p
            className="object-channels-section__summary object-channels-section__summary--connected"
            role="status"
          >
            <span className="object-channels-section__summary-icon" aria-hidden="true">
              🟢
            </span>
            {pluralConnectedChannels(channels.length)}
          </p>
          <div className="object-channels-section__list">
            {channels.map((channel) => (
              <ObjectBoundChannelCard key={channel.id} channel={channel} />
            ))}
          </div>
        </>
      ) : (
        <div className="object-channels-section__empty">
          <p
            className="object-channels-section__summary object-channels-section__summary--disconnected"
            role="status"
          >
            <span className="object-channels-section__summary-icon" aria-hidden="true">
              ⚠
            </span>
            Каналы не подключены
          </p>
          <p className="object-channels-section__empty-text muted">
            Заказы объекта не будут публиковаться для курьеров.
          </p>
        </div>
      )}

      <Link
        to={`/service/restaurants/${restaurantId}/channels`}
        className="btn btn-secondary objects-detail-section__btn object-channels-section__action"
      >
        Настроить каналы
      </Link>
    </section>
  )
}
