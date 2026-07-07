import { useCallback, useEffect, useMemo, useState } from 'react'
import AppModal from '../AppModal.jsx'
import CopySnackbar from '../CopySnackbar.jsx'
import CourierPayoutFormModal from './CourierPayoutFormModal.jsx'
import {
  createCourierBalancePayout,
  createCourierPartnerBalanceTransfer,
  createCourierPartnerPayout,
  fetchCourierBalance,
  fetchCourierPartnerProgram,
} from '../../api/deliveryService.js'

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

function formatScheduledPayoutDate(isoDate) {
  if (!isoDate) return null
  try {
    const [year, month, day] = isoDate.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    const monthName = date.toLocaleDateString('ru-RU', { month: 'long' })
    return `Выплата будет назначена на ${day} ${monthName} ${year} года`
  } catch {
    return null
  }
}

function mainPayoutStatusLabel(status) {
  if (status === 'PENDING') return 'Ожидает обработки'
  if (status === 'PAID') return 'Выплачено'
  if (status === 'REJECTED') return 'Отклонено'
  if (status === 'CANCELLED') return 'Отменено'
  return status || '—'
}

function formatDateOnly(isoDate) {
  if (!isoDate) return '—'
  try {
    if (typeof isoDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(isoDate)) {
      const [year, month, day] = isoDate.split('-').map(Number)
      return new Date(year, month - 1, day).toLocaleDateString('ru-RU')
    }
    return formatDate(isoDate)
  } catch {
    return '—'
  }
}

function partnerBalanceTransferStatusLabel(status) {
  if (status === 'SCHEDULED') return 'Запланировано'
  if (status === 'COMPLETED') return 'Зачислено на общий баланс'
  return status || '—'
}

function partnerPayoutStatusLabel(status) {
  if (status === 'PENDING') return 'Ожидает обработки'
  if (status === 'SCHEDULED') return 'Ожидает даты выплаты'
  if (status === 'PROCESSING') return 'В обработке'
  if (status === 'PAID') return 'Выплачено'
  if (status === 'REJECTED') return 'Отклонено'
  if (status === 'CANCELLED') return 'Отменено'
  return status || '—'
}

function payoutMethodLabel(method) {
  if (method === 'BANK_TRANSFER') return 'Банковский перевод'
  if (method === 'TRANSFER_TO_MAIN_BALANCE') return 'На общий баланс'
  return method || '—'
}

function statusBadgeClass(status) {
  if (status === 'PENDING' || status === 'SCHEDULED' || status === 'IN_PROGRESS') {
    return 'partner-program-badge partner-program-badge--pending'
  }
  if (status === 'PAID' || status === 'APPROVED' || status === 'COMPLETED') {
    return 'partner-program-badge partner-program-badge--paid'
  }
  if (status === 'REJECTED' || status === 'CANCELLED') {
    return 'partner-program-badge partner-program-badge--rejected'
  }
  return 'partner-program-badge partner-program-badge--muted'
}

function PartnerManageModal({ open, program, onClose, onSelectAction }) {
  const methods = program?.availablePayoutMethods || []
  const hasBank = methods.includes('BANK_TRANSFER')
  const hasTransfer = methods.includes('TRANSFER_TO_MAIN_BALANCE')
  const balance = program?.balance
  const eligible = Number(balance?.eligibleForRequest) || 0
  const canCreate = Boolean(balance?.canCreatePayoutRequest)
  const scheduledHint = formatScheduledPayoutDate(balance?.nextScheduledPayoutDate)

  return (
    <AppModal open={open} title="Партнёрский баланс" onClose={onClose}>
      <p className="courier-balance-modal__available muted">
        Доступно: <strong>{formatMoney(eligible)}</strong>
      </p>
      {scheduledHint && (
        <p className="partner-program-field__hint muted">{scheduledHint}</p>
      )}
      {!canCreate && Number(balance?.awaitingExecution) > 0 && (
        <p className="partner-program-field__hint partner-program-field__hint--warn">
          Ожидает исполнения: {formatMoney(balance.awaitingExecution)}.
        </p>
      )}
      {!canCreate && eligible > 0 && (
        <p className="partner-program-field__hint partner-program-field__hint--warn">
          В ближайшем выплатном цикле операция уже создана.
        </p>
      )}
      <div className="courier-balance-manage-actions">
        {hasBank && (
          <button
            type="button"
            className="btn btn-secondary courier-balance-manage-actions__btn"
            disabled={!canCreate}
            onClick={() => onSelectAction('BANK_TRANSFER')}
          >
            Вывести на карту
          </button>
        )}
        {hasTransfer && (
          <button
            type="button"
            className="btn btn-secondary courier-balance-manage-actions__btn"
            disabled={!canCreate}
            onClick={() => onSelectAction('TRANSFER_TO_MAIN_BALANCE')}
          >
            Перевести на общий баланс
          </button>
        )}
        {!hasBank && !hasTransfer && (
          <p className="muted">Способы выплаты не настроены.</p>
        )}
      </div>
    </AppModal>
  )
}

