import { useCallback, useEffect, useId, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import QrCodeImage from '../QrCodeImage.jsx'
import CopySnackbar from '../CopySnackbar.jsx'
import PartnerBankPayoutFields, { isValidBankPayoutDetails } from './PartnerBankPayoutFields.jsx'
import { copyToClipboard } from '../../utils/copyToClipboard.js'
import { buildPayoutDetailsPayload, getLastTransferType, saveLastTransferType } from '../../utils/partnerBankPayout.js'

const PARTNER_PROGRAM_HASH = '#partner-program'
const HISTORY_PREVIEW_LIMIT = 5
const INVITED_PREVIEW_LIMIT = 8

function formatMoney(value) {
  const n = value != null ? Number(value) : 0
  if (Number.isNaN(n)) return '0 ₽'
  return `${n.toLocaleString('ru-RU')} ₽`
}

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

function statusBadgeClass(status) {
  if (status === 'NEW' || status === 'IN_PROGRESS') return 'partner-program-badge partner-program-badge--pending'
  if (status === 'APPROVED') return 'partner-program-badge partner-program-badge--paid'
  if (status === 'REJECTED') return 'partner-program-badge partner-program-badge--rejected'
  return 'partner-program-badge partner-program-badge--muted'
}

function accrualStatusLabel(status, availableFrom) {
  if (status === 'ACCRUED') {
    if (availableFrom && new Date(availableFrom) > new Date()) {
      return 'Ожидает цикла выплаты'
    }
    return 'Начислено'
  }
  if (status === 'REVERSED') return 'Отменено'
  return status || '—'
}

function accrualStatusBadgeClass(status, availableFrom) {
  if (status === 'ACCRUED') {
    if (availableFrom && new Date(availableFrom) > new Date()) {
      return 'partner-program-badge partner-program-badge--pending'
    }
    return 'partner-program-badge partner-program-badge--paid'
  }
  if (status === 'REVERSED') return 'partner-program-badge partner-program-badge--rejected'
  return 'partner-program-badge partner-program-badge--muted'
}

function referralTypeLabel(type) {
  if (type === 'RESTAURANT') return 'Объект'
  if (type === 'COURIER') return 'Курьер'
  return type || '—'
}

function buildAccrualStatsByInvitee(accrualHistory) {
  const map = new Map()
  for (const item of accrualHistory || []) {
    if (item.status !== 'ACCRUED') continue
    const key = `${item.inviteeType}:${item.inviteeDisplayName || ''}`
    const prev = map.get(key) || { count: 0, total: 0 }
    map.set(key, {
      count: prev.count + 1,
      total: prev.total + (Number(item.amount) || 0),
    })
  }
  return map
}

function getInviteeStats(statsMap, inviteeType, displayName) {
  return statsMap.get(`${inviteeType}:${displayName || ''}`) || { count: 0, total: 0 }
}

function SegmentTabs({ tabs, active, onChange, className = '' }) {
  return (
    <div className={`partner-program-tabs ${className}`.trim()} role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={active === tab.id}
          className={`partner-program-tabs__btn${
            active === tab.id ? ' partner-program-tabs__btn--active' : ''
          }`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
          {tab.badge != null && tab.badge > 0 ? ` (${tab.badge})` : ''}
        </button>
      ))}
    </div>
  )
}

function EmptyState({ children }) {
  return <p className="partner-program-empty muted">{children}</p>
}

function CollapsibleBlock({ title, defaultOpen = false, children }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <section className={`partner-program-block partner-program-block--collapsible${open ? ' partner-program-block--open' : ''}`}>
      <button
        type="button"
        className="partner-program-block__toggle"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <h3 className="partner-program-block__title">{title}</h3>
        <span
          className={`partner-program-section__chevron${
            open ? ' partner-program-section__chevron--open' : ''
          }`}
          aria-hidden
        />
      </button>
      {open && <div className="partner-program-block__content">{children}</div>}
    </section>
  )
}

function CompactSummary({ program }) {
  const balance = program?.balance
  const statsParts = []
  if (balance?.balance > 0 || balance?.eligibleForRequest > 0) {
    statsParts.push(`Баланс ${formatMoney(balance?.balance)}`)
    if (balance?.eligibleForRequest > 0) {
      statsParts.push(`доступно к выплате ${formatMoney(balance?.eligibleForRequest)}`)
    }
  }
  statsParts.push(`приглашено ${program?.totalInvitations ?? 0}`)
  statsParts.push(`активных ${program?.pendingCount ?? 0}`)
  statsParts.push(`подключено ${program?.connectedCount ?? 0}`)

  return (
    <p className="partner-program-compact-summary muted">
      {statsParts.join(' · ')}
    </p>
  )
}

