import { useEffect, useMemo, useState } from 'react'
import AppModal from '../AppModal.jsx'
import PartnerBankPayoutFields, { isValidBankPayoutDetails } from '../partner/PartnerBankPayoutFields.jsx'
import {
  buildPayoutDetailsPayload,
  getLastTransferType,
  maskCardForSummary,
  maskPhoneForSummary,
  PAYOUT_TRANSFER_TYPE_CARD,
  PAYOUT_TRANSFER_TYPE_SBP,
  saveLastTransferType,
  transferTypeLabel,
} from '../../utils/partnerBankPayout.js'

function formatMoney(value) {
  const n = value != null ? Number(value) : 0
  if (Number.isNaN(n)) return '0 ₽'
  return `${n.toLocaleString('ru-RU')} ₽`
}

function formatDateOnly(isoDate) {
  if (!isoDate) return '—'
  try {
    const [year, month, day] = isoDate.split('-').map(Number)
    return new Date(year, month - 1, day).toLocaleDateString('ru-RU')
  } catch {
    return '—'
  }
}

function payoutMethodLabel(method) {
  if (method === 'BANK_TRANSFER') return 'Банковский перевод'
  if (method === 'TRANSFER_TO_MAIN_BALANCE') return 'На общий баланс'
  return method || '—'
}