export default function CourierBalanceSection({ memberId, disabled, onBalanceChange }) {
  const [mainBalance, setMainBalance] = useState(null)
  const [partnerProgram, setPartnerProgram] = useState(null)
  const [partnerVisible, setPartnerVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [snackbar, setSnackbar] = useState(null)

  const [mainPayoutOpen, setMainPayoutOpen] = useState(false)
  const [partnerManageOpen, setPartnerManageOpen] = useState(false)
  const [partnerPayoutOpen, setPartnerPayoutOpen] = useState(false)
  const [partnerPayoutMethod, setPartnerPayoutMethod] = useState('BANK_TRANSFER')
  const [submitting, setSubmitting] = useState(false)

  const [historyFilter, setHistoryFilter] = useState('all')

  const reload = useCallback(async () => {
    if (disabled || !memberId) {
      setMainBalance(null)
      setPartnerProgram(null)
      setPartnerVisible(false)
      return
    }
    setLoading(true)
    try {
      const [balanceData, programData] = await Promise.all([
        fetchCourierBalance(memberId),
        fetchCourierPartnerProgram(memberId).catch(() => null),
      ])
      setMainBalance(balanceData)
      if (programData?.enabled === false) {
        setPartnerProgram(null)
        setPartnerVisible(false)
      } else {
        setPartnerProgram(programData)
        setPartnerVisible(true)
      }
      onBalanceChange?.(balanceData)
    } catch {
      setMainBalance(null)
      setPartnerProgram(null)
      setPartnerVisible(false)
    } finally {
      setLoading(false)
    }
  }, [disabled, memberId, onBalanceChange])

  useEffect(() => {
    reload()
  }, [reload])

  const partnerMin = Number(partnerProgram?.minPayoutAmount) || 0
  const mainCanCreate =
    mainBalance?.canCreatePayoutRequest !== undefined
      ? Boolean(mainBalance.canCreatePayoutRequest)
      : Number(mainBalance?.availableForPayout) > 0

  const [mainPayoutFieldErrors, setMainPayoutFieldErrors] = useState({})

  const unifiedPayouts = useMemo(() => {
    const earnings = (mainBalance?.earningHistory || []).map((item) => ({
      id: item.id,
      amount: item.amount,
      status: 'EARNING',
      payoutMethod: null,
      createdAt: item.createdAt,
      source: 'earning',
      sourceLabel: item.orderPublicNumber
        ? `Доставка №${item.orderPublicNumber}`
        : 'Начисление за доставку',
    }))
    const main = (mainBalance?.payoutHistory || []).map((item) => ({
      ...item,
      source: 'main',
      sourceLabel: 'Общий баланс — заявка',
    }))
    const partner = (partnerProgram?.payoutHistory || []).map((item) => ({
      ...item,
      source: 'partner',
      sourceLabel: 'Партнёрский баланс',
      operationType: 'Банковский перевод',
    }))
    const transfers = (partnerProgram?.balanceTransferHistory || []).map((item) => ({
      ...item,
      source: 'partner_transfer',
      sourceLabel: 'Партнёрский баланс',
      operationType: 'Перевод на общий баланс',
      createdAt: item.createdAt,
    }))
    return [...earnings, ...main, ...partner, ...transfers].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
  }, [
    mainBalance?.earningHistory,
    mainBalance?.payoutHistory,
    partnerProgram?.payoutHistory,
    partnerProgram?.balanceTransferHistory,
  ])

  const filteredPayouts = useMemo(() => {
    if (historyFilter === 'main') {
      return unifiedPayouts.filter((p) => p.source === 'main' || p.source === 'earning')
    }
    if (historyFilter === 'partner') {
      return unifiedPayouts.filter(
        (p) => p.source === 'partner' || p.source === 'partner_transfer',
      )
    }
    return unifiedPayouts
  }, [historyFilter, unifiedPayouts])

  const handleMainPayout = async (body) => {
    setSubmitting(true)
    setMainPayoutFieldErrors({})
    try {
      await createCourierBalancePayout(memberId, body)
      setMainPayoutOpen(false)
      setSnackbar({ message: 'Заявка на выплату создана', variant: 'success' })
      await reload()
    } catch (e) {
      if (e?.conflictField) {
        setMainPayoutFieldErrors({ [e.conflictField]: e.message })
        return
      }
      setSnackbar({ message: e?.message || 'Не удалось создать заявку', variant: 'error' })
    } finally {
      setSubmitting(false)
    }
  }

  const handlePartnerPayout = async (body) => {
    setSubmitting(true)
    try {
      if (body.payoutMethod === 'TRANSFER_TO_MAIN_BALANCE') {
        await createCourierPartnerBalanceTransfer(memberId, { amount: body.amount })
        setSnackbar({ message: 'Средства зачислены на общий баланс', variant: 'success' })
      } else {
        await createCourierPartnerPayout(memberId, body)
        setSnackbar({ message: 'Заявка на выплату создана', variant: 'success' })
      }
      setPartnerPayoutOpen(false)
      setPartnerManageOpen(false)
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
      setSubmitting(false)
    }
  }

  if (disabled) return null

  return (
    <>
      <CopySnackbar
        message={snackbar?.message}
        variant={snackbar?.variant}
        onDismiss={() => setSnackbar(null)}
      />

      <section className="courier-balance-cards">
        <article className="card courier-balance-card">
          <h2 className="courier-balance-card__title">Общий баланс</h2>
          {loading && !mainBalance ? (
            <p className="muted">Загрузка…</p>
          ) : (
            <>
              <p className="courier-balance-card__amount">{formatMoney(mainBalance?.balance)}</p>
              <p className="courier-balance-card__available muted">
                Доступно к выплате: {formatMoney(mainBalance?.availableForPayout)}
              </p>
              {!mainCanCreate && Number(mainBalance?.availableForPayout) > 0 && (
                <p className="partner-program-field__hint partner-program-field__hint--warn">
                  У вас уже есть активная заявка на выплату.
                </p>
              )}
              <button
                type="button"
                className="btn courier-balance-card__action"
                disabled={!mainCanCreate}
                onClick={() => {
                  setMainPayoutFieldErrors({})
                  setMainPayoutOpen(true)
                }}
              >
                Запросить выплату
              </button>
            </>
          )}
        </article>

        {partnerVisible && (
          <article className="card courier-balance-card courier-balance-card--partner">
            <h2 className="courier-balance-card__title">Партнёрский баланс</h2>
            <p className="courier-balance-card__amount">
              {formatMoney(partnerProgram?.balance?.balance)}
            </p>
            {Number(partnerProgram?.balance?.eligibleForRequest) > 0 && (
              <p className="courier-balance-card__available muted">
                Доступно к выплате: {formatMoney(partnerProgram.balance.eligibleForRequest)}
              </p>
            )}
            {Number(partnerProgram?.balance?.accruedNotYetEligible) > 0 && (
              <p className="courier-balance-card__available muted">
                Начислено за текущий период: {formatMoney(partnerProgram.balance.accruedNotYetEligible)}
              </p>
            )}
            {Number(partnerProgram?.balance?.awaitingExecution) > 0 && (
              <p className="courier-balance-card__available muted">
                Ожидает исполнения: {formatMoney(partnerProgram.balance.awaitingExecution)}
              </p>
            )}
            {Number(partnerProgram?.balance?.carriedOver) > 0 && (
              <p className="courier-balance-card__available muted">
                Перенесено на следующий месяц: {formatMoney(partnerProgram.balance.carriedOver)}
              </p>
            )}
            <button
              type="button"
              className="btn btn-secondary courier-balance-card__action"
              onClick={() => setPartnerManageOpen(true)}
            >
              Управлять
            </button>
          </article>
        )}
      </section>

      <section className="card role-profile-section courier-payout-history">
        <h2 className="role-profile-section__title">История операций</h2>
        <div className="partner-program-tabs partner-program-tabs--compact courier-payout-history__filter">
          {[
            { id: 'all', label: 'Все' },
            { id: 'main', label: 'Общий баланс' },
            { id: 'partner', label: 'Партнёрский' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`partner-program-tabs__btn${
                historyFilter === tab.id ? ' partner-program-tabs__btn--active' : ''
              }`}
              onClick={() => setHistoryFilter(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        {!filteredPayouts.length ? (
          <p className="partner-program-empty muted">Операций пока нет.</p>
        ) : (
          <div className="partner-program-list">
            {filteredPayouts.map((item) => (
              <article key={`${item.source}-${item.id}`} className="partner-program-list-row">
                <div className="partner-program-list-row__head">
                  <strong className="partner-program-list-row__title">
                    {item.source === 'earning' ? '+' : ''}{formatMoney(item.amount)}
                  </strong>
                  <span className={statusBadgeClass(item.status)}>
                    {item.source === 'earning'
                      ? 'Начисление'
                      : item.source === 'partner_transfer'
                        ? partnerBalanceTransferStatusLabel(item.status)
                        : item.source === 'main'
                          ? mainPayoutStatusLabel(item.status)
                          : partnerPayoutStatusLabel(item.status)}
                  </span>
                </div>
                <dl className="partner-program-list-row__meta">
                  <div>
                    <dt>Тип</dt>
                    <dd>
                      {item.operationType
                        || (item.source === 'earning'
                          ? item.sourceLabel
                          : item.source === 'main'
                            ? 'Вывод на карту'
                            : item.payoutMethod
                              ? payoutMethodLabel(item.payoutMethod)
                              : item.sourceLabel)}
                    </dd>
                  </div>
                  {item.source !== 'earning' && (
                    <div>
                      <dt>Источник</dt>
                      <dd>{item.sourceLabel}</dd>
                    </div>
                  )}
                  {item.source === 'partner_transfer' && item.status === 'SCHEDULED' && item.scheduledExecutionDate && (
                    <div>
                      <dt>Дата исполнения</dt>
                      <dd>{formatDateOnly(item.scheduledExecutionDate)}</dd>
                    </div>
                  )}
                  {item.source === 'partner_transfer' && item.status === 'COMPLETED' && item.executedAt && (
                    <div>
                      <dt>Дата зачисления</dt>
                      <dd>{formatDate(item.executedAt)}</dd>
                    </div>
                  )}
                  {item.source === 'partner' && item.cardMask && (
                    <div>
                      <dt>{item.transferType === 'SBP_PHONE' ? 'Телефон' : 'Карта'}</dt>
                      <dd>{item.cardMask}</dd>
                    </div>
                  )}
                  {item.source === 'main' && item.cardMask && (
                    <div>
                      <dt>{item.transferType === 'SBP_PHONE' ? 'Телефон' : 'Карта'}</dt>
                      <dd>{item.cardMask}</dd>
                    </div>
                  )}
                  {(item.source === 'partner' || item.source === 'main') && item.rejectionComment && (
                    <div>
                      <dt>Причина отклонения</dt>
                      <dd>{item.rejectionComment}</dd>
                    </div>
                  )}
                  {item.source === 'partner' && item.scheduledPayoutDate && (
                    <div>
                      <dt>Дата выплаты</dt>
                      <dd>{formatDateOnly(item.scheduledPayoutDate)}</dd>
                    </div>
                  )}
                  {item.source !== 'partner_transfer' && (
                    <div>
                      <dt>Дата</dt>
                      <dd>{formatDate(item.createdAt)}</dd>
                    </div>
                  )}
                  {item.source === 'partner_transfer' && item.status === 'SCHEDULED' && (
                    <div>
                      <dt>Создано</dt>
                      <dd>{formatDate(item.createdAt)}</dd>
                    </div>
                  )}
                </dl>
              </article>
            ))}
          </div>
        )}
      </section>

      <CourierPayoutFormModal
        open={mainPayoutOpen}
        title="Заявка на выплату"
        available={mainBalance?.availableForPayout}
        methods={['BANK_TRANSFER']}
        submitting={submitting}
        serverFieldErrors={mainPayoutFieldErrors}
        onClose={() => {
          setMainPayoutFieldErrors({})
          setMainPayoutOpen(false)
        }}
        onSubmit={handleMainPayout}
      />

      <PartnerManageModal
        open={partnerManageOpen}
        program={partnerProgram}
        onClose={() => setPartnerManageOpen(false)}
        onSelectAction={(method) => {
          setPartnerPayoutMethod(method)
          setPartnerManageOpen(false)
          setPartnerPayoutOpen(true)
        }}
      />

      <CourierPayoutFormModal
        open={partnerPayoutOpen}
        title={
          partnerPayoutMethod === 'TRANSFER_TO_MAIN_BALANCE'
            ? 'Перевод на общий баланс'
            : 'Вывод на карту'
        }
        available={partnerProgram?.balance?.eligibleForRequest}
        minAmount={partnerMin}
        methods={[partnerPayoutMethod]}
        defaultMethod={partnerPayoutMethod}
        submitting={submitting}
        scheduledPayoutDate={partnerProgram?.balance?.nextScheduledPayoutDate}
        submitLabel={
          partnerPayoutMethod === 'TRANSFER_TO_MAIN_BALANCE' ? 'Запланировать перевод' : 'Создать заявку'
        }
        onClose={() => setPartnerPayoutOpen(false)}
        onSubmit={handlePartnerPayout}
      />
    </>
  )
}

export { formatMoney as formatCourierMoney }
