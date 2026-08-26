import { useCallback, useEffect, useId, useState } from 'react'
import { useLocation } from 'react-router-dom'
import QrCodeImage from '../QrCodeImage.jsx'
import CopySnackbar from '../CopySnackbar.jsx'
import { copyToClipboard } from '../../utils/copyToClipboard.js'

const PARTNER_PROGRAM_HASH = '#partner-program'

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

function referralTypeLabel(type) {
  if (type === 'RESTAURANT') return 'Объект'
  if (type === 'COURIER') return 'Курьер'
  return type || '—'
}

function InviteBlock({ title, hint, url, partnerCode, onCopy }) {
  if (!url) return null
  return (
    <div className="partner-program-invite">
      <h3 className="partner-program-invite__title">{title}</h3>
      <p className="muted partner-program-invite__hint">{hint}</p>
      <p className="partner-program-invite__url">{url}</p>
      <button type="button" className="btn btn-secondary" onClick={() => onCopy(url, title)}>
        Скопировать ссылку
      </button>
      <div className="partner-program-invite__qr">
        <QrCodeImage value={url} size={180} />
        {partnerCode && (
          <p className="muted partner-program-invite__code">Код: {partnerCode}</p>
        )}
      </div>
    </div>
  )
}

export default function PartnerProgramSection({ loadProgram, disabled = false, disabledMessage }) {
  const location = useLocation()
  const panelId = useId()
  const [expanded, setExpanded] = useState(() => location.hash === PARTNER_PROGRAM_HASH)
  const [program, setProgram] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [snackbar, setSnackbar] = useState(null)

  const reload = useCallback(async () => {
    if (disabled || !loadProgram) {
      setProgram(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      setProgram(await loadProgram())
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить реферальную программу')
    } finally {
      setLoading(false)
    }
  }, [disabled, loadProgram])

  useEffect(() => {
    if (location.hash === PARTNER_PROGRAM_HASH) {
      setExpanded(true)
    }
  }, [location.hash])

  useEffect(() => {
    if (expanded) {
      reload()
    }
  }, [expanded, reload])

  const onCopy = async (url, label) => {
    try {
      await copyToClipboard(url)
      setSnackbar({ message: `${label}: ссылка скопирована`, variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }

  if (disabled) {
    return (
      <section className="card role-profile-section partner-program-section partner-program-section--collapsed">
        <h2 className="role-profile-section__title">Реферальная программа</h2>
        <p className="muted partner-program-section__intro">
          {disabledMessage || 'Раздел будет доступен после активации аккаунта.'}
        </p>
      </section>
    )
  }

  return (
    <section
      className={`card role-profile-section partner-program-section${
        expanded ? ' partner-program-section--expanded' : ' partner-program-section--collapsed'
      }`}
    >
      <CopySnackbar
        message={snackbar?.message}
        variant={snackbar?.variant}
        onDismiss={() => setSnackbar(null)}
      />

      <button
        type="button"
        className="partner-program-section__toggle"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-controls={panelId}
      >
        <span className="role-profile-section__title">Реферальная программа</span>
        <span
          className={`partner-program-section__chevron${
            expanded ? ' partner-program-section__chevron--open' : ''
          }`}
          aria-hidden
        />
      </button>

      {!expanded && (
        <p className="muted partner-program-section__intro">
          Приглашайте объекты и курьеров по персональным ссылкам.
        </p>
      )}

      {expanded && (
        <div id={panelId} className="partner-program-section__body">
          {loading && <p className="muted">Загрузка…</p>}
          {error && <p style={{ color: '#b91c1c' }}>{error}</p>}

          {program && !loading && (
            <>
              <div className="partner-program-invites">
                <InviteBlock
                  title="Пригласить объект"
                  hint="Ссылка для кафе и ресторанов — заявка на подключение к системе"
                  url={program.restaurantInviteUrl}
                  partnerCode={program.partnerCode}
                  onCopy={onCopy}
                />
                <InviteBlock
                  title="Пригласить курьера"
                  hint="Ссылка для кандидатов в курьеры — заявка на подключение к службе"
                  url={program.courierInviteUrl}
                  partnerCode={program.partnerCode}
                  onCopy={onCopy}
                />
              </div>

              <div className="partner-program-stats">
                <p className="muted">
                  Всего приглашений: <strong>{program.totalInvitations}</strong>
                </p>
                <p className="muted">
                  Ожидают рассмотрения: <strong>{program.pendingCount}</strong>
                </p>
                <p className="muted">
                  Подключены: <strong>{program.connectedCount}</strong>
                </p>
              </div>

              <div className="partner-program-referrals">
                <h3 className="partner-program-referrals__title">Мои приглашения</h3>
                {!program.referrals?.length ? (
                  <p className="muted">Пока нет приглашённых участников.</p>
                ) : (
                  program.referrals.map((ref) => (
                    <article key={`${ref.referralType}-${ref.requestId}`} className="partner-program-referral card">
                      <div className="partner-program-referral__header">
                        <strong>{ref.displayName}</strong>
                        <span className="partner-program-referral__type">{referralTypeLabel(ref.referralType)}</span>
                      </div>
                      <p className="muted">Подана: {formatDate(ref.submittedAt)}</p>
                      <p className="muted">Статус: {statusLabel(ref.status)}</p>
                      {ref.connectedAt && (
                        <p className="muted">Подключён: {formatDate(ref.connectedAt)}</p>
                      )}
                    </article>
                  ))
                )}
              </div>
            </>
          )}
        </div>
      )}
    </section>
  )
}