export default function CourierPayoutFormModal({
  open,
  title,
  available,
  minAmount = 0,
  methods,
  defaultMethod,
  submitting,
  scheduledPayoutDate,
  submitLabel = 'Создать заявку',
  serverFieldErrors = {},
  onClose,
  onSubmit,
}) {
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState(defaultMethod || methods[0] || 'BANK_TRANSFER')
  const [transferType, setTransferType] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [recipientName, setRecipientName] = useState('')
  const [bankName, setBankName] = useState('')

  useEffect(() => {
    if (open) {
      setAmount('')
      setMethod(defaultMethod || methods[0] || 'BANK_TRANSFER')
      setTransferType(getLastTransferType())
      setCardNumber('')
      setPhoneNumber('')
      setRecipientName('')
      setBankName('')
    }
  }, [open, defaultMethod, methods])

  const availableNum = Number(available) || 0
  const minNum = Number(minAmount) || 0
  const amountNum = Number(amount) || 0
  const needsBankDetails = method === 'BANK_TRANSFER'
  const bankDetailsValid =
    !needsBankDetails
    || isValidBankPayoutDetails({ transferType, cardNumber, phoneNumber, recipientName, bankName })

  const fundsAvailable = availableNum > 0 && availableNum >= minNum
  const amountValid = amountNum > 0 && amountNum <= availableNum && (needsBankDetails ? amountNum >= minNum : true)
  const canSubmit = fundsAvailable && amountValid && bankDetailsValid

  const amountError = serverFieldErrors.amount || null

  const submitHint = useMemo(() => {
    if (submitting) return ''
    if (amountError) return amountError
    if (availableNum <= 0) return 'Сейчас нет средств, доступных к выплате.'
    if (availableNum < minNum) {
      return `Минимальная сумма — ${formatMoney(minNum)}. Доступно ${formatMoney(availableNum)}.`
    }
    if (!amountNum) return 'Укажите сумму выплаты.'
    if (amountNum > availableNum) {
      return `Сумма превышает доступный остаток (${formatMoney(availableNum)}).`
    }
    if (needsBankDetails && amountNum > 0 && amountNum < minNum) {
      return `Минимальная сумма — ${formatMoney(minNum)}.`
    }
    if (needsBankDetails && !transferType) {
      return 'Выберите способ получения выплаты.'
    }
    if (needsBankDetails && !bankDetailsValid) {
      return 'Заполните реквизиты для выбранного способа получения.'
    }
    return ''
  }, [
    submitting,
    availableNum,
    minNum,
    amountNum,
    needsBankDetails,
    transferType,
    bankDetailsValid,
    amountError,
  ])

  const handleFillAll = () => {
    if (availableNum > 0) {
      setAmount(String(availableNum))
    }
  }

  const handleSubmit = () => {
    if (!canSubmit) return
    const body = { amount: amountNum, payoutMethod: method }
    if (needsBankDetails) {
      body.payoutDetails = buildPayoutDetailsPayload({
        transferType,
        cardNumber,
        phoneNumber,
        recipientName,
        bankName,
      })
      saveLastTransferType(transferType)
    }
    onSubmit(body)
  }

  const summaryRequisitesLabel = transferType === PAYOUT_TRANSFER_TYPE_SBP ? 'Телефон' : 'Карта'
  const summaryRequisitesValue =
    transferType === PAYOUT_TRANSFER_TYPE_SBP
      ? maskPhoneForSummary(phoneNumber)
      : maskCardForSummary(cardNumber)

  return (
    <AppModal
      open={open}
      title={title}
      compact
      onClose={onClose}
      footer={
        <div className="courier-payout-modal__footer">
          {!canSubmit && submitHint && (
            <p className="courier-payout-modal__submit-hint" role="status">
              {submitHint}
            </p>
          )}
          <div className="app-modal__footer-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
              Отмена
            </button>
            <button
              type="button"
              className="btn"
              disabled={submitting || !canSubmit}
              onClick={handleSubmit}
            >
              {submitting ? 'Отправка…' : submitLabel}
            </button>
          </div>
        </div>
      }
    >
      <div className="courier-payout-modal">
        <p className="courier-payout-modal__available muted">
          Доступно <strong>{formatMoney(availableNum)}</strong>
        </p>

        <div className="courier-payout-modal__amount-block">
          <label className="courier-payout-modal__amount-label" htmlFor="payout-amount">
            Сумма
          </label>
          <div className="courier-payout-modal__amount-row">
            <div className="courier-payout-modal__amount-input-wrap">
              <input
                id="payout-amount"
                type="text"
                inputMode="decimal"
                className={`courier-payout-modal__amount-input${amountError ? ' courier-payout-modal__control--error' : ''}`}
                placeholder="0"
                value={amount}
                onChange={(e) => {
                  const raw = e.target.value.replace(/[^\d.,]/g, '').replace(',', '.')
                  setAmount(raw)
                }}
                disabled={!fundsAvailable || submitting}
                aria-invalid={Boolean(amountError)}
                aria-describedby={amountError ? 'payout-amount-error' : undefined}
              />
              <span className="courier-payout-modal__amount-currency" aria-hidden>
                ₽
              </span>
            </div>
            <button
              type="button"
              className="btn btn-secondary courier-payout-modal__amount-all"
              onClick={handleFillAll}
              disabled={!fundsAvailable || submitting}
            >
              Вся сумма
            </button>
          </div>
          {minNum > 0 && needsBankDetails && (
            <p className="courier-payout-modal__field-hint muted">Минимум: {formatMoney(minNum)}</p>
          )}
          {amountError && (
            <p id="payout-amount-error" className="courier-payout-modal__field-error">
              {amountError}
            </p>
          )}
        </div>

        <div className="courier-payout-modal__field">
          <span className="courier-payout-modal__field-label">Способ выплаты</span>
          {methods.length === 1 ? (
            <p className="courier-payout-modal__method-static">{payoutMethodLabel(methods[0])}</p>
          ) : (
            <select
              className="courier-payout-modal__control"
              value={method}
              onChange={(e) => setMethod(e.target.value)}
              disabled={submitting}
            >
              {methods.map((m) => (
                <option key={m} value={m}>
                  {payoutMethodLabel(m)}
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
            disabled={submitting}
            idPrefix="courier-payout"
            compact
            serverFieldErrors={serverFieldErrors}
          />
        )}

        {canSubmit && (
          <div className="courier-payout-summary" aria-live="polite">
            <p className="courier-payout-summary__title">Проверьте перед отправкой</p>
            <dl className="courier-payout-summary__list">
              <div className="courier-payout-summary__row">
                <dt>Сумма</dt>
                <dd>{formatMoney(amountNum)}</dd>
              </div>
              {needsBankDetails && (
                <>
                  <div className="courier-payout-summary__row">
                    <dt>Способ</dt>
                    <dd>{transferTypeLabel(transferType)}</dd>
                  </div>
                  <div className="courier-payout-summary__row">
                    <dt>{summaryRequisitesLabel}</dt>
                    <dd>{summaryRequisitesValue}</dd>
                  </div>
                  {transferType === PAYOUT_TRANSFER_TYPE_SBP && bankName?.trim() && (
                    <div className="courier-payout-summary__row">
                      <dt>Банк</dt>
                      <dd>{bankName.trim()}</dd>
                    </div>
                  )}
                  {recipientName?.trim() && (
                    <div className="courier-payout-summary__row">
                      <dt>Получатель</dt>
                      <dd>{recipientName.trim()}</dd>
                    </div>
                  )}
                </>
              )}
              {scheduledPayoutDate && (
                <div className="courier-payout-summary__row">
                  <dt>Дата выплаты</dt>
                  <dd>{formatDateOnly(scheduledPayoutDate)}</dd>
                </div>
              )}
            </dl>
          </div>
        )}
      </div>
    </AppModal>
  )
}
