import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  createPickupPoint,
  deletePickupPoint,
  listPickupPoints,
  patchPickupPoint,
} from '../../api/deliveryService.js'
import AddressSuggestInput from '../../components/AddressSuggestInput.jsx'
import ShortAddressText from '../../components/ShortAddressText.jsx'
import PhoneInput from '../../components/PhoneInput.jsx'
import PickupPointMap from '../../components/PickupPointMap.jsx'
import { getGeocoderProviderLabel } from '../../services/geocoding.js'
import { formatShortAddress } from '../../utils/formatShortAddress.js'
import EmptyState, { EmptyStateIcon } from '../../components/EmptyState.jsx'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import './PickupPointsPage.css'

const YANDEX_KEY = (import.meta.env.VITE_YANDEX_MAPS_API_KEY || '').trim()

function emptyForm() {
  return {
    name: '',
    address: '',
    addressSelected: false,
    lat: null,
    lon: null,
    phone: '',
    comment: '',
    isDefault: false,
  }
}

function formFromPoint(p) {
  const hasCoords = p.lat != null && p.lon != null && Number.isFinite(Number(p.lat)) && Number.isFinite(Number(p.lon))
  return {
    name: p.name || '',
    address: formatShortAddress(p.address || '') || p.address || '',
    addressSelected: hasCoords,
    lat: hasCoords ? Number(p.lat) : null,
    lon: hasCoords ? Number(p.lon) : null,
    phone: p.phone || '',
    comment: p.comment || '',
    isDefault: Boolean(p.isDefault),
  }
}

