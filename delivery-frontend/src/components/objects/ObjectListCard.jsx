import { Link } from 'react-router-dom'
import { objectStatusLabel } from '../../utils/objectLabels.js'

export default function ObjectListCard({ object, stats }) {
  const status = objectStatusLabel(object.active)
  const channelCount = stats?.channelCount ?? 0
  const pickupCount = stats?.pickupCount ?? 0

  return (
    <article className="card objects-list-card">
      <h3 className="objects-list-card__name">{object.name}</h3>
      <dl className="objects-list-card__meta">
        <div className="objects-list-card__row">
          <dt>ID объекта</dt>
          <dd>{object.publicId ?? '—'}</dd>
        </div>
        <div className="objects-list-card__row">
          <dt>Каналов публикации</dt>
          <dd>{channelCount}</dd>
        </div>
        <div className="objects-list-card__row">
          <dt>Точек забора</dt>
          <dd>{pickupCount}</dd>
        </div>
        <div className="objects-list-card__row">
          <dt>Статус</dt>
          <dd>
            <span
              className={
                object.active ? 'objects-status objects-status--active' : 'objects-status'
              }
            >
              {status}
            </span>
          </dd>
        </div>
      </dl>
      <Link to={`/service/restaurants/${object.id}`} className="btn objects-list-card__action">
        Подробнее
      </Link>
    </article>
  )
}
