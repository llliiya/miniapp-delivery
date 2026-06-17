import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getCourier,
  patchCourier,
  resetCourierAccess,
} from '../../api/deliveryService.js'
import ProvisioningCredentialsModal from '../../components/ProvisioningCredentialsModal.jsx'
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

export default function ServiceCourierDetailPage() {
  const { courierId } = useParams()
  const navigate = useNavigate()
  const [courier, setCourier] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [messageOk, setMessageOk] = useState(false)
  const [busy, setBusy] = useState(false)
  const [credentials, setCredentials] = useState(null)
  const resetAccessSeqRef = useRef(0)

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await getCourier(courierId)
      setCourier(data || null)
    } catch (e) {
      setCourier(null)
      setError(e?.message || 'Не удалось загрузить карточку курьера')
    } finally {
      setLoading(false)
    }
  }, [courierId])

  useEffect(() => {
    reload()
  }, [reload])

  const onResetAccess = async () => {
    if (!courier?.memberId) return
    if (!window.confirm('Сбросить пароль? Курьеру будет выдан новый временный пароль.')) {
      return
    }
    setBusy(true)
    setMessage('')
    setCredentials(null)
    try {
      const { applied, creds } = await runLatestResetAccess(resetAccessSeqRef, () =>
        resetCourierAccess(courier.memberId),
      )
      if (!applied || !creds?.login || !creds?.temporaryPassword) return
      setCredentials(creds)
      setMessageOk(true)
      setMessage('Доступ обновлён')
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось сбросить доступ'))
    } finally {
      setBusy(false)
    }
  }

  const onSetStatus = async (nextStatus) => {
    if (!courier?.memberId) return
    const label = nextStatus === 'blocked' ? 'заблокировать' : 'разблокировать'
    if (!window.confirm(`Вы уверены, что хотите ${label} этого курьера?`)) {
      return
    }
    setBusy(true)
    setMessage('')
    setMessageOk(false)
    try {
      const updated = await patchCourier(courier.memberId, { status: nextStatus })
      setCourier(updated)
      setMessageOk(true)
      setMessage(nextStatus === 'blocked' ? 'Курьер заблокирован' : 'Курьер разблокирован')
    } catch (err) {
      setMessageOk(false)
      setMessage(err?.message || 'Не удалось изменить статус')
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return <div className="card">Загрузка…</div>
  }

  if (error || !courier) {
    return (
      <div className="card">
        <p style={{ color: '#b91c1c' }}>{error || 'Курьер не найден'}</p>
        <Link to="/service/couriers" className="btn btn-secondary">
          Назад к списку
        </Link>
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
        title="Доступ обновлен"
        intro="Передайте курьеру новые данные для входа."
        login={credentials?.login}
        temporaryPassword={credentials?.temporaryPassword}
        personHint="Данные для курьера"
        onClose={() => setCredentials(null)}
      />

      <header className="service-couriers-page__header">
        <h1 className="service-couriers-page__title">Карточка курьера</h1>
        <button type="button" className="btn btn-secondary" onClick={() => navigate('/service/couriers')}>
          Назад
        </button>
      </header>

      {message && (
        <div
          className="card"
          style={{ color: messageOk ? '#047857' : '#b91c1c', marginTop: 0 }}
          role="status"
        >
          {message}
        </div>
      )}

      <div className={`card${courier.status === 'blocked' ? ' card-inactive' : ''}`}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
          <strong>{courier.displayName || `Курьер ${courier.publicId ?? ''}`}</strong>
          <span className={statusBadgeClass(courier.status)}>{statusLabel(courier.status)}</span>
        </div>
        <p className="muted" style={{ margin: '8px 0 4px' }}>
          ФИО: {courier.displayName || '—'}
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          Телефон: {courier.phone || '—'}
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          Email: {courier.email || '—'}
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          ID пользователя: {courier.userId ?? '—'}
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          ID курьера: {courier.publicId ?? '—'}
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          Баланс: {courier.balance != null ? Number(courier.balance).toLocaleString('ru-RU') : '0'} ₽
        </p>
        <p className="muted" style={{ margin: '4px 0' }}>
          Выполнено заказов: {courier.completedOrdersCount ?? 0}
        </p>
        <p className="muted" style={{ margin: '4px 0 12px' }}>
          Дата добавления: {formatDate(courier.createdAt)}
        </p>
        <div className="form-actions">
          <button type="button" className="btn btn-secondary" disabled={busy} onClick={onResetAccess}>
            Сбросить пароль
          </button>
          {courier.status === 'active' ? (
            <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => onSetStatus('blocked')}>
              Заблокировать
            </button>
          ) : (
            <button type="button" className="btn" disabled={busy} onClick={() => onSetStatus('active')}>
              Разблокировать
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
