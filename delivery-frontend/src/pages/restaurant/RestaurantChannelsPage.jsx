import { useEffect, useState } from 'react'
import { getRestaurantChannels } from '../../api/deliveryService.js'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import {
  channelPublicationLabel,
  channelStatusLabel,
} from '../../utils/channelLabels.js'

function ChannelStatusBadge({ isActive }) {
  return (
    <span
      className={`objects-status restaurant-channel-card__status${
        isActive ? ' objects-status--active' : ''
      }`}
    >
      {channelStatusLabel(isActive)}
    </span>
  )
}

export default function RestaurantChannelsPage() {
  const restaurantId = useRestaurantId()
  const [channels, setChannels] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!restaurantId) return
    let cancelled = false
    ;(async () => {
      try {
        const data = await getRestaurantChannels(restaurantId)
        if (!cancelled) setChannels(data?.channels || [])
      } catch (e) {
        if (!cancelled) setError(e?.message || 'Ошибка')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [restaurantId])

  if (!restaurantId) {
    return <div className="card">Выберите объект в профиле.</div>
  }

  if (loading) return <div className="card">Загрузка…</div>
  if (error) return <div className="card" style={{ color: '#b91c1c' }}>{error}</div>

  return (
    <div className="restaurant-channels-page">
      <header className="restaurant-channels-page__header">
        <h1 className="restaurant-channels-page__title">Куда публикуются заказы</h1>
        <p className="restaurant-channels-page__subtitle muted">
          Здесь показаны каналы службы доставки, в которые попадают заказы вашего объекта.
        </p>
      </header>

      {channels.length === 0 ? (
        <section className="card objects-empty restaurant-channels-page__empty">
          <h2 className="objects-empty__title">Каналы публикации не подключены</h2>
          <p className="objects-empty__text">
            Заказы будут создаваться, но не будут публиковаться для курьеров. Обратитесь в службу
            доставки, чтобы подключить канал.
          </p>
        </section>
      ) : (
        <div className="restaurant-channels-page__list">
          {channels.map((ch) => (
            <article key={ch.id} className="card restaurant-channel-card">
              <div className="restaurant-channel-card__main">
                <strong className="restaurant-channel-card__name">{ch.name}</strong>
                <p className="restaurant-channel-card__platform muted">
                  {channelPublicationLabel(ch)}
                </p>
              </div>
              <ChannelStatusBadge isActive={ch.isActive} />
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
