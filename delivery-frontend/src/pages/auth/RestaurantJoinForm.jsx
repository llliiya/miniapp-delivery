import { useState } from 'react'
import PhoneInput from '../../components/PhoneInput.jsx'
import { submitRestaurantRegistrationRequest } from '../../api/deliveryService.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'

export default function RestaurantJoinForm({ partnerCode, onBack, onSuccess }) {
  const [restaurantName, setRestaurantName] = useState('')
  const [address, setAddress] = useState('')
  const [contactPerson, setContactPerson] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const trimmedName = restaurantName.trim()
    const trimmedAddress = address.trim()
    const trimmedContact = contactPerson.trim()
    const trimmedEmail = email.trim()
    if (!trimmedName || !trimmedAddress || !trimmedContact || !phone.trim() || !trimmedEmail) {
      setError('Заполните все обязательные поля')
      return
    }
    if (!trimmedEmail.includes('@')) {
      setError('Введите корректный email')
      return
    }
    setSubmitting(true)
    try {
      const body = {
        restaurantName: trimmedName,
        address: trimmedAddress,
        contactPerson: trimmedContact,
        phone: phone.trim(),
        email: trimmedEmail,
        comment: comment.trim() || null,
      }
      if (partnerCode) {
        body.partnerCode = partnerCode
      }
      await submitRestaurantRegistrationRequest(body)
      onSuccess()
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось отправить заявку'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <p className="auth-hint">
        Заполните форму — наш менеджер свяжется с вами для подключения объекта к системе доставки.
      </p>
      <label className="auth-label">Название объекта *</label>
      <input
        type="text"
        className="auth-input"
        placeholder="Кафе «Уют»"
        value={restaurantName}
        onChange={(e) => setRestaurantName(e.target.value)}
        required
      />
      <label className="auth-label">Адрес *</label>
      <input
        type="text"
        className="auth-input"
        placeholder="г. Казань, ул. Примерная, 1"
        value={address}
        onChange={(e) => setAddress(e.target.value)}
        required
      />
      <label className="auth-label">Контактное лицо (ЛПР) *</label>
      <input
        type="text"
        className="auth-input"
        placeholder="Иван Иванов"
        value={contactPerson}
        onChange={(e) => setContactPerson(e.target.value)}
        required
        autoComplete="name"
      />
      <label className="auth-label">Телефон *</label>
      <PhoneInput className="auth-input" value={phone} onChange={setPhone} required />
      <label className="auth-label">E-mail *</label>
      <input
        type="email"
        className="auth-input"
        placeholder="cafe@mail.ru"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
        autoComplete="email"
      />
      <label className="auth-label">Комментарий (необязательно)</label>
      <textarea
        className="auth-input auth-textarea"
        rows={3}
        placeholder="Например: работаем с 10:00 до 22:00"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
      />
      {error && <div className="auth-error">{error}</div>}
      <button type="submit" className="auth-submit" disabled={submitting}>
        {submitting ? 'Отправляем…' : 'Отправить заявку'}
      </button>
      {onBack && (
        <button type="button" className="auth-link" onClick={onBack}>
          ← Ко входу
        </button>
      )}
    </form>
  )
}
