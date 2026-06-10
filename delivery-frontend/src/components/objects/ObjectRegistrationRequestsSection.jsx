import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  approveRestaurantRegistrationRequest,
  listRestaurantRegistrationRequests,
  markRestaurantRegistrationInProgress,
  rejectRestaurantRegistrationRequest,
} from '../../api/deliveryService.js'
import ProvisioningCredentialsModal from '../ProvisioningCredentialsModal.jsx'
import AppModal from '../AppModal.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'

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
  if (status === 'NEW') return 'Новая'
  if (status === 'IN_PROGRESS') return 'В работе'
  if (status === 'APPROVED') return 'Подключён'
  if (status === 'REJECTED') return 'Отклонена'
  return status || '—'
}

function statusBadgeClass(status) {
  if (status === 'APPROVED') return 'badge'
  if (status === 'REJECTED') return 'badge badge-warn'
  if (status === 'IN_PROGRESS') return 'badge'
  return 'badge badge-warn'
}

function isPending(status) {
  return status === 'NEW' || status === 'IN_PROGRESS'
}

export default function ObjectRegistrationRequestsSection({ onChanged }) {
  const navigate = useNavigate()
  const courierServiceId = useCourierServiceId()
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [messageOk, setMessageOk] = useState(false)
  const [selected, setSelected] = useState(null)
  const [credentials, setCredentials] = useState(null)
  const [actionId, setActionId] = useState(null)

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setRequests([])
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRequests((await listRestaurantRegistrationRequests(courierServiceId)) || [])
    } catch (e) {
      setMessageOk(false)
      setMessage(e?.message || 'Не удалось загрузить заявки')
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const pendingRequests = requests.filter((r) => isPending(r.status))

  const onView = async (req) => {
    if (req.status === 'NEW' && courierServiceId) {
      try {
        const updated = await markRestaurantRegistrationInProgress(req.id, courierServiceId)
        setSelected(updated)
        setRequests((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      } catch {
        setSelected(req)
      }
    } else {
      setSelected(req)
    }
  }

  const onApprove = async (req) => {
    if (!window.confirm(`Одобрить заявку от «${req.restaurantName}»? Будет создан объект и учётная запись.`)) {
      return
    }
    setActionId(req.id)
    setMessage('')
    try {
      const result = await approveRestaurantRegistrationRequest(req.id, courierServiceId)
      setMessageOk(true)
      setMessage(result?.message || 'Объект подключён')
      if (result?.ownerCredentials?.login && result?.ownerCredentials?.temporaryPassword) {
        setCredentials(result.ownerCredentials)
      }
      setSelected(null)
      await reload()
      onChanged?.()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось одобрить заявку'))
    } finally {
      setActionId(null)
    }
  }

  const onReject = async (req) => {
    if (!window.confirm('Отклонить заявку?')) return
    setActionId(req.id)
    setMessage('')
    try {
      await rejectRestaurantRegistrationRequest(req.id, courierServiceId)
      setMessageOk(true)
      setMessage('Заявка отклонена')
      setSelected(null)
      await reload()
    } catch (err) {
      setMessageOk(false)
      setMessage(mapDeliveryApiError(err, 'Не удалось отклонить заявку'))
    } finally {
      setActionId(null)
    }
  }

  if (!courierServiceId) return null
  if (loading) return null
  if (pendingRequests.length === 0 && !selected) return null

  const detailFooter = selected ? (
    <div className="app-modal__footer-actions">
      {isPending(selected.status) && (
        <>
          <button
            type="button"
            className="btn"
            disabled={actionId === selected.id}
            onClick={() => onApprove(selected)}
          >
            Одобрить
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={actionId === selected.id}
            onClick={() => onReject(selected)}
          >
            Отклонить
          </button>
        </>
      )}
    </div>
  ) : null

  return (
    <section className="objects-page__requests">
      <ProvisioningCredentialsModal
        open={Boolean(credentials)}
        title="Объект подключён"
        intro="Передайте владельцу объекта данные для входа."
        login={credentials?.login}
        temporaryPassword={credentials?.temporaryPassword}
        personHint="Данные для объекта"
        onClose={() => setCredentials(null)}
      />

      <AppModal
        open={Boolean(selected)}
        title={selected?.restaurantName}
        onClose={() => setSelected(null)}
        footer={detailFooter}
      >
        {selected && (
          <>
            <dl className="app-modal__kv">
              <div className="app-modal__kv-row">
                <dt>Адрес</dt>
                <dd>{selected.address}</dd>
              </div>
              <div className="app-modal__kv-row">
                <dt>Контактное лицо</dt>
                <dd>{selected.contactPerson}</dd>
              </div>
              <div className="app-modal__kv-row">
                <dt>Телефон</dt>
                <dd>{selected.phone}</dd>
              </div>
              <div className="app-modal__kv-row">
                <dt>E-mail</dt>
                <dd>{selected.email}</dd>
              </div>
              {selected.comment && (
                <div className="app-modal__kv-row">
                  <dt>Комментарий</dt>
                  <dd>{selected.comment}</dd>
                </div>
              )}
              <div className="app-modal__kv-row">
                <dt>Источник</dt>
                <dd>{selected.sourceLabel}</dd>
              </div>
              <div className="app-modal__kv-row">
                <dt>Статус</dt>
                <dd>{statusLabel(selected.status)}</dd>
              </div>
              <div className="app-modal__kv-row">
                <dt>Подана</dt>
                <dd>{formatDate(selected.createdAt)}</dd>
              </div>
            </dl>
            {selected.restaurantId && (
              <button
                type="button"
                className="auth-link"
                style={{ padding: 0, marginTop: 4 }}
                onClick={() => {
                  setSelected(null)
                  navigate(`/service/restaurants/${selected.restaurantId}`)
                }}
              >
                Открыть объект →
              </button>
            )}
          </>
        )}
      </AppModal>

      {pendingRequests.length > 0 && (
        <div className="card">
          <h2 className="objects-page__section-title">Запросы на подключение</h2>
          <p className="muted objects-page__section-lead">
            Новые заявки от кафе и ресторанов — самостоятельные и по ссылкам курьеров.
          </p>

          {message && (
            <p style={{ color: messageOk ? '#047857' : '#b91c1c', margin: '12px 0 0' }} role="status">
              {message}
            </p>
          )}

          <div className="objects-page__requests-table-wrap">
            <table className="objects-page__requests-table">
              <thead>
                <tr>
                  <th>Объект</th>
                  <th>Контакт</th>
                  <th>Телефон</th>
                  <th>Источник</th>
                  <th>Статус</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {pendingRequests.map((req) => (
                  <tr key={req.id}>
                    <td>{req.restaurantName}</td>
                    <td>{req.contactPerson}</td>
                    <td>{req.phone}</td>
                    <td>{req.sourceLabel}</td>
                    <td>
                      <span className={statusBadgeClass(req.status)}>{statusLabel(req.status)}</span>
                    </td>
                    <td>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => onView(req)}>
                        Просмотреть
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  )
}