function InviteSection({ program, onCopy }) {
  const hasRestaurant = Boolean(program?.restaurantInviteUrl)
  const hasCourier = Boolean(program?.courierInviteUrl)
  const [inviteTab, setInviteTab] = useState(hasRestaurant ? 'restaurant' : 'courier')

  useEffect(() => {
    if (inviteTab === 'restaurant' && !hasRestaurant && hasCourier) setInviteTab('courier')
    if (inviteTab === 'courier' && !hasCourier && hasRestaurant) setInviteTab('restaurant')
  }, [hasRestaurant, hasCourier, inviteTab])

  if (!hasRestaurant && !hasCourier) return <EmptyState>Ссылки для приглашения недоступны.</EmptyState>

  const tabs = []
  if (hasRestaurant) tabs.push({ id: 'restaurant', label: 'Объект' })
  if (hasCourier) tabs.push({ id: 'courier', label: 'Курьер' })

  const activeConfig =
    inviteTab === 'courier'
      ? {
          title: 'Пригласить курьера',
          hint: 'Ссылка для кандидатов в курьеры — заявка на подключение к службе',
          url: program.courierInviteUrl,
        }
      : {
          title: 'Пригласить объект',
          hint: 'Ссылка для кафе и ресторанов — заявка на подключение к системе',
          url: program.restaurantInviteUrl,
        }

  if (!activeConfig.url) return null

  return (
    <>
      <p className="partner-program-block__lead muted">
        Поделитесь ссылкой или QR-кодом — начисления поступят после подключения приглашённого.
      </p>
      {tabs.length > 1 && (
        <SegmentTabs tabs={tabs} active={inviteTab} onChange={setInviteTab} className="partner-program-tabs--compact" />
      )}
      <div className="partner-program-invite-compact">
        <div className="partner-program-invite-compact__main">
          <p className="partner-program-invite-compact__hint muted">{activeConfig.hint}</p>
          <p className="partner-program-invite-compact__url">{activeConfig.url}</p>
          <button
            type="button"
            className="btn btn-secondary partner-program-invite-compact__copy"
            onClick={() => onCopy(activeConfig.url, activeConfig.title)}
          >
            Скопировать ссылку
          </button>
          {program.partnerCode && (
            <p className="muted partner-program-invite-compact__code">Код: {program.partnerCode}</p>
          )}
        </div>
        <div className="partner-program-invite-compact__qr">
          <QrCodeImage value={activeConfig.url} size={120} />
        </div>
      </div>
    </>
  )
}

function ConnectedReferralRow({ referral, inviteeType, stats }) {
  return (
    <article className="partner-program-list-row">
      <div className="partner-program-list-row__head">
        <strong className="partner-program-list-row__title">{referral.displayName}</strong>
        <span className="partner-program-badge partner-program-badge--type">
          {referralTypeLabel(inviteeType)}
        </span>
      </div>
      <dl className="partner-program-list-row__meta">
        <div>
          <dt>Подключён</dt>
          <dd>{formatDate(referral.connectedAt)}</dd>
        </div>
        <div>
          <dt>Начислений</dt>
          <dd>{stats.count}</dd>
        </div>
        <div>
          <dt>Принёс всего</dt>
          <dd>{formatMoney(stats.total)}</dd>
        </div>
      </dl>
      {referral.programExpiresAt && (
        <p className="muted partner-program-list-row__extra">Действует до: {formatDate(referral.programExpiresAt)}</p>
      )}
    </article>
  )
}

function PendingReferralRow({ referral }) {
  return (
    <article className="partner-program-list-row">
      <div className="partner-program-list-row__head">
        <strong className="partner-program-list-row__title">{referral.displayName}</strong>
        <span className={statusBadgeClass(referral.status)}>{statusLabel(referral.status)}</span>
      </div>
      <dl className="partner-program-list-row__meta">
        <div>
          <dt>Тип</dt>
          <dd>{referralTypeLabel(referral.referralType)}</dd>
        </div>
        <div>
          <dt>Подана</dt>
          <dd>{formatDate(referral.submittedAt)}</dd>
        </div>
      </dl>
    </article>
  )
}

