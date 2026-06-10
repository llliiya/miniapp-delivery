import { useEffect, useState } from 'react'
import PhoneInput from '../../components/PhoneInput.jsx'
import { submitCourierRequest } from '../../api/deliveryService.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'

function prefillNameFromMessenger(identity) {
  if (!identity) return ''
  const tgUser = window.Telegram?.WebApp?.initDataUnsafe?.user
  const maxUser = window.WebApp?.initDataUnsafe?.user || window.Telegram?.WebApp?.initDataUnsafe?.user
  const user = tgUser || maxUser || {}
  const parts = [user.first_name, user.last_name].filter(Boolean)
  if (parts.length) return parts.join(' ')
  return ''
}

export default function RegistrationRequestScreen({ onBack, onSuccess, messengerIdentity }) {
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [city, setCity] = useState('')
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (messengerIdentity) {
      setFullName((prev) => prev || prefillNameFromMessenger(messengerIdentity))
    }
  }, [messengerIdentity])

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const trimmedName = fullName.trim()
    const trimmedEmail = email.trim()
    const trimmedCity = city.trim()
    if (!trimmedName || !phone.trim() || !trimmedEmail || !trimmedCity) {
      setError('Заполните ФИО, телефон, email и город')
      return
    }
    if (!trimmedEmail.includes('@')) {
      setError('Введите корректный email')
      return
    }
    setSubmitting(true)
    try {
      const body = {
        fullName: trimmedName,
        phone: phone.trim(),
        email: trimmedEmail,
        city: trimmedCity,
        comment: comment.trim() || null,
      }
      if (messengerIdentity?.provider && messengerIdentity?.externalId) {
        body.messengerProvider = messengerIdentity.provider
        body.messengerExternalId = messengerIdentity.externalId
        body.messengerUsername = messengerIdentity.username || null
      }
      await submitCourierRequest(body)
      onSuccess()
    } catch (err) {
      if (err?.error === 'application_already_pending') {
        onSuccess()
        return
      }
      if (err?.error === 'messenger_already_registered') {
        setError('Этот аккаунт уже зарегистрирован. Попробуйте войти.')
        return
      }
      setError(mapDeliveryApiError(err, 'Не удалось отправить заявку'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <p className="auth-hint">
        {messengerIdentity
          ? 'Оставьте контакты — после одобрения заявки Telegram/MAX привяжется автоматически.'
          : 'Оставьте контакты — мы свяжемся с вами и выдадим доступ к системе.'}
      </p>
      <label className="auth-label">ФИО</label>
      <input
        type="text"
        className="auth-input"
        placeholder="Иван Иванов"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        required
        autoComplete="name"
      />
      <label className="auth-label">Телефон</label>
      <PhoneInput className="auth-input" value={phone} onChange={setPhone} required />
      <label className="auth-label">Email</label>
      <input
        type="email"
        className="auth-input"
        placeholder="ivan@mail.ru"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
        autoComplete="email"
      />
      <p className="auth-hint">На этот адрес будут приходить коды для входа</p>
      <label className="auth-label">Город</label>
      <input
        type="text"
        className="auth-input"
        placeholder="Казань"
        value={city}
        onChange={(e) => setCity(e.target.value)}
        required
      />
      <label className="auth-label">Комментарий (необязательно)</label>
      <textarea
        className="auth-input auth-textarea"
        rows={3}
        placeholder="Например: есть опыт курьерской доставки"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
      />
      {error && <div className="auth-error">{error}</div>}
      <button type="submit" className="auth-submit" disabled={submitting}>
        {submitting ? 'Отправляем…' : 'Отправить заявку'}
      </button>
      <button type="button" className="auth-link" onClick={onBack}>
        ← Ко входу
      </button>
    </form>
  )
}
