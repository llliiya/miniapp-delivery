import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  addCourier,
  approveCourierRequest,
  listCourierRequests,
  listCouriers,
  patchCourier,
  rejectCourierRequest,
  resetCourierAccess,
} from '../../api/deliveryService.js'
import EmptyState, { EmptyStateIcon } from '../../components/EmptyState.jsx'
import ProvisioningCredentialsModal from '../../components/ProvisioningCredentialsModal.jsx'
import PhoneInput from '../../components/PhoneInput.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { useServiceCity } from '../../context/ServiceCityContext.jsx'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'
import { runLatestResetAccess } from '../../utils/runLatestResetAccess.js'

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

function statusLabel(status) {
  if (status === 'active') return 'Активен'
  if (status === 'blocked') return 'Заблокирован'
  return status || '—'
}

function statusBadgeClass(status) {
  if (status === 'active') return 'badge'
  if (status === 'blocked') return 'badge badge-warn'
  return 'badge'
}

export default function ServiceCouriersPage() {
  const courierServiceId = useCourierServiceId()
  const { cityQueryParam } = useServiceCity()
  const [couriers, setCouriers] = useState([])
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [applicationsLoading, setApplicationsLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [messageOk, setMessageOk] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [conflictCourierId, setConflictCourierId] = useState(null)
  const [statusUpdatingId, setStatusUpdatingId] = useState(null)
  const [credentials, setCredentials] = useState(null)
  const [credentialsTitle, setCredentialsTitle] = useState('Курьер создан')
  const [credentialsIntro, setCredentialsIntro] = useState('Курьер добавлен в службу.')
  const resetAccessSeqRef = useRef(0)

  const reloadApplications = useCallback(async () => {
    if (!courierServiceId) {
      setApplications([])
      setApplicationsLoading(false)
      return
    }
    setApplicationsLoading(true)
    try {
      setApplications((await listCourierRequests(courierServiceId, cityQueryParam)) || [])
    } catch {
      setApplications([])
    } finally {
      setApplicationsLoading(false)
    }
  }, [courierServiceId, cityQueryParam])

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setCouriers([])
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setCouriers((await listCouriers(courierServiceId)) || [])
    } catch (e) {
      setMessageOk(false)
      setMessage(e?.message || 'Не удалось загрузить список курьеров')
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
    reloadApplications()
  }, [reload, reloadApplications])

  const resetForm = () => {
    setFullName('')
    setPhone('')
    setEmail('')
  }

  const onAddCourier = async (e) => {
    e.preventDefault()
    setMessage('')
    setMessageOk(false)
    setConflictCourierId(null)
    const trimmedName = fullName.trim()
    const trimmedPhone = phone.trim()
    const trimmedEmail = email.trim()
    if (!trimmedName || !trimmedPhone || !trimmedEmail) {
      setMessage('Заполните ФИО, телефон и email')
      return
    }
    if (!courierServiceId) {
      setMessage('Служба доставки не выбрана')
      return
    }
    setSubmitting(true)
    try {
      const result = await addCourier({
        courierServiceId,
        fullName: trimmedName,
        phone: trimmedPhone,
        email: trimmedEmail,
      })
      setCredentialsTitle('Курьер создан')
      setCredentialsIntro('Курьер добавлен в службу.')
      setCredentials(result?.credentials || null)
      setMessageOk(true)
      setMessage('Курьер создан')
      setShowForm(false)
      resetForm()
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось добавить курьера'))
      const existingId = err?.existingCourierId || err?.existingCourier?.memberId
      if (err?.error === 'courier_already_in_service' && existingId) {
        setConflictCourierId(existingId)
      } else {
        setConflictCourierId(null)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const onResetAccess = async (courier) => {
    if (!window.confirm('Сбросить пароль? Курьеру будет выдан новый временный пароль.')) {
      return
    }
    setCredentials(null)
    setStatusUpdatingId(courier.memberId)
    setMessage('')
    try {
      const { applied, creds } = await runLatestResetAccess(resetAccessSeqRef, () =>
        resetCourierAccess(courier.memberId),
      )
      if (!applied || !creds?.login || !creds?.temporaryPassword) return
      setCredentialsTitle('Доступ обновлен')
      setCredentialsIntro('Передайте курьеру новые данные для входа.')
      setCredentials(creds)
      setMessageOk(true)
      setMessage('Доступ обновлён')
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось сбросить доступ'))
    } finally {
      setStatusUpdatingId(null)
    }
  }

  const onApproveApplication = async (application) => {
    const messengerNote = application.messengerExternalId
      ? ' Telegram/MAX привяжется автоматически.'
      : ' Курьеру будут выданы логин и пароль.'
    if (!window.confirm(`Одобрить заявку от ${application.fullName}?${messengerNote}`)) {
      return
    }
    setMessage('')
    try {
      const result = await approveCourierRequest(application.id, courierServiceId)
      setMessageOk(true)
      setMessage(result?.message || 'Курьер одобрен')
      await Promise.all([reload(), reloadApplications()])
    } catch (err) {
      setMessageOk(false)
      setMessage(err?.message || 'Не удалось одобрить заявку')
    }
  }

  const onRejectApplication = async (application) => {
    if (!window.confirm('Отклонить заявку?')) return
    setMessage('')
    try {
      await rejectCourierRequest(application.id, courierServiceId)
      setMessageOk(true)
      setMessage('Заявка отклонена')
      await reloadApplications()
    } catch (err) {
      setMessageOk(false)
      setMessage(err?.message || 'Не удалось отклонить заявку')
    }
  }

  const onSetStatus = async (courier, nextStatus) => {
    const label = nextStatus === 'blocked' ? 'заблокировать' : 'разблокировать'
    if (!window.confirm(`Вы уверены, что хотите ${label} этого курьера?`)) {
      return
    }
    setStatusUpdatingId(courier.memberId)
    setMessage('')
    setMessageOk(false)
    setConflictCourierId(null)
    try {
      await patchCourier(courier.memberId, { status: nextStatus })
      setMessageOk(true)
      setMessage(nextStatus === 'blocked' ? 'Курьер заблокирован' : 'Курьер разблокирован')
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(err?.message || 'Не удалось изменить статус')
    } finally {
      setStatusUpdatingId(null)
    }
  }

  if (!courierServiceId) {
    return (
      <div className="card">
        <h2 style={{ marginTop: 0 }}>Курьеры</h2>
        <p className="muted">
          Нет активной курьерской службы. Убедитесь, что вы вошли как собственник или менеджер службы.
        </p>
      </div>
    )
  }

  return (
    <div className="service-couriers-page">
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
        personHint="Данные для курьера"
        onClose={() => setCredentials(null)}
      />

      {!applicationsLoading && applications.length > 0 && (
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Заявки курьеров</h3>
          <p className="muted">Новые заявки из веб-входа и мессенджера. Нажмите «Одобрить» — курьер создаётся автоматически.</p>
          {applications.map((app) => (
            <div key={app.id} className="card" style={{ marginTop: 12 }}>
              <strong>{app.fullName}</strong>
              <p className="muted" style={{ margin: '6px 0' }}>Телефон: {app.phone}</p>
              {app.email && (
                <p className="muted" style={{ margin: '6px 0' }}>Email: {app.email}</p>
              )}
              <p className="muted" style={{ margin: '6px 0' }}>Город: {app.city}</p>
              {app.transport && (
                <p className="muted" style={{ margin: '6px 0' }}>Транспорт: {app.transport}</p>
              )}
              {app.source && (
                <p className="muted" style={{ margin: '6px 0' }}>Источник: {app.source === 'messenger' ? 'Telegram/MAX' : 'Веб'}</p>
              )}
              {app.messengerExternalId && (
                <p className="muted" style={{ margin: '6px 0' }}>
                  {app.messengerProvider}: {app.messengerExternalId}
                  {app.messengerUsername ? ` (@${app.messengerUsername})` : ''}
                </p>
              )}
              {app.comment && (
                <p className="muted" style={{ margin: '6px 0' }}>Комментарий: {app.comment}</p>
              )}
              <p className="muted" style={{ margin: '6px 0 12px' }}>
                Подана: {formatDate(app.createdAt)}
              </p>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => onApproveApplication(app)}>
                  Одобрить
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => onRejectApplication(app)}>
                  Отклонить
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <header className="service-couriers-page__header">
        <h1 className="service-couriers-page__title">Курьеры</h1>
        {!showForm && (
          <button type="button" className="btn service-couriers-page__add-btn" onClick={() => setShowForm(true)}>
            Добавить
          </button>
        )}
      </header>

      {message && (
        <div
          className="card"
          style={{ color: messageOk ? '#047857' : '#b91c1c', marginTop: 0 }}
          role="status"
        >
          <p style={{ margin: conflictCourierId ? '0 0 12px' : 0 }}>{message}</p>
          {conflictCourierId && (
            <Link to={`/service/couriers/${conflictCourierId}`} className="btn btn-secondary">
              Открыть карточку курьера
            </Link>
          )}
        </div>
      )}

      {showForm && (
        <form className="card" onSubmit={onAddCourier}>
          <h3 style={{ marginTop: 0 }}>Новый курьер</h3>
          <label className="muted" style={{ display: 'block', marginBottom: 4 }}>
            ФИО
          </label>
          <input
            type="text"
            className="input"
            placeholder="Иванов Иван"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            style={{ width: '100%', marginBottom: 12 }}
            required
          />
          <label className="muted" style={{ display: 'block', marginBottom: 4 }}>
            Телефон
          </label>
          <PhoneInput
            className="input"
            value={phone}
            onChange={setPhone}
            style={{ width: '100%', marginBottom: 12 }}
          />
          <label className="muted" style={{ display: 'block', marginBottom: 4 }}>
            Email
          </label>
          <input
            type="email"
            className="input"
            placeholder="ivan@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={{ width: '100%', marginBottom: 12 }}
            required
          />
          <div className="form-actions">
            <button type="submit" className="btn" disabled={submitting}>
              {submitting ? 'Создание…' : 'Создать курьера'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setShowForm(false)
                resetForm()
              }}
            >
              Отмена
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="card">Загрузка…</div>
      ) : couriers.length === 0 ? (
        <EmptyState
          icon={
            <EmptyStateIcon>
              <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="24" cy="14" r="6" />
                <path d="M10 40v-2a8 8 0 0 1 16 0v2M30 40v-2a6 6 0 0 1 10-4" />
              </svg>
            </EmptyStateIcon>
          }
          title="Курьеров пока нет"
          description="Добавьте первого курьера, чтобы он мог принимать заказы."
        />
      ) : (
        couriers.map((c) => (
          <div
            key={c.memberId}
            className={`card${c.status === 'blocked' ? ' card-inactive' : ''}`}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
              <strong>
                <Link to={`/service/couriers/${c.memberId}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                  {c.displayName || `Курьер ${c.publicId ?? ''}`}
                </Link>
              </strong>
              <span className={statusBadgeClass(c.status)}>{statusLabel(c.status)}</span>
            </div>
            {c.email && (
              <p className="muted" style={{ margin: '8px 0 4px' }}>
                Email: {c.email}
              </p>
            )}
            {c.phone && (
              <p className="muted" style={{ margin: '4px 0' }}>
                Телефон: {c.phone}
              </p>
            )}
            <p className="muted" style={{ margin: '8px 0 4px' }}>
              ID курьера: {c.publicId ?? '—'}
            </p>
            <p className="muted" style={{ margin: '4px 0' }}>
              ID пользователя: {c.userId}
            </p>
            <p className="muted" style={{ margin: '4px 0' }}>
              Баланс: {c.balance != null ? Number(c.balance).toLocaleString('ru-RU') : '0'} ₽
            </p>
            <p className="muted" style={{ margin: '4px 0' }}>
              Выполнено заказов: {c.completedOrdersCount ?? 0}
            </p>
            <p className="muted" style={{ margin: '4px 0 12px' }}>
              Добавлен: {formatDate(c.createdAt)}
            </p>
            <div className="form-actions">
              <Link to={`/service/couriers/${c.memberId}`} className="btn btn-secondary">
                Карточка
              </Link>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={statusUpdatingId === c.memberId}
                onClick={() => onResetAccess(c)}
              >
                Сбросить пароль
              </button>
              {c.status === 'active' ? (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={statusUpdatingId === c.memberId}
                  onClick={() => onSetStatus(c, 'blocked')}
                >
                  Заблокировать
                </button>
              ) : (
                <button
                  type="button"
                  className="btn"
                  disabled={statusUpdatingId === c.memberId}
                  onClick={() => onSetStatus(c, 'active')}
                >
                  Разблокировать
                </button>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  )
}