function InvitedSection({ program }) {
  const statsMap = useMemo(
    () => buildAccrualStatsByInvitee(program?.accrualHistory),
    [program?.accrualHistory],
  )

  const pendingReferrals = useMemo(
    () => (program?.referrals || []).filter((ref) => ref.status !== 'APPROVED'),
    [program?.referrals],
  )

  const tabs = useMemo(() => {
    const items = [
      { id: 'couriers', label: 'Курьеры', badge: program?.connectedCouriers?.length || 0 },
      { id: 'objects', label: 'Объекты', badge: program?.connectedRestaurants?.length || 0 },
    ]
    if (pendingReferrals.length > 0) {
      items.push({ id: 'pending', label: 'Заявки', badge: pendingReferrals.length })
    }
    return items
  }, [program?.connectedCouriers?.length, program?.connectedRestaurants?.length, pendingReferrals.length])

  const [invitedTab, setInvitedTab] = useState('couriers')
  const [showAllCouriers, setShowAllCouriers] = useState(false)
  const [showAllObjects, setShowAllObjects] = useState(false)
  const [showAllPending, setShowAllPending] = useState(false)

  useEffect(() => {
    if (!tabs.some((tab) => tab.id === invitedTab)) {
      setInvitedTab(tabs[0]?.id || 'couriers')
    }
  }, [tabs, invitedTab])

  const couriers = program?.connectedCouriers || []
  const objects = program?.connectedRestaurants || []
  const visibleCouriers = showAllCouriers ? couriers : couriers.slice(0, INVITED_PREVIEW_LIMIT)
  const visibleObjects = showAllObjects ? objects : objects.slice(0, INVITED_PREVIEW_LIMIT)
  const visiblePending = showAllPending
    ? pendingReferrals
    : pendingReferrals.slice(0, INVITED_PREVIEW_LIMIT)

  return (
    <>
      <SegmentTabs tabs={tabs} active={invitedTab} onChange={setInvitedTab} className="partner-program-tabs--compact" />

      {invitedTab === 'couriers' && (
        <>
          {!couriers.length ? (
            <EmptyState>Пока нет подключённых курьеров.</EmptyState>
          ) : (
            <div className="partner-program-list">
              {visibleCouriers.map((referral) => (
                <ConnectedReferralRow
                  key={referral.referralId}
                  referral={referral}
                  inviteeType="COURIER"
                  stats={getInviteeStats(statsMap, 'COURIER', referral.displayName)}
                />
              ))}
              {couriers.length > INVITED_PREVIEW_LIMIT && (
                <button
                  type="button"
                  className="btn btn-secondary partner-program-block__more"
                  onClick={() => setShowAllCouriers((v) => !v)}
                >
                  {showAllCouriers ? 'Свернуть' : `Показать все (${couriers.length})`}
                </button>
              )}
            </div>
          )}
        </>
      )}

      {invitedTab === 'objects' && (
        <>
          {!objects.length ? (
            <EmptyState>Пока нет подключённых объектов.</EmptyState>
          ) : (
            <div className="partner-program-list">
              {visibleObjects.map((referral) => (
                <ConnectedReferralRow
                  key={referral.referralId}
                  referral={referral}
                  inviteeType="RESTAURANT"
                  stats={getInviteeStats(statsMap, 'RESTAURANT', referral.displayName)}
                />
              ))}
              {objects.length > INVITED_PREVIEW_LIMIT && (
                <button
                  type="button"
                  className="btn btn-secondary partner-program-block__more"
                  onClick={() => setShowAllObjects((v) => !v)}
                >
                  {showAllObjects ? 'Свернуть' : `Показать все (${objects.length})`}
                </button>
              )}
            </div>
          )}
        </>
      )}

      {invitedTab === 'pending' && pendingReferrals.length > 0 && (
        <div className="partner-program-list">
          {visiblePending.map((referral) => (
            <PendingReferralRow key={`${referral.referralType}-${referral.requestId}`} referral={referral} />
          ))}
          {pendingReferrals.length > INVITED_PREVIEW_LIMIT && (
            <button
              type="button"
              className="btn btn-secondary partner-program-block__more"
              onClick={() => setShowAllPending((v) => !v)}
            >
              {showAllPending ? 'Свернуть' : `Показать все (${pendingReferrals.length})`}
            </button>
          )}
        </div>
      )}
    </>
  )
}

