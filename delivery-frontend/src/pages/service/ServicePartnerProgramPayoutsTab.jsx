import { useCallback, useEffect, useState } from 'react'
import {
  fetchPartnerPayoutRequests,
  processCourierBalancePayout,
  processPartnerPayout,
  takePartnerPayoutInWork,
} from '../../api/deliveryService.js'
import AppModal from '../../components/AppModal.jsx'
import CopySnackbar from '../../components/CopySnackbar.jsx'
import EmptyState from '../../components/EmptyState.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'
import {
  formatCardNumberForDisplay,
  formatPhoneNumberForDisplay,
  PAYOUT_TRANSFER_TYPE_SBP,
  transferTypeLabel,
} from '../../utils/partnerBankPayout.js'
import {
  balanceSourceLabel,
  formatDateOnly,
  formatDateTime,
  formatMoney,
  participantTypeLabel,
  payoutMethodLabel,
  payoutStatusBadgeClass,
  payoutStatusLabel,
} from '../../utils/partnerProgramAdminUi.js'

function mapPayoutError(err) {
  if (err?.error === 'payout_once_per_month') {
    return 'Партнёрскую выплату можно запросить не чаще одного раза в календарный месяц'
  }
  if (err?.error === 'partner_payout_date_not_reached') {
    return 'Подтвердить выплату можно с 1-го числа месяца выплаты'
  }
  if (err?.error === 'partner_payout_rejection_comment_required') {
    return 'Укажите причину отклонения'
  }
  return mapDeliveryApiError(err, 'Не удалось обработать заявку')
}

function canProcessPayout(item) {
  return item.status === 'PENDING' || item.status === 'PROCESSING' || item.status === 'SCHEDULED'
}

function isPayoutWindowOpen(scheduledPayoutDate) {
  if (!scheduledPayoutDate) return true
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const [year, month] = scheduledPayoutDate.split('-').map(Number)
  const monthStart = new Date(year, month - 1, 1)
  monthStart.setHours(0, 0, 0, 0)
  return monthStart <= today
}

function canApprovePayout(item) {
  return canProcessPayout(item) && item.payoutDetailsComplete && isPayoutWindowOpen(item.scheduledPayoutDate)
}

async function processPayoutRequest(courierServiceId, item, approve, comment) {
  if (item.balanceSource === 'MAIN') {
    return processCourierBalancePayout(
      courierServiceId,
      item.participantMemberId,
      item.id,
      approve,
      comment,
    )
  }
  return processPartnerPayout(courierServiceId, item.id, approve, comment)
}

