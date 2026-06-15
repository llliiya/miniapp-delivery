import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createRestaurant, listServiceCities } from '../../api/deliveryService.js'
import ProvisioningCredentialsModal from '../../components/ProvisioningCredentialsModal.jsx'
import PhoneInput from '../../components/PhoneInput.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'

export default function AddObjectPage() {
  const navigate = useNavigate()
  const courierServiceId = useCourierServiceId()
  const [name, setName] = useState('')
  const [city, setCity] = useState('')
  const [cityOptions, setCityOptions] = useState([])
  const [ownerFullName, setOwnerFullName] = useState('')
  const [ownerPhone, setOwnerPhone] = useState('')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [ownerCredentials, setOwnerCredentials] = useState(null)
  const [createdObjectId, setCreatedObjectId] = useState(null)

  useEffect(() => {
    if (!courierServiceId) return
    listServiceCities(courierServiceId)
      .then((list) => setCityOptions(list || []))
      .catch(() => setCityOptions([]))
  }, [courierServiceId])

  const onSubmit = async (e) => {
    e.preventDefault()
    if (!courierServiceId) {
      setError('Нет активной курьерской службы')
      return
    }
    const trimmed = name.trim()
    const cityVal = city.trim()
    const ownerName = ownerFullName.trim()
    const ownerPhoneVal = ownerPhone.trim()
    if (!trimmed) {
      setError('Укажите название объекта')
      return
    }
    if (!cityVal) {
      setError('Укажите город')
      return
    }
    const ownerEmailVal = ownerEmail.trim()
    if (!ownerName || !ownerPhoneVal || !ownerEmailVal) {
      setError('Заполните ФИО, телефон и email владельца')
      return
    }

    setSaving(true)
    setError('')
    try {
      const created = await createRestaurant({
        name: trimmed,
        city: cityVal,
        courierServiceId,
        owner: {
          fullName: ownerName,
          phone: ownerPhoneVal,
          email: ownerEmailVal,
        },
      })
      const objectId = created?.object?.id ?? created?.id
      setCreatedObjectId(objectId)
      setOwnerCredentials(created?.ownerCredentials || null)
      if (!created?.ownerCredentials) {
        navigate(`/service/restaurants/${objectId}`, {
          replace: true,
          state: { createdMessage: 'Объект успешно создан' },
        })
      }
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось создать объект'))
    } finally {
      setSaving(false)
    }
  }

  const goToObject = () => {
    if (createdObjectId) {
      navigate(`/service/restaurants/${createdObjectId}`, {
        replace: true,
        state: { createdMessage: 'Объект успешно создан' },
      })
    }
    setOwnerCredentials(null)
  }

  return (
    <div className="objects-page">
      <ProvisioningCredentialsModal
        open={Boolean(ownerCredentials)}
        title="Объект создан"
        intro="Объект добавлен в службу."
        login={ownerCredentials?.login}
        temporaryPassword={ownerCredentials?.temporaryPassword}
        personHint="Данные для владельца объекта"
        primaryAction="Перейти к объекту"
        onPrimaryAction={goToObject}
        onClose={() => {
          setOwnerCredentials(null)
          if (createdObjectId) goToObject()
        }}
      />

      <Link to="/service/restaurants" className="objects-page__back muted">
        ← К объектам
      </Link>

      <h1 className="objects-page__title">Добавить объект</h1>

      {!courierServiceId && (
        <section className="card">
          <p className="muted">
            Нет активной курьерской службы. Войдите как собственник или менеджер службы.
          </p>
        </section>
      )}

      {courierServiceId && (
        <form className="card objects-form" onSubmit={onSubmit}>
          <h2 className="objects-form__section-title">Данные объекта</h2>
          <label className="objects-form__label">
            Название объекта
            <input
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Например, Кафе на набережной"
              autoFocus
              required
            />
          </label>

          <label className="objects-form__label">
            Город
            <input
              className="input"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              placeholder="Например, Казань"
              list="service-city-options"
              required
            />
            <datalist id="service-city-options">
              {cityOptions.map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
          </label>

          <h2 className="objects-form__section-title">Владелец объекта</h2>
          <label className="objects-form__label">
            ФИО владельца
            <input
              className="input"
              value={ownerFullName}
              onChange={(e) => setOwnerFullName(e.target.value)}
              placeholder="Иванов Иван"
              required
            />
          </label>
          <label className="objects-form__label">
            Телефон владельца
            <PhoneInput className="input" value={ownerPhone} onChange={setOwnerPhone} />
          </label>
          <label className="objects-form__label">
            Email владельца
            <input
              type="email"
              className="input"
              value={ownerEmail}
              onChange={(e) => setOwnerEmail(e.target.value)}
              placeholder="owner@example.com"
              required
            />
          </label>

          {error && <p className="objects-form__error">{error}</p>}
          <button type="submit" className="btn objects-form__submit" disabled={saving}>
            {saving ? 'Создание…' : 'Создать объект'}
          </button>
        </form>
      )}
    </div>
  )
}