function AccrualsSection({ program }) {
  const [showAll, setShowAll] = useState(false)
  const accruals = program?.accrualHistory || []
  const visible = showAll ? accruals : accruals.slice(0, HISTORY_PREVIEW_LIMIT)

  if (!accruals.length) return <EmptyState>Начислений пока нет.</EmptyState>

  return (
    <div className="partner-program-list">
      {visible.map((item) => (
        <article key={item.id} className="partner-program-list-row">
          <div className="partner-program-list-row__head">
            <strong className="partner-program-list-row__title">{formatMoney(item.amount)}</strong>
            <span className={accrualStatusBadgeClass(item.status, item.availableFrom)}>
              {accrualStatusLabel(item.status, item.availableFrom)}
            </span>
          </div>
          <dl className="partner-program-list-row__meta">
            <div>
              <dt>Дата</dt>
              <dd>{formatDate(item.createdAt)}</dd>
            </div>
            <div>
              <dt>За кого</dt>
              <dd>
                {referralTypeLabel(item.inviteeType)}: {item.inviteeDisplayName || '—'}
              </dd>
            </div>
            {item.accrualPeriodMonth && (
              <div>
                <dt>Период</dt>
                <dd>{item.accrualPeriodMonth}</dd>
              </div>
            )}
            {item.availableFrom && new Date(item.availableFrom) > new Date() && (
              <div>
                <dt>Доступно для заявки</dt>
                <dd>{formatDate(item.availableFrom)}</dd>
              </div>
            )}
          </dl>
        </article>
      ))}
      {accruals.length > HISTORY_PREVIEW_LIMIT && (
        <button
          type="button"
          className="btn btn-secondary partner-program-block__more"
          onClick={() => setShowAll((v) => !v)}
        >
          {showAll ? 'Свернуть' : `Показать всё (${accruals.length})`}
        </button>
      )}
    </div>
  )
}

function payoutMethodLabel(method) {
  if (method === 'BANK_TRANSFER') return 'Банковский перевод'
  if (method === 'TRANSFER_TO_MAIN_BALANCE') return 'На общий баланс'
  return method || '—'
}

function LegacyPayoutSection({
  program,
  payoutAmount,
  setPayoutAmount,
  payoutMethod,
  setPayoutMethod,
  transferType,
  setTransferType,
  cardNumber,
  setCardNumber,
  phoneNumber,
  setPhoneNumber,
  recipientName,
  setRecipientName,
  bankName,
  setBankName,
  payoutSubmitting,
  onSubmitPayout,
}) {
  const available = Number(program?.balance?.eligibleForRequest) || 0
  const minPayout = Number(program?.minPayoutAmount) || 0
  const methods = program?.availablePayoutMethods || []
  const singleMethod = methods.length === 1
  const needsBankDetails = payoutMethod === 'BANK_TRANSFER'
  const bankDetailsValid =
    !needsBankDetails
    || isValidBankPayoutDetails({ transferType, cardNumber, phoneNumber, recipientName, bankName })
  const canSubmit = available > 0 && available >= minPayout && bankDetailsValid

  let unavailableHint = ''
  if (available <= 0) {
    unavailableHint = 'Сейчас нет средств, доступных к выплате.'
  } else if (available < minPayout) {
    unavailableHint = `Минимальная сумма выплаты — ${formatMoney(minPayout)}. Доступно ${formatMoney(available)}.`
  }

  return (
    <>
      <p className="partner-program-block__lead muted">
        Доступно: <strong>{formatMoney(available)}</strong>
      </p>
      <div className="partner-program-field">
        <label className="partner-program-field__label" htmlFor="partner-payout-amount">
          Сумма
        </label>
        <input
          id="partner-payout-amount"
          type="number"
          min="0"
          step="0.01"
          className="partner-program-field__control"
          value={payoutAmount}
          onChange={(e) => setPayoutAmount(e.target.value)}
          disabled={!canSubmit || payoutSubmitting}
        />
        {minPayout > 0 && (
          <p className="partner-program-field__hint muted">Минимум: {formatMoney(minPayout)}</p>
        )}
        {unavailableHint && (
          <p className="partner-program-field__hint partner-program-field__hint--warn">{unavailableHint}</p>
        )}
      </div>
      <div className="partner-program-field">
        <span className="partner-program-field__label">Способ выплаты</span>
        {singleMethod ? (
          <p className="partner-program-payout-method-static">{payoutMethodLabel(methods[0])}</p>
        ) : (
          <select
            className="partner-program-field__control"
            value={payoutMethod}
            onChange={(e) => setPayoutMethod(e.target.value)}
            disabled={payoutSubmitting}
          >
            {methods.map((method) => (
              <option key={method} value={method}>
                {payoutMethodLabel(method)}
              </option>
            ))}
          </select>
        )}
      </div>
      {needsBankDetails && (
        <PartnerBankPayoutFields
          transferType={transferType}
          onTransferTypeChange={setTransferType}
          cardNumber={cardNumber}
          phoneNumber={phoneNumber}
          recipientName={recipientName}
          bankName={bankName}
          onCardNumberChange={setCardNumber}
          onPhoneNumberChange={setPhoneNumber}
          onRecipientNameChange={setRecipientName}
          onBankNameChange={setBankName}
          disabled={payoutSubmitting}
          idPrefix="restaurant-partner-payout"
        />
      )}
      <button
        type="button"
        className="btn partner-program-block__action"
        disabled={payoutSubmitting || !canSubmit}
        onClick={onSubmitPayout}
      >
        {payoutSubmitting ? 'Отправка…' : 'Создать заявку'}
      </button>
    </>
  )
}

