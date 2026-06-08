import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getRestaurantChannels,
  listChannels,
  listRestaurants,
  replaceRestaurantChannels,
} from '../../api/deliveryService.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { canManageChannelBindings, useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { chatTypeLabel } from '../../utils/channelLabels.js'
import { formatChannelLine } from '../../utils/objectLabels.js'

export default function ObjectChannelsPage() {
  const { restaurantId } = useParams()
  const courierServiceId = useCourierServiceId()
  const { activeMembership } = useAuth()
  const canEdit = canManageChannelBindings(activeMembership)

  const [object, setObject] = useState(null)
  const [allChannels, setAllChannels] = useState([])
  const [boundIds, setBoundIds] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const restaurants = await listRestaurants()
      const found = (restaurants || []).find((x) => x.id === restaurantId)
      setObject(found || null)
      if (courierServiceId) {
        const [all, bound] = await Promise.all([
          listChannels(courierServiceId),
          getRestaurantChannels(restaurantId),
        ])
        setAllChannels((all || []).filter((c) => c.isActive))
        setBoundIds((bound?.channels || []).map((c) => c.id))
      }
    } catch (e) {
      setMessage(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [restaurantId, courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const toggleChannel = (channelId) => {
    setBoundIds((prev) =>
      prev.includes(channelId) ? prev.filter((id) => id !== channelId) : [...prev, channelId],
    )
  }

  const onSave = async () => {
    if (!canEdit) return
    setSaving(true)
    setMessage('')
    try {
      await replaceRestaurantChannels(restaurantId, boundIds)
      setMessage(
        boundIds.length > 0
          ? 'Объект подключен к выбранным каналам публикации'
          : 'Каналы отключены: заказы объекта не будут публиковаться',
      )
      await reload()
    } catch (e) {
      setMessage(e?.message || 'Ошибка сохранения')
    } finally {
      setSaving(false)
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

  const boundChannels = allChannels.filter((c) => boundIds.includes(c.id))

  return (
    <div className="objects-page">
      <Link to={`/service/restaurants/${restaurantId}`} className="objects-page__back muted">
        ← {object.name}
      </Link>

      <h1 className="objects-page__title">Каналы публикации</h1>
      <p className="objects-page__lead muted">
        Выберите один или несколько каналов публикации. После сохранения объект будет подключен к
        ним, и новые заказы начнут публиковаться для курьеров.
      </p>

      {boundChannels.length > 0 && (
        <section className="card objects-detail-section">
          <h2 className="objects-detail-section__title">Сейчас привязано</h2>
          <ul className="objects-channel-lines">
            {boundChannels.map((ch) => (
              <li key={ch.id}>{formatChannelLine(ch)}</li>
            ))}
          </ul>
        </section>
      )}

      <section className="card objects-detail-section">
        <h2 className="objects-detail-section__title">Доступные каналы</h2>
        {!canEdit && (
          <p className="muted">Только просмотр. Изменять привязки может курьерская служба.</p>
        )}
        {allChannels.length === 0 ? (
          <p className="muted">Нет активных каналов службы. Добавьте их в разделе «Каналы».</p>
        ) : (
          <div className="objects-channel-picker">
            {allChannels.map((ch) => (
              <label key={ch.id} className="objects-channel-picker__item">
                <input
                  type="checkbox"
                  disabled={!canEdit}
                  checked={boundIds.includes(ch.id)}
                  onChange={() => toggleChannel(ch.id)}
                />
                <span>
                  <strong>{ch.name}</strong>
                  <span className="muted objects-channel-picker__sub">
                    {chatTypeLabel(ch.chatType)}
                    {ch.city ? ` · ${ch.city}` : ''}
                    {canEdit && ch.externalId ? ` · ${ch.externalId}` : ''}
                  </span>
                </span>
              </label>
            ))}
          </div>
        )}
        {canEdit && allChannels.length > 0 && (
          <button
            type="button"
            className="btn"
            style={{ marginTop: 14, width: '100%' }}
            disabled={saving}
            onClick={onSave}
          >
            {saving ? 'Сохранение…' : 'Сохранить'}
          </button>
        )}
        {message && <p className="objects-form__hint" style={{ marginTop: 10 }}>{message}</p>}
      </section>
    </div>
  )
}
