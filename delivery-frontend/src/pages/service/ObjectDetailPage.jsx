import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { listRestaurants, patchRestaurant } from '../../api/deliveryService.js'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import ObjectChannelsSection from '../../components/objects/ObjectChannelsSection.jsx'
import { loadObjectStats } from '../../utils/loadObjectStats.js'
import {
  objectStatusLabel,
  pluralOrders,
  pluralPickupPoints,
} from '../../utils/objectLabels.js'

export default function ObjectDetailPage() {
  const { restaurantId } = useParams()
  const location = useLocation()
  const courierServiceId = useCourierServiceId()
  const [object, setObject] = useState(null)
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [banner, setBanner] = useState(location.state?.createdMessage || '')
  const [deactivating, setDeactivating] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const restaurants = await listRestaurants()
      const found = (restaurants || []).find((x) => x.id === restaurantId)
      setObject(found || null)
      if (found) {
        setStats(await loadObjectStats(found.id, courierServiceId))
      } else {
        setStats(null)
      }
    } catch (e) {
      setError(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [restaurantId, courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  useEffect(() => {
    if (!location.state?.createdMessage) return
    window.history.replaceState({}, document.title)
  }, [location.state?.createdMessage])

  const onDeactivateObject = async () => {
    const confirmed = window.confirm(
      'Удалить объект?\n\nОбъект будет скрыт из списка, новые заказы по нему создавать нельзя. История заказов сохранится.',
    )
    if (!confirmed) return
    setDeactivating(true)
    setBanner('')
    try {
      await patchRestaurant(restaurantId, { active: false })
      window.location.href = '/service/restaurants'
    } catch (e) {
      setBanner(e?.message || 'Не удалось удалить объект')
    } finally {
      setDeactivating(false)
    }
  }

  if (loading) {
    return (
      <div className="objects-page">
        <section className="card">
          <p className="muted">Загрузка…</p>
        </section>
      </div>
    )
  }

  if (error) {
    return (
      <div className="objects-page">
        <section className="card">
          <p>{error}</p>
        </section>
      </div>
    )
  }

  if (!object) {
    return (
      <div className="objects-page">
        <section className="card">
          <p>Объект не найден</p>
          <Link to="/service/restaurants" className="btn" style={{ marginTop: 12 }}>
            К списку
          </Link>
        </section>
      </div>
    )
  }

  const ordersLink = `/service/orders?object=${object.id}`

  return (
    <div className="objects-page">
      <Link to="/service/restaurants" className="objects-page__back muted">
        ← Объекты
      </Link>

      {banner && (
        <section className="card objects-banner" role="status">
          {banner}
        </section>
      )}

      <header className="objects-detail-hero card">
        <h1 className="objects-detail-hero__name">{object.name}</h1>
        <p className="objects-detail-hero__id muted">ID объекта: {object.publicId ?? '—'}</p>
      </header>

      <section className="card objects-detail-section">
        <h2 className="objects-detail-section__title">Сотрудники объекта</h2>
        <p className="muted">Владельцы и менеджеры объекта с доступом к заказам и настройкам.</p>
        <Link
          to={`/service/restaurants/${object.id}/staff`}
          className="btn btn-secondary objects-detail-section__btn"
        >
          Сотрудники объекта
        </Link>
      </section>

      <section className="card objects-detail-section">
        <h2 className="objects-detail-section__title">Основная информация</h2>
        <dl className="objects-detail-kv">
          <div className="objects-detail-kv__row">
            <dt>Название</dt>
            <dd>{object.name}</dd>
          </div>
          <div className="objects-detail-kv__row">
            <dt>ID объекта</dt>
            <dd>{object.publicId ?? '—'}</dd>
          </div>
          <div className="objects-detail-kv__row">
            <dt>Статус</dt>
            <dd>
              <span
                className={
                  object.active ? 'objects-status objects-status--active' : 'objects-status'
                }
              >
                {objectStatusLabel(object.active)}
              </span>
            </dd>
          </div>
        </dl>
      </section>

      <ObjectChannelsSection restaurantId={object.id} channels={stats?.channels || []} />

      <section className="card objects-detail-section">
        <h2 className="objects-detail-section__title">Точки забора</h2>
        <p className="objects-detail-section__metric">
          {pluralPickupPoints(stats?.pickupCount ?? 0)}
        </p>
        <Link
          to={`/service/restaurants/${object.id}/pickup`}
          className="btn btn-secondary objects-detail-section__btn"
        >
          Управление точками
        </Link>
      </section>

      <section className="card objects-detail-section">
        <h2 className="objects-detail-section__title">Заказы</h2>
        <p className="objects-detail-section__metric">{pluralOrders(stats?.orderCount ?? 0)}</p>
        <Link to={ordersLink} className="btn btn-secondary objects-detail-section__btn">
          Открыть заказы
        </Link>
      </section>

      {object.active !== false && (
        <section className="card objects-detail-section">
          <h2 className="objects-detail-section__title">Удаление объекта</h2>
          <p className="muted">
            Объект будет скрыт из списка. Новые заказы по нему создавать нельзя. История заказов сохранится.
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={deactivating}
            onClick={onDeactivateObject}
          >
            {deactivating ? 'Удаление…' : 'Удалить объект'}
          </button>
        </section>
      )}
    </div>
  )
}