export default function PartnerProgramSection({
  loadProgram,
  createPayout,
  disabled = false,
  disabledMessage,
  onProgramLoaded,
}) {
  const location = useLocation()
  const panelId = useId()
  const [expanded, setExpanded] = useState(() => location.hash === PARTNER_PROGRAM_HASH)
  const [program, setProgram] = useState(null)
  const [visibility, setVisibility] = useState(disabled ? 'disabled' : 'checking')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [snackbar, setSnackbar] = useState(null)
  const [payoutAmount, setPayoutAmount] = useState('')
  const [payoutMethod, setPayoutMethod] = useState('BANK_TRANSFER')
  const [payoutTransferType, setPayoutTransferType] = useState('')
  const [payoutCardNumber, setPayoutCardNumber] = useState('')
  const [payoutPhoneNumber, setPayoutPhoneNumber] = useState('')
  const [payoutRecipientName, setPayoutRecipientName] = useState('')
  const [payoutBankName, setPayoutBankName] = useState('')
  const [payoutSubmitting, setPayoutSubmitting] = useState(false)

  const reload = useCallback(async () => {
    if (disabled || !loadProgram) {
      setProgram(null)
      setLoading(false)
      return null
    }
    setLoading(true)
    setError('')
    try {
      const data = await loadProgram()
      if (data?.enabled === false) {
        setProgram(null)
        setVisibility('hidden')
        onProgramLoaded?.(null)
        return null
      }
      setProgram(data)
      setVisibility('ready')
      onProgramLoaded?.(data)
      return data
    } catch (e) {
      setProgram(null)
      setVisibility('hidden')
      onProgramLoaded?.(null)
      setError(e?.message || 'Не удалось загрузить партнёрскую программу')
      return null
    } finally {
      setLoading(false)
    }
  }, [disabled, loadProgram, onProgramLoaded])

  useEffect(() => {
    if (disabled || !loadProgram) {
      setVisibility('disabled')
      return undefined
    }

    let cancelled = false
    setVisibility('checking')
    loadProgram()
      .then((data) => {
        if (cancelled) return
        if (data?.enabled === false) {
          setVisibility('hidden')
          setProgram(null)
          onProgramLoaded?.(null)
          return
        }
        setProgram(data)
        setVisibility('ready')
        onProgramLoaded?.(data)
      })
      .catch(() => {
        if (cancelled) return
        setVisibility('hidden')
        setProgram(null)
        onProgramLoaded?.(null)
      })

    return () => {
      cancelled = true
    }
  }, [disabled, loadProgram, onProgramLoaded])

  useEffect(() => {
    if (location.hash === PARTNER_PROGRAM_HASH) {
      setExpanded(true)
    }
  }, [location.hash])

  useEffect(() => {
    if (expanded && visibility === 'ready') {
      reload()
    }
  }, [expanded, visibility, reload])

  useEffect(() => {
    if (program?.availablePayoutMethods?.length) {
      setPayoutMethod(program.availablePayoutMethods[0])
    }
    setPayoutTransferType(getLastTransferType())
  }, [program?.availablePayoutMethods])

  const onSubmitPayout = async () => {
    if (!createPayout) return
    const amount = Number(payoutAmount)
    if (!amount || amount <= 0) {
      setSnackbar({ message: 'Укажите сумму выплаты', variant: 'error' })
      return
    }
    if (payoutMethod === 'BANK_TRANSFER' && !isValidBankPayoutDetails({
      transferType: payoutTransferType,
      cardNumber: payoutCardNumber,
      phoneNumber: payoutPhoneNumber,
      recipientName: payoutRecipientName,
      bankName: payoutBankName,
    })) {
      setSnackbar({ message: 'Укажите реквизиты для получения выплаты', variant: 'error' })
      return
    }
    setPayoutSubmitting(true)
    try {
      const body = { amount, payoutMethod }
      if (payoutMethod === 'BANK_TRANSFER') {
        body.payoutDetails = buildPayoutDetailsPayload({
          transferType: payoutTransferType,
          cardNumber: payoutCardNumber,
          phoneNumber: payoutPhoneNumber,
          recipientName: payoutRecipientName,
          bankName: payoutBankName,
        })
        saveLastTransferType(payoutTransferType)
      }
      await createPayout(body)
      setPayoutAmount('')
      setPayoutCardNumber('')
      setPayoutPhoneNumber('')
      setPayoutRecipientName('')
      setPayoutBankName('')
      setSnackbar({ message: 'Заявка на выплату создана', variant: 'success' })
      await reload()
    } catch (e) {
      const message =
        e?.error === 'payout_once_per_month'
          ? 'Партнёрскую выплату можно запросить не чаще одного раза в календарный месяц'
          : e?.error === 'partner_payout_details_required'
            ? 'Укажите реквизиты для получения выплаты'
            : e?.message || 'Не удалось создать заявку'
      setSnackbar({ message, variant: 'error' })
    } finally {
      setPayoutSubmitting(false)
    }
  }

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
        <h2 className="role-profile-section__title">Партнёрская программа</h2>
        <p className="muted partner-program-section__intro">
          {disabledMessage || 'Раздел будет доступен после активации аккаунта.'}
        </p>
      </section>
    )
  }

  if (visibility === 'checking' || visibility === 'hidden') {
    return null
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
        <span className="role-profile-section__title">Партнёрская программа</span>
        <span
          className={`partner-program-section__chevron${
            expanded ? ' partner-program-section__chevron--open' : ''
          }`}
          aria-hidden
        />
      </button>

      {program && <CompactSummary program={program} />}

      {!expanded && (
        <p className="muted partner-program-section__intro">
          Приглашайте объекты и курьеров, получайте начисления за их активность.
        </p>
      )}

      {expanded && (
        <div id={panelId} className="partner-program-section__body">
          {loading && <p className="muted">Загрузка…</p>}
          {error && <p className="partner-program-state__error-text">{error}</p>}

          {program && !loading && (
            <div className="partner-program-layout">
              {createPayout && (
                <CollapsibleBlock title="Заявка на выплату">
                  <LegacyPayoutSection
                    program={program}
                    payoutAmount={payoutAmount}
                    setPayoutAmount={setPayoutAmount}
                    payoutMethod={payoutMethod}
                    setPayoutMethod={setPayoutMethod}
                    transferType={payoutTransferType}
                    setTransferType={setPayoutTransferType}
                    cardNumber={payoutCardNumber}
                    setCardNumber={setPayoutCardNumber}
                    phoneNumber={payoutPhoneNumber}
                    setPhoneNumber={setPayoutPhoneNumber}
                    recipientName={payoutRecipientName}
                    setRecipientName={setPayoutRecipientName}
                    bankName={payoutBankName}
                    setBankName={setPayoutBankName}
                    payoutSubmitting={payoutSubmitting}
                    onSubmitPayout={onSubmitPayout}
                  />
                </CollapsibleBlock>
              )}
              <CollapsibleBlock title="Приглашённые">
                <InvitedSection program={program} />
              </CollapsibleBlock>
              <CollapsibleBlock title="Начисления">
                <AccrualsSection program={program} />
              </CollapsibleBlock>
              <CollapsibleBlock title="Пригласить">
                <InviteSection program={program} onCopy={onCopy} />
              </CollapsibleBlock>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