export default function ServicePartnerProgramPayoutsTab() {
  const courierServiceId = useCourierServiceId()
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState(null)
  const [processingId, setProcessingId] = useState(null)
  const [approveTarget, setApproveTarget] = useState(null)
  const [rejectTarget, setRejectTarget] = useState(null)
  const [rejectComment, setRejectComment] = useState('')

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setRequests([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const data = (await fetchPartnerPayoutRequests(courierServiceId)) || []
      setRequests(data.filter((item) => item.payoutMethod === 'BANK_TRANSFER'))
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить заявки на выплату')
      setRequests([])
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const onApprove = async () => {
    if (!courierServiceId || !approveTarget) return
    setProcessingId(approveTarget.id)
    try {
      await processPayoutRequest(courierServiceId, approveTarget, true)
      setToast({ message: 'Выплата подтверждена', variant: 'success' })
      setApproveTarget(null)
      await reload()
    } catch (e) {
      setToast({ message: mapPayoutError(e), variant: 'error' })
    } finally {
      setProcessingId(null)
    }
  }

  const onReject = async () => {
    if (!courierServiceId || !rejectTarget) return
    const comment = rejectComment.trim()
    if (!comment) {
      setToast({ message: 'Укажите причину отклонения', variant: 'error' })
      return
    }
    setProcessingId(rejectTarget.id)
    try {
      await processPayoutRequest(courierServiceId, rejectTarget, false, comment)
      setToast({ message: 'Заявка отклонена', variant: 'success' })
      setRejectTarget(null)
      setRejectComment('')
      await reload()
    } catch (e) {
      setToast({ message: mapPayoutError(e), variant: 'error' })
    } finally {
      setProcessingId(null)
    }
  }

  const onTakeInWork = async (requestId) => {
    if (!courierServiceId) return
    setProcessingId(requestId)
    try {
      await takePartnerPayoutInWork(courierServiceId, requestId)
      setToast({ message: 'Заявка взята в работу', variant: 'success' })
      await reload()
    } catch (e) {
      setToast({ message: mapPayoutError(e), variant: 'error' })
    } finally {
      setProcessingId(null)
    }
  }

  if (!courierServiceId) {
    return (
      <div className="partner-program-state card">
        <p className="muted">Служба доставки не выбрана.</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="partner-program-state card">
        <p className="muted">Загрузка заявок…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="partner-program-state partner-program-state--error card">
        <p className="partner-program-state__error-text">{error}</p>
        <button type="button" className="btn btn-secondary" onClick={reload}>
          Повторить
        </button>
      </div>
    )
  }

  return (
    <div className="partner-program-payouts">
      {!requests.length ? (
        <EmptyState title="Заявок на выплату пока нет" />
      ) : (
        <div className="partner-program-payouts__list">
          {requests.map((item) => {
            const approveBlockedByDate =
              canProcessPayout(item) &&
              item.payoutDetailsComplete &&
              !isPayoutWindowOpen(item.scheduledPayoutDate)

            return (
            <article key={item.id} className="partner-program-payout-card card">
              <div className="partner-program-payout-card__head">
                <div className="partner-program-payout-card__identity">
                  <strong className="partner-program-payout-card__name">
                    {item.participantName || '—'}
                  </strong>
                  <span className="partner-program-badge partner-program-badge--type">
                    {participantTypeLabel(item.participantType)}
                  </span>
                </div>
                <span className={payoutStatusBadgeClass(item.status)}>
                  {payoutStatusLabel(item.status)}
                </span>
              </div>

              <p className="partner-program-payout-card__amount">{formatMoney(item.amount)}</p>

              <dl className="partner-program-payout-card__meta">
                <div className="partner-program-payout-card__meta-row">
                  <dt>Источник средств</dt>
                  <dd>{balanceSourceLabel(item.balanceSource)}</dd>
                </div>
                <div className="partner-program-payout-card__meta-row">
                  <dt>Создана</dt>
                  <dd>{formatDateTime(item.createdAt)}</dd>
                </div>
                {item.scheduledPayoutDate && item.status !== 'PAID' && item.status !== 'REJECTED' && (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Дата выплаты</dt>
                    <dd>{formatDateOnly(item.scheduledPayoutDate)}</dd>
                  </div>
                )}
                {item.processedAt && (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Обработана</dt>
                    <dd>{formatDateTime(item.processedAt)}</dd>
                  </div>
                )}
                <div className="partner-program-payout-card__meta-row">
                  <dt>Способ выплаты</dt>
                  <dd>{payoutMethodLabel(item.payoutMethod, item.participantType)}</dd>
                </div>
                {item.payoutMethod === 'BANK_TRANSFER' && (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Способ получения</dt>
                    <dd>{transferTypeLabel(item.transferType) || '—'}</dd>
                  </div>
                )}
                {item.transferType === PAYOUT_TRANSFER_TYPE_SBP ? (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Телефон</dt>
                    <dd>
                      {item.payoutDetailsComplete
                        ? formatPhoneNumberForDisplay(item.phoneNumber)
                        : 'Не указан'}
                    </dd>
                  </div>
                ) : (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Номер карты</dt>
                    <dd>
                      {item.payoutDetailsComplete
                        ? formatCardNumberForDisplay(item.cardNumber)
                        : 'Не указан'}
                    </dd>
                  </div>
                )}
                <div className="partner-program-payout-card__meta-row">
                  <dt>Имя получателя</dt>
                  <dd>{item.recipientName || '—'}</dd>
                </div>
                {item.bankName && (
                  <div className="partner-program-payout-card__meta-row">
                    <dt>Банк</dt>
                    <dd>{item.bankName}</dd>
                  </div>
                )}
              </dl>

              {!item.payoutDetailsComplete && canProcessPayout(item) && (
                <p className="partner-program-field__hint partner-program-field__hint--warn">
                  Заявка не готова к выплате: отсутствуют банковские реквизиты.
                </p>
              )}

              {approveBlockedByDate && (
                <p className="partner-program-field__hint partner-program-field__hint--warn">
                  Подтвердить выплату можно с 1-го числа месяца выплаты.
                </p>
              )}

              {canProcessPayout(item) && (
                <div className="partner-program-payout-card__actions">
                  {item.balanceSource !== 'MAIN' && item.status === 'PENDING' && item.payoutDetailsComplete && (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={processingId === item.id}
                      onClick={() => onTakeInWork(item.id)}
                    >
                      {processingId === item.id ? 'Обработка…' : 'Взять в работу'}
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn partner-program-payout-card__approve"
                    disabled={processingId === item.id || !canApprovePayout(item)}
                    title={
                      approveBlockedByDate
                        ? 'Подтвердить выплату можно с 1-го числа месяца выплаты'
                        : undefined
                    }
                    onClick={() => setApproveTarget(item)}
                  >
                    Подтвердить выплату
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={processingId === item.id}
                    onClick={() => {
                      setRejectTarget(item)
                      setRejectComment('')
                    }}
                  >
                    Отклонить
                  </button>
                </div>
              )}
            </article>
            )
          })}
        </div>
      )}

      <AppModal
        open={Boolean(approveTarget)}
        title="Подтвердить выплату"
        onClose={() => setApproveTarget(null)}
        footer={
          <div className="app-modal__footer-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setApproveTarget(null)}
              disabled={processingId === approveTarget?.id}
            >
              Отмена
            </button>
            <button
              type="button"
              className="btn"
              disabled={processingId === approveTarget?.id}
              onClick={onApprove}
            >
              {processingId === approveTarget?.id ? 'Сохранение…' : 'Подтвердить перевод'}
            </button>
          </div>
        }
      >
        <p>
          Подтвердить выплату <strong>{formatMoney(approveTarget?.amount)}</strong> для{' '}
          <strong>{approveTarget?.participantName}</strong>?
        </p>
        {approveTarget?.balanceSource && (
          <p className="muted">Источник: {balanceSourceLabel(approveTarget.balanceSource)}</p>
        )}
        {approveTarget?.transferType && (
          <p className="muted">Способ получения: {transferTypeLabel(approveTarget.transferType)}</p>
        )}
        {approveTarget?.transferType === PAYOUT_TRANSFER_TYPE_SBP && approveTarget?.phoneNumber && (
          <p className="muted">
            Телефон: {formatPhoneNumberForDisplay(approveTarget.phoneNumber)}
            {approveTarget.recipientName ? ` · ${approveTarget.recipientName}` : ''}
            {approveTarget.bankName ? ` · ${approveTarget.bankName}` : ''}
          </p>
        )}
        {approveTarget?.transferType !== PAYOUT_TRANSFER_TYPE_SBP && approveTarget?.cardNumber && (
          <p className="muted">
            Карта: {formatCardNumberForDisplay(approveTarget.cardNumber)}
            {approveTarget.recipientName ? ` · ${approveTarget.recipientName}` : ''}
            {approveTarget.bankName ? ` · ${approveTarget.bankName}` : ''}
          </p>
        )}
      </AppModal>

      <AppModal
        open={Boolean(rejectTarget)}
        title="Отклонить заявку"
        onClose={() => {
          setRejectTarget(null)
          setRejectComment('')
        }}
        footer={
          <div className="app-modal__footer-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setRejectTarget(null)
                setRejectComment('')
              }}
              disabled={processingId === rejectTarget?.id}
            >
              Отмена
            </button>
            <button
              type="button"
              className="btn"
              disabled={processingId === rejectTarget?.id || !rejectComment.trim()}
              onClick={onReject}
            >
              {processingId === rejectTarget?.id ? 'Сохранение…' : 'Отклонить'}
            </button>
          </div>
        }
      >
        <p className="muted">
          Сумма вернётся на доступный{' '}
          {rejectTarget?.balanceSource === 'MAIN' ? 'общий' : 'партнёрский'} баланс курьера.
        </p>
        <div className="partner-program-field">
          <label className="partner-program-field__label" htmlFor="payout-reject-comment">
            Причина отклонения
          </label>
          <textarea
            id="payout-reject-comment"
            className="partner-program-field__control"
            rows={3}
            value={rejectComment}
            onChange={(e) => setRejectComment(e.target.value)}
            disabled={processingId === rejectTarget?.id}
          />
        </div>
      </AppModal>

      <CopySnackbar
        message={toast?.message}
        variant={toast?.variant}
        onDismiss={() => setToast(null)}
      />
    </div>
  )
}
