import PhoneInput from '../PhoneInput.jsx'
import { formatShortAddress } from '../../utils/formatShortAddress.js'
import { isValidRussianPhone, toE164 } from '../../utils/phone.js'
import './OrderEditForm.css'

const COMMENT_MAX_LENGTH = 300

/**
 * @param {{
 *   form: object
 *   setForm: (updater: object | ((prev: object) => object)) => void
 *   onSubmit: (e: React.FormEvent) => void
 *   saving?: boolean
 *   message?: string
 *   showPickupPoint?: boolean
 *   pickupPoints?: Array<{ id: string, name: string, address: string }>
 *   priceLabel?: string
 * }} props
 */
export default function OrderEditForm({
  form,
  setForm,
  onSubmit,
  saving = false,
  message = '',
  showPickupPoint = false,
  pickupPoints = [],
  priceLabel = 'Стоимость',
}) {
  const phoneInvalid = Boolean(form.customerPhone) && !isValidRussianPhone(form.customerPhone)
  const phoneTouched = Boolean(form.customerPhone)
  const priceValue = form.price === '' || form.price == null ? '' : String(form.price)
  const priceNumber = priceValue === '' ? NaN : Number(priceValue)
  const priceInvalid = priceValue !== '' && (Number.isNaN(priceNumber) || priceNumber < 0)
  const canSubmit =
    !saving &&
    isValidRussianPhone(form.customerPhone) &&
    form.deliveryAddress?.trim() &&
    form.deliveryTime &&
    priceValue !== '' &&
    !Number.isNaN(priceNumber) &&
    priceNumber >= 0

  const handlePriceChange = (e) => {
    const raw = e.target.value
    if (raw === '') {
      setForm({ ...form, price: '' })
      return
    }
    const num = Number(raw)
    if (Number.isNaN(num) || num < 0) return
    setForm({ ...form, price: raw })
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!isValidRussianPhone(form.customerPhone)) return
    onSubmit(e)
  }

  return (
    <form onSubmit={handleSubmit} className="order-edit-form card">
      {showPickupPoint ? (
        <label className="order-edit-form__field">
          <span className="order-edit-form__label">Точка забора</span>
          <select
            className="order-edit-form__select"
            value={form.pickupPointId}
            onChange={(e) => setForm({ ...form, pickupPointId: e.target.value })}
          >
            {pickupPoints.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} — {formatShortAddress(p.address)}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <label className="order-edit-form__field">
        <span className="order-edit-form__label">Адрес доставки</span>
        <input
          className="order-edit-form__input"
          value={form.deliveryAddress}
          onChange={(e) => setForm({ ...form, deliveryAddress: e.target.value })}
          autoComplete="street-address"
        />
      </label>

      <label className="order-edit-form__field">
        <span className="order-edit-form__label">Дата и время</span>
        <input
          className="order-edit-form__input"
          type="datetime-local"
          value={form.deliveryTime}
          onChange={(e) => setForm({ ...form, deliveryTime: e.target.value })}
        />
      </label>

      <label className="order-edit-form__field">
        <span className="order-edit-form__label">{priceLabel}</span>
        <div className="order-edit-form__price-wrap">
          <input
            className={[
              'order-edit-form__input',
              priceInvalid ? 'order-edit-form__input--invalid' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={priceValue}
            onChange={handlePriceChange}
          />
          <span className="order-edit-form__price-suffix" aria-hidden="true">
            ₽
          </span>
        </div>
      </label>

      <label className="order-edit-form__field">
        <span className="order-edit-form__label">Телефон</span>
        <PhoneInput
          className="order-edit-form__input"
          value={form.customerPhone}
          onChange={(phone) => setForm({ ...form, customerPhone: phone })}
          invalid={phoneTouched && phoneInvalid}
          placeholder="+7 (___) ___-__-__"
        />
        {phoneTouched && phoneInvalid ? (
          <p className="order-edit-form__field-error">Введите номер в формате +7 (9XX) XXX-XX-XX</p>
        ) : null}
      </label>

      <label className="order-edit-form__field">
        <span className="order-edit-form__label">Комментарий</span>
        <div className="order-edit-form__textarea-wrap">
          <textarea
            className="order-edit-form__textarea"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value.slice(0, COMMENT_MAX_LENGTH) })}
            maxLength={COMMENT_MAX_LENGTH}
            rows={4}
          />
          <span className="order-edit-form__char-count" aria-live="polite">
            {form.comment.length} / {COMMENT_MAX_LENGTH}
          </span>
        </div>
      </label>

      {message ? <p className="order-edit-form__error">{message}</p> : null}

      <button type="submit" className="order-edit-form__submit" disabled={!canSubmit}>
        {saving ? (
          <>
            <span className="order-edit-form__spinner" aria-hidden="true" />
            Сохранение...
          </>
        ) : (
          'Сохранить'
        )}
      </button>
    </form>
  )
}

/** Нормализованный телефон для отправки на backend */
export function normalizeOrderPhone(phone) {
  return toE164(phone) || phone
}
