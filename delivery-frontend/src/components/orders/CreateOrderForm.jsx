import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { createOrder, listPickupPoints } from '../../api/deliveryService.js'
import AddressSuggestInput from '../AddressSuggestInput.jsx'
import PhoneInput from '../PhoneInput.jsx'
import { formatShortAddress } from '../../utils/formatShortAddress.js'

const DELIVERY_MODES = {
  ASAP: 'asap',
  SCHEDULED: 'scheduled',
}

const emptyForm = {
  pickupPointId: '',
  deliveryAddress: '',
  deliveryAddressFull: '',
  deliveryAddressSelected: false,
  deliveryLat: null,
  deliveryLon: null,
  apartment: '',
  entrance: '',
  deliveryMode: DELIVERY_MODES.ASAP,
  deliveryTime: '',
  price: '',
  customerPhone: '',
  comment: '',
}

/**
 * @param {{
 *   restaurantId: string
 *   restaurantName?: string
 *   showRestaurantSelect?: boolean
 *   restaurants?: Array<{ id: string, name: string }>
 *   onRestaurantChange?: (id: string) => void
 *   pickupPointsPath: string
 *   onCreated: (res: { order: { id: string }, warnings?: string[] }) => void
 * }} props
 */
export default function CreateOrderForm({
  restaurantId,
  restaurantName,
  showRestaurantSelect = false,
  restaurants = [],
  onRestaurantChange,
  pickupPointsPath,
  onCreated,
}) {
  const [points, setPoints] = useState([])
  const [pointsLoading, setPointsLoading] = useState(true)
  const [form, setForm] = useState(emptyForm)
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)

  const loadPoints = useCallback(async () => {
    if (!restaurantId) {
      setPoints([])
      setPointsLoading(false)
      return
    }
    setPointsLoading(true)
    try {
      const list = (await listPickupPoints(restaurantId)) || []
      setPoints(list)
      const def = list.find((p) => p.isDefault) || list[0]
      if (def) {
        setForm((f) => ({ ...f, pickupPointId: def.id }))
      } else {
        setForm((f) => ({ ...f, pickupPointId: '' }))
      }
    } catch (e) {
      setMessage(e?.message || 'Не удалось загрузить точки забора')
      setPoints([])
    } finally {
      setPointsLoading(false)
    }
  }, [restaurantId])

  useEffect(() => {
    loadPoints()
  }, [loadPoints])

  const canSubmit =
    Boolean(restaurantId) &&
    Boolean(form.pickupPointId) &&
    points.length > 0 &&
    form.deliveryAddressSelected

  const handleDeliveryAddressResolved = ({ address, shortAddress, lat, lon }) => {
    const display = shortAddress || formatShortAddress(address)
    setForm((f) => ({
      ...f,
      deliveryAddress: display,
      deliveryAddressFull: address,
      deliveryAddressSelected: true,
      deliveryLat: lat,
      deliveryLon: lon,
    }))
  }

  const handleDeliveryAddressClearSelection = () => {
    setForm((f) => ({
      ...f,
      deliveryAddressSelected: false,
      deliveryAddressFull: '',
      deliveryLat: null,
      deliveryLon: null,
    }))
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setMessage('')
    if (!restaurantId) {
      setMessage('Выберите объект')
      return
    }
    if (!form.pickupPointId) {
      setMessage('Выберите точку забора')
      return
    }
    if (!form.deliveryAddressSelected) {
      setMessage('Выберите адрес доставки из подсказок')
      return
    }
    if (form.deliveryMode === DELIVERY_MODES.SCHEDULED && !form.deliveryTime) {
      setMessage('Укажите дату и время доставки')
      return
    }
    setSaving(true)
    try {
      const deliveryTime =
        form.deliveryMode === DELIVERY_MODES.ASAP
          ? new Date().toISOString()
          : new Date(form.deliveryTime).toISOString()
      const res = await createOrder({
        restaurantId,
        pickupPointId: form.pickupPointId,
        deliveryAddress: form.deliveryAddress,
        deliveryAddressFull: form.deliveryAddressFull || null,
        deliveryLat: form.deliveryLat,
        deliveryLon: form.deliveryLon,
        apartment: form.apartment.trim() || null,
        entrance: form.entrance.trim() || null,
        deliveryTime,
        price: Number(form.price),
        customerPhone: form.customerPhone,
        comment: form.comment.trim() || null,
      })
      onCreated(res)
    } catch (err) {
      setMessage(err?.message || 'Ошибка создания')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="card restaurant-order-form">
      {(showRestaurantSelect || restaurantName) && (
        <section className="restaurant-order-form__section">
          <h2 className="restaurant-order-form__section-title">Объект</h2>
          {showRestaurantSelect ? (
            <label className="restaurant-order-form__label">
              Объект
              <select
                className="input"
                value={restaurantId || ''}
                onChange={(e) => onRestaurantChange?.(e.target.value)}
                required
              >
                <option value="">Выберите объект</option>
                {restaurants.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.name}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p className="restaurant-order-form__object-name">{restaurantName}</p>
          )}
        </section>
      )}

      <section className="restaurant-order-form__section">
        <h2 className="restaurant-order-form__section-title">Откуда забрать</h2>
        {!restaurantId ? (
          <p className="muted">Сначала выберите объект</p>
        ) : pointsLoading ? (
          <p className="muted">Загрузка точек…</p>
        ) : points.length === 0 ? (
          <div className="restaurant-order-form__empty">
            <p className="restaurant-order-form__empty-title">У объекта нет точек забора</p>
            <p className="restaurant-order-form__empty-text muted">
              Добавьте точку забора в карточке объекта, чтобы создать заказ.
            </p>
            <Link to={pickupPointsPath} className="btn restaurant-order-form__empty-btn">
              Перейти к точкам
            </Link>
          </div>
        ) : (
          <label className="restaurant-order-form__label">
            Точка забора
            <select
              className="input"
              value={form.pickupPointId}
              onChange={(e) => setForm({ ...form, pickupPointId: e.target.value })}
              required
            >
              <option value="">Выберите точку</option>
              {points.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} — {formatShortAddress(p.address)}
                </option>
              ))}
            </select>
          </label>
        )}
      </section>

      <section className="restaurant-order-form__section">
        <h2 className="restaurant-order-form__section-title">Куда доставить</h2>
        <label className="restaurant-order-form__label">
          Адрес доставки
          <AddressSuggestInput
            value={form.deliveryAddress}
            onChange={(deliveryAddress) => setForm((f) => ({ ...f, deliveryAddress }))}
            onResolved={handleDeliveryAddressResolved}
            onClearSelection={handleDeliveryAddressClearSelection}
            disabled={!restaurantId}
            placeholder="Улица, дом"
          />
          <span className="restaurant-order-form__hint muted">
            Выберите адрес из списка, чтобы курьер получил точную точку доставки
          </span>
          {form.deliveryAddress.trim() && !form.deliveryAddressSelected ? (
            <span className="restaurant-order-form__hint" style={{ color: '#b45309' }}>
              Выберите адрес из списка
            </span>
          ) : null}
        </label>
        <label className="restaurant-order-form__label">
          Квартира
          <input
            className="input"
            value={form.apartment}
            onChange={(e) => setForm((f) => ({ ...f, apartment: e.target.value }))}
            placeholder="Например: 25"
            disabled={!restaurantId}
          />
        </label>
        <label className="restaurant-order-form__label">
          Подъезд
          <input
            className="input"
            value={form.entrance}
            onChange={(e) => setForm((f) => ({ ...f, entrance: e.target.value }))}
            placeholder="Например: 3, 5 этаж, домофон 25"
            disabled={!restaurantId}
          />
        </label>
        <label className="restaurant-order-form__label">
          Телефон получателя
          <PhoneInput
            className="input"
            value={form.customerPhone}
            onChange={(phone) => setForm((f) => ({ ...f, customerPhone: phone }))}
            required
            placeholder="+7 (999) 123-45-67"
          />
        </label>
        <label className="restaurant-order-form__label">
          Комментарий
          <textarea
            className="input restaurant-order-form__textarea"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            rows={3}
            placeholder="Например: позвонить за 5 минут, оставить у двери"
            disabled={!restaurantId}
          />
        </label>
      </section>

      <section className="restaurant-order-form__section">
        <h2 className="restaurant-order-form__section-title">Когда доставить</h2>
        <div className="restaurant-order-form__label">
          Как доставить?
          <div className="restaurant-order-form__delivery-mode" role="group" aria-label="Как доставить">
            <button
              type="button"
              className={
                form.deliveryMode === DELIVERY_MODES.ASAP
                  ? 'restaurant-order-form__delivery-mode-btn restaurant-order-form__delivery-mode-btn--active'
                  : 'restaurant-order-form__delivery-mode-btn'
              }
              onClick={() => setForm((f) => ({ ...f, deliveryMode: DELIVERY_MODES.ASAP }))}
              disabled={!restaurantId}
            >
              Как можно скорее
            </button>
            <button
              type="button"
              className={
                form.deliveryMode === DELIVERY_MODES.SCHEDULED
                  ? 'restaurant-order-form__delivery-mode-btn restaurant-order-form__delivery-mode-btn--active'
                  : 'restaurant-order-form__delivery-mode-btn'
              }
              onClick={() => setForm((f) => ({ ...f, deliveryMode: DELIVERY_MODES.SCHEDULED }))}
              disabled={!restaurantId}
            >
              Ко времени
            </button>
          </div>
        </div>
        {form.deliveryMode === DELIVERY_MODES.SCHEDULED ? (
          <label className="restaurant-order-form__label">
            Дата и время доставки
            <input
              className="input"
              type="datetime-local"
              value={form.deliveryTime}
              onChange={(e) => setForm({ ...form, deliveryTime: e.target.value })}
              required
              disabled={!restaurantId}
            />
          </label>
        ) : (
          <p className="restaurant-order-form__hint muted">Курьер заберёт заказ в ближайшее время</p>
        )}
      </section>

      <section className="restaurant-order-form__section restaurant-order-form__section--last">
        <h2 className="restaurant-order-form__section-title">Стоимость</h2>
        <label className="restaurant-order-form__label">
          Стоимость доставки
          <input
            className="input"
            type="number"
            min="1"
            step="1"
            value={form.price}
            onChange={(e) => setForm({ ...form, price: e.target.value })}
            required
            placeholder="₽"
            disabled={!restaurantId}
          />
          <span className="restaurant-order-form__hint muted">
            Эту сумму увидит курьер перед взятием заказа
          </span>
        </label>
      </section>

      {message && <p className="restaurant-order-form__error">{message}</p>}

      <button type="submit" className="btn restaurant-order-form__submit" disabled={saving || !canSubmit}>
        {saving ? 'Создание…' : 'Создать и опубликовать'}
      </button>
    </form>
  )
}
