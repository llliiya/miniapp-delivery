import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  addOrganizationMember,
  listOrganizationMembers,
  listRestaurants,
  patchOrganizationMember,
  removeOrganizationMember,
  resetOrganizationMemberAccess,
} from '../../api/deliveryService.js'
import ProvisioningCredentialsModal from '../../components/ProvisioningCredentialsModal.jsx'
import PhoneInput from '../../components/PhoneInput.jsx'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'
import { runLatestResetAccess } from '../../utils/runLatestResetAccess.js'

const ROLE_OPTIONS = [
  { value: 'manager', label: 'Менеджер' },
  { value: 'owner', label: 'Собственник' },
]

function roleLabel(role) {
  if (role === 'owner') return 'Собственник'
  if (role === 'manager') return 'Менеджер'
  return role || '—'
}

function statusLabel(status) {
  if (status === 'active') return 'Активен'
  if (status === 'blocked') return 'Заблокирован'
  return status || '—'
}

function formatDate(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '—'
  }
}

export default function ObjectStaffPage() {
  const { restaurantId } = useParams()
  const [objectName, setObjectName] = useState('')
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [messageOk, setMessageOk] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('manager')
  const [submitting, setSubmitting] = useState(false)
  const [credentials, setCredentials] = useState(null)
  const [credentialsTitle, setCredentialsTitle] = useState('Сотрудник создан')
  const [credentialsIntro, setCredentialsIntro] = useState('Сотрудник добавлен в объект.')
  const [actionBusyUserId, setActionBusyUserId] = useState(null)
  const resetAccessSeqRef = useRef(0)

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const restaurants = await listRestaurants()
      const found = (restaurants || []).find((x) => x.id === restaurantId)
      setObjectName(found?.name || '')
      const list = (await listOrganizationMembers(restaurantId)) || []
      setMembers(list.filter((m) => m.role === 'owner' || m.role === 'manager'))
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить сотрудников')
    } finally {
      setLoading(false)
    }
  }, [restaurantId])

  useEffect(() => {
    reload()
  }, [reload])

  const onAdd = async (e) => {
    e.preventDefault()
    setMessage('')
    setMessageOk(false)
    const trimmedName = fullName.trim()
    const trimmedPhone = phone.trim()
    const trimmedEmail = email.trim()
    if (!trimmedName || !trimmedPhone || !trimmedEmail) {
      setMessage('Заполните ФИО, телефон и email')
      return
    }
    setSubmitting(true)
    try {
      const result = await addOrganizationMember(restaurantId, {
        role,
        fullName: trimmedName,
        phone: trimmedPhone,
        email: trimmedEmail,
      })
      setCredentialsTitle('Сотрудник создан')
      setCredentialsIntro('Сотрудник добавлен в объект.')
      setCredentials(result?.credentials || null)
      setMessageOk(true)
      setMessage('Сотрудник создан')
      setShowForm(false)
      setFullName('')
      setPhone('')
      setEmail('')
      setRole('manager')
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось добавить сотрудника'))
    } finally {
      setSubmitting(false)
    }
  }

  const onResetAccess = async (member) => {
    if (!window.confirm('Сбросить пароль? Пользователю будет выдан новый временный пароль.')) {
      return
    }
    setCredentials(null)
    setActionBusyUserId(member.userId)
    setMessage('')
    try {
      const { applied, creds } = await runLatestResetAccess(resetAccessSeqRef, () =>
        resetOrganizationMemberAccess(restaurantId, member.userId),
      )
      if (!applied || !creds?.login || !creds?.temporaryPassword) return
      setCredentialsTitle('Доступ обновлен')
      setCredentialsIntro('Передайте пользователю новые данные для входа.')
      setCredentials(creds)
      setMessageOk(true)
      setMessage('Доступ обновлён')
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось сбросить доступ'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  const onSetStatus = async (member, nextStatus) => {
    const label = nextStatus === 'blocked' ? 'заблокировать' : 'разблокировать'
    if (!window.confirm(`Вы уверены, что хотите ${label} этого сотрудника?`)) {
      return
    }
    setActionBusyUserId(member.userId)
    setMessage('')
    try {
      await patchOrganizationMember(restaurantId, member.userId, { status: nextStatus })
      setMessageOk(true)
      setMessage(nextStatus === 'blocked' ? 'Сотрудник заблокирован' : 'Сотрудник разблокирован')
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось изменить статус'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  const onRemoveFromObject = async (member) => {
    if (!window.confirm('Удалить сотрудника из объекта? Учётная запись в системе сохранится.')) {
      return
    }
    setActionBusyUserId(member.userId)
    setMessage('')
    try {
      await removeOrganizationMember(restaurantId, member.userId)
      setMessageOk(true)
      setMessage('Сотрудник удалён из объекта')
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось удалить сотрудника'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  return (
    <div className="objects-page">
      <ProvisioningCredentialsModal
        key={
          credentials
            ? `${credentials.login}:${credentials.temporaryPassword}`
            : 'credentials-closed'
        }
        open={Boolean(credentials)}
        title={credentialsTitle}
        intro={credentialsIntro}
        login={credentials?.login}
        temporaryPassword={credentials?.temporaryPassword}
        personHint="Данные для сотрудника"
        onClose={() => setCredentials(null)}
      />

      <Link to={`/service/restaurants/${restaurantId}`} className="objects-page__back muted">
        ← К объекту
      </Link>

      <h1 className="objects-page__title">Сотрудники объекта</h1>
      {objectName && <p className="muted">{objectName}</p>}

      {error && (
        <section className="card">
          <p>{error}</p>
        </section>
      )}

      <section className="card">
        <p className="muted" style={{ marginTop: 0 }}>
          Добавьте собственника или менеджера объекта. Система создаст учётную запись и выдаст логин с
          временным паролем.
        </p>
        <button type="button" className="btn" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Скрыть форму' : 'Добавить сотрудника'}
        </button>
      </section>

      {message && (
        <section
          className="card"
          style={{ color: messageOk ? '#047857' : '#b91c1c' }}
          role="status"
        >
          {message}
        </section>
      )}

      {showForm && (
        <form className="card objects-form" onSubmit={onAdd}>
          <h3 style={{ marginTop: 0 }}>Новый сотрудник</h3>
          <label className="objects-form__label">
            ФИО
            <input
              className="input"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
            />
          </label>
          <label className="objects-form__label">
            Телефон
            <PhoneInput className="input" value={phone} onChange={setPhone} />
          </label>
          <label className="objects-form__label">
            Email
            <input
              type="email"
              className="input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label className="objects-form__label">
            Роль
            <select
              className="input"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              {ROLE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <div className="form-actions">
            <button type="submit" className="btn" disabled={submitting}>
              {submitting ? 'Создание…' : 'Добавить'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>
              Отмена
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <section className="card">
          <p className="muted">Загрузка…</p>
        </section>
      ) : members.length === 0 ? (
        <section className="card muted">Сотрудников пока нет.</section>
      ) : (
        members.map((m) => (
          <section key={m.id} className="card">
            <strong>{m.displayName || `Сотрудник ${m.publicId ?? ''}`}</strong>
            <p className="muted" style={{ margin: '6px 0 0' }}>
              Роль: {roleLabel(m.role)}
            </p>
            <p className="muted" style={{ margin: '4px 0' }}>
              Статус: {statusLabel(m.status)}
            </p>
            <p className="muted" style={{ margin: '4px 0' }}>
              ID пользователя: {m.userId}
            </p>
            <p className="muted" style={{ margin: '4px 0 12px' }}>
              Добавлен: {formatDate(m.createdAt)}
            </p>
            <div className="form-actions">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={actionBusyUserId === m.userId}
                onClick={() => onResetAccess(m)}
              >
                Сбросить пароль
              </button>
              {m.status === 'active' ? (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={actionBusyUserId === m.userId}
                  onClick={() => onSetStatus(m, 'blocked')}
                >
                  Заблокировать
                </button>
              ) : (
                <button
                  type="button"
                  className="btn"
                  disabled={actionBusyUserId === m.userId}
                  onClick={() => onSetStatus(m, 'active')}
                >
                  Разблокировать
                </button>
              )}
              {m.role !== 'owner' && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={actionBusyUserId === m.userId}
                  onClick={() => onRemoveFromObject(m)}
                >
                  Удалить из объекта
                </button>
              )}
            </div>
          </section>
        ))
      )}
    </div>
  )
}
