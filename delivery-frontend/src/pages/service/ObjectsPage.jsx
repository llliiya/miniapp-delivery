import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listRestaurants } from '../../api/deliveryService.js'
import ObjectEmptyState from '../../components/objects/ObjectEmptyState.jsx'
import ObjectListCard from '../../components/objects/ObjectListCard.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { loadObjectStats } from '../../utils/loadObjectStats.js'

export default function ObjectsPage() {
  const courierServiceId = useCourierServiceId()
  const [objects, setObjects] = useState([])
  const [statsById, setStatsById] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const data = await listRestaurants()
        const list = (data || []).filter(
          (o) => !courierServiceId || o.courierServiceId === courierServiceId,
        )
        if (cancelled) return
        setObjects(list)

        if (list.length > 0) {
          const entries = await Promise.all(
            list.map(async (o) => {
              const stats = await loadObjectStats(o.id, courierServiceId)
              return [o.id, stats]
            }),
          )
          if (!cancelled) {
            setStatsById(Object.fromEntries(entries))
          }
        } else {
          setStatsById({})
        }
      } catch (e) {
        if (!cancelled) setError(e?.message || 'Не удалось загрузить объекты')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [courierServiceId])

  return (
    <div className="objects-page">
      <header className="objects-page__header">
        <div>
          <h1 className="objects-page__title">Объекты</h1>
          <p className="objects-page__subtitle">Мои объекты</p>
        </div>
        <Link to="/service/restaurants/new" className="btn objects-page__add-btn">
          + Добавить объект
        </Link>
      </header>

      {loading && (
        <section className="card objects-page__loading">
          <p className="muted">Загрузка объектов…</p>
        </section>
      )}

      {!loading && error && (
        <section className="card objects-page__error">
          <p>{error}</p>
        </section>
      )}

      {!loading && !error && objects.length === 0 && <ObjectEmptyState />}

      {!loading && !error && objects.length > 0 && (
        <div className="objects-page__list">
          {objects.map((o) => (
            <ObjectListCard key={o.id} object={o} stats={statsById[o.id]} />
          ))}
        </div>
      )}
    </div>
  )
}