export default function PickupPointsPage() {
  const { restaurantId: routeRestaurantId } = useParams()
  const membershipRestaurantId = useRestaurantId()
  const restaurantId = routeRestaurantId || membershipRestaurantId
  const serviceContext = Boolean(routeRestaurantId)
  const [points, setPoints] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [form, setForm] = useState(emptyForm)
  const [editId, setEditId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)

  const mapPosition = useMemo(() => {
    if (form.addressSelected && form.lat != null && form.lon != null) {
      return { lat: form.lat, lon: form.lon }
    }
    return null
  }, [form.addressSelected, form.lat, form.lon])

  const canSave = Boolean(form.name.trim()) && form.addressSelected && !saving

  const reload = useCallback(async () => {
    if (!restaurantId) return
    setLoading(true)
    try {
      setPoints((await listPickupPoints(restaurantId)) || [])
    } catch (e) {
      setMessage(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [restaurantId])

  useEffect(() => {
    reload()
  }, [reload])

  const openCreate = () => {
    setEditId(null)
    setForm(emptyForm())
    setShowForm(true)
    setMessage('')
  }

  const openEdit = (p) => {
    setEditId(p.id)
    setForm(formFromPoint(p))
    setShowForm(true)
    setMessage('')
  }

  const handleAddressResolved = ({ address, shortAddress, lat, lon }) => {
    setForm((f) => ({
      ...f,
      address: shortAddress || formatShortAddress(address),
      addressSelected: true,
      lat,
      lon,
    }))
  }

  const handleAddressClearSelection = () => {
    setForm((f) => ({
      ...f,
      addressSelected: false,
      lat: null,
      lon: null,
    }))
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    if (!canSave) return
    setMessage('')
    setSaving(true)
    const body = {
      name: form.name.trim(),
      address: form.address.trim(),
      lat: form.lat,
      lon: form.lon,
      phone: form.phone.trim() || null,
      comment: form.comment.trim() || null,
      isDefault: form.isDefault,
    }
    try {
      if (editId) {
        await patchPickupPoint(editId, body)
      } else {
        await createPickupPoint(restaurantId, body)
      }
      setShowForm(false)
      await reload()
    } catch (err) {
      setMessage(err?.message || 'Ошибка сохранения')
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (id) => {
    if (!window.confirm('Удалить точку забора?')) return
    try {
      await deletePickupPoint(id)
      await reload()
    } catch (err) {
      setMessage(err?.message || 'Ошибка удаления')
    }
  }

  if (!restaurantId) {
    return <div className="card">Выберите объект в профиле.</div>
  }

  return (
    <div className={`pickup-points-page${serviceContext ? ' objects-page' : ''}`}>
      {serviceContext && (
        <Link to={`/service/restaurants/${restaurantId}`} className="objects-page__back muted">
          ← К карточке объекта
        </Link>
      )}
      <header className="pickup-points-page__header">
        <h1 className="pickup-points-page__title">Точки забора</h1>
        {!showForm && (
          <button type="button" className="btn pickup-points-page__add-btn" onClick={openCreate}>
            Добавить
          </button>
        )}
      </header>

      {message && <p className="card" style={{ color: '#b91c1c' }}>{message}</p>}

      {showForm && (
        <form className="pickup-points-form-card" onSubmit={onSubmit}>
          <h3>{editId ? 'Редактировать точку' : 'Новая точка забора'}</h3>
          <div className="pickup-points-form-layout">
            <div className="pickup-points-form-fields">
              <label>
                Название
                <input
                  className="input"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="Кухня, зал, склад…"
                  required
                />
              </label>

              <label>
                Адрес
                <AddressSuggestInput
                  value={form.address}
                  onChange={(address) => setForm((f) => ({ ...f, address }))}
                  onResolved={handleAddressResolved}
                  onClearSelection={handleAddressClearSelection}
                />
                <p className="pickup-points-form-hint">
                  Начните вводить адрес и выберите вариант из списка ({getGeocoderProviderLabel()}).
                </p>
              </label>

              <label>
                Телефон точки <span className="muted">(необязательно)</span>
                <PhoneInput
                  className="input"
                  value={form.phone}
                  onChange={(phone) => setForm((f) => ({ ...f, phone }))}
                  placeholder="+7 (999) 123-45-67"
                />
                <p className="pickup-points-form-hint">Для связи курьера с точкой.</p>
              </label>

              <label>
                Комментарий <span className="muted">(необязательно)</span>
                <textarea
                  className="input"
                  value={form.comment}
                  onChange={(e) => setForm({ ...form, comment: e.target.value })}
                  placeholder="Вход со двора, позвонить охране, 3 этаж…"
                  rows={3}
                />
              </label>

              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={form.isDefault}
                  onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
                />
                <span>
                  <strong>Сделать основной точкой забора</strong>
                  <p className="pickup-points-form-hint" style={{ marginTop: 4 }}>
                    По умолчанию новые заказы будут создаваться с этой точки.
                  </p>
                </span>
              </label>

              <div className="pickup-points-form-actions">
                <button type="submit" className="btn" disabled={!canSave}>
                  {saving ? 'Сохранение…' : 'Сохранить точку'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowForm(false)}
                  disabled={saving}
                >
                  Отмена
                </button>
              </div>
            </div>

            <div className="pickup-points-form-map">
              <PickupPointMap apiKey={YANDEX_KEY} position={mapPosition} />
            </div>
          </div>
        </form>
      )}

      {loading ? (
        <div className="card">Загрузка…</div>
      ) : points.length === 0 ? (
        <EmptyState
          icon={
            <EmptyStateIcon>
              <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M24 8c6 8 12 12 12 18a12 12 0 1 1-24 0c0-6 6-10 12-18z" />
                <circle cx="24" cy="28" r="4" />
              </svg>
            </EmptyStateIcon>
          }
          title="Точек пока нет"
          description="Добавьте первую точку выдачи — она станет точкой по умолчанию."
        />
      ) : (
        points.map((p) => (
          <div key={p.id} className="card">
            <strong>{p.name}</strong>
            {p.isDefault && <span className="badge">Основная</span>}
            <p className="pickup-point-card__meta">
              <ShortAddressText address={p.address} />
            </p>
            {p.phone ? (
              <p className="pickup-point-card__meta">Телефон: {p.phone}</p>
            ) : null}
            {p.comment ? (
              <p className="pickup-point-card__meta">{p.comment}</p>
            ) : null}
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => openEdit(p)}>
                Редактировать
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => onDelete(p.id)}>
                Удалить
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  )
}
