import { useCallback, useEffect, useMemo, useState } from 'react'
import QrCodeImage from '../../components/QrCodeImage.jsx'
import CopySnackbar from '../../components/CopySnackbar.jsx'
import { fetchPartnerProgram } from '../../api/deliveryService.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { copyToClipboard } from '../../utils/copyToClipboard.js'

function formatDate(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })
  } catch {
    return '—'
  }
}

function statusLabel(status) {
  if (status === 'NEW') return 'Ожидает рассмотрения'
  if (status === 'IN_PROGRESS') return 'В работе'
  if (status === 'APPROVED') return 'Подключён'
  if (status === 'REJECTED') return 'Отклонён'
  return status || '—'
}

export default function CourierPartnerProgramPage() {
  const { activeMembership, deliveryMe } = useAuth()
  const [program, setProgram] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [snackbar, setSnackbar] = useState(null)

  const courierMembership = useMemo(() => {
    if (activeMembership?.organizationType === 'courier_service' && activeMembership?.role === 'courier') {
      return activeMembership
    }
    return (deliveryMe?.memberships || []).find(
      (m) => m.organizationType === 'courier_service' && m.role === 'courier',
    ) || null
  }, [activeMembership, deliveryMe])

  const memberId = courierMembership?.memberId

  const reload = useCallback(async () => {
    if (!memberId) {
      setProgram(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      setProgram(await fetchPartnerProgram(memberId))
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить партнёрскую программу')
    } finally {
      setLoading(false)
    }
  }, [memberId])

  useEffect(() => {
    reload()
  }, [reload])

  const onCopyLink = async () => {
    if (!program?.inviteUrl) return
    try {
      await copyToClipboard(program.inviteUrl)
      setSnackbar({ message: 'Ссылка скопирована', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }

  if (!memberId) {
    return (
      <div className="card">
        <h2 style={{ marginTop: 0 }}>Партнерская программа</h2>
        <p className="muted">Раздел доступен курьерам службы доставки.</p>
      </div>
    )
  }

  return (
    <div>
      <CopySnackbar
        message={snackbar?.message}
        variant={snackbar?.variant}
        onDismiss={() => setSnackbar(null)}
      />

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Партнерская программа</h2>
        <p className="muted">
          Приглашайте кафе и рестораны по персональной ссылке или QR-коду. Связь сохраняется для будущих вознаграждений.
        </p>
      </div>

      {loading && <div className="card">Загрузка…</div>}
      {error && <div className="card" style={{ color: '#b91c1c' }}>{error}</div>}

      {program && !loading && (
        <>
          <div className="card">
            <h3 style={{ marginTop: 0 }}>Моя ссылка</h3>
            <p style={{ wordBreak: 'break-all', margin: '8px 0 12px', fontFamily: 'monospace', fontSize: 14 }}>
              {program.inviteUrl}
            </p>
            <button type="button" className="btn btn-secondary" onClick={onCopyLink}>
              Скопировать ссылку
            </button>
          </div>

          <div className="card" style={{ textAlign: 'center' }}>
            <h3 style={{ marginTop: 0 }}>QR-код</h3>
            <p className="muted" style={{ marginBottom: 16 }}>
              Покажите QR владельцу объекта для быстрого подключения
            </p>
            <QrCodeImage value={program.inviteUrl} size={220} />
            <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
              Код: {program.partnerCode}
            </p>
          </div>

          <div className="card">
            <h3 style={{ marginTop: 0 }}>Статистика</h3>
            <p className="muted" style={{ margin: '6px 0' }}>
              Всего приглашений: <strong>{program.totalInvitations}</strong>
            </p>
            <p className="muted" style={{ margin: '6px 0' }}>
              Ожидают рассмотрения: <strong>{program.pendingCount}</strong>
            </p>
            <p className="muted" style={{ margin: '6px 0' }}>
              Подключены: <strong>{program.connectedCount}</strong>
            </p>
          </div>

          <div className="card">
            <h3 style={{ marginTop: 0 }}>Мои объекты</h3>
            {!program.referrals?.length ? (
              <p className="muted" style={{ margin: 0 }}>Пока нет приглашённых объектов.</p>
            ) : (
              program.referrals.map((ref) => (
                <div key={ref.requestId} className="card" style={{ marginTop: 12 }}>
                  <strong>{ref.restaurantName}</strong>
                  <p className="muted" style={{ margin: '6px 0' }}>
                    Подана: {formatDate(ref.submittedAt)}
                  </p>
                  <p className="muted" style={{ margin: '6px 0' }}>
                    Статус: {statusLabel(ref.status)}
                  </p>
                  {ref.connectedAt && (
                    <p className="muted" style={{ margin: '6px 0' }}>
                      Подключён: {formatDate(ref.connectedAt)}
                    </p>
                  )}
                </div>
              ))
            )}
          </div>
        </>
      )}
    </div>
  )
}
