import { useState } from 'react'
import AppModal from './AppModal.jsx'
import CopySnackbar from './CopySnackbar.jsx'
import PartnerBankPayoutFields, { isValidBankPayoutDetails } from '../partner/PartnerBankPayoutFields.jsx'
import { buildPayoutDetailsPayload, getLastTransferType, saveLastTransferType } from '../../utils/partnerBankPayout.js'

function formatMoney(value) {
  const n = value != null ? Number(value) : 0
  if (Number.isNaN(n)) return '0 ₽'
  return `${n.toLocaleString('ru-RU')} ₽`
}

function payoutMethodLabel(method) {
  if (method === 'BANK_TRANSFER') return 'Банковский перевод'
  if (method === 'TRANSFER_TO_MAIN_BALANCE') return 'На общий баланс'
  return method || '—'
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

function MainPayoutModal({ open, available, submitting, onClose, onSubmit }) {
  const [amount, setAmount] = useState('')

  const handleClose = () => {
    setAmount('')
    onClose()
  }

  const handleSubmit = () => {
    onSubmit(amount, handleClose)
  }

  const availableNum = Number(available) || 0
  const amountNum = Number(amount) || 0
  const canSubmit = availableNum > 0 && amountNum > 0 && amountNum <= availableNum

  let unavailableHint = ''
  if (open && availableNum <= 0) {
    unavailableHint = 'Сейчас нет средств, доступных к выплате.'
  } else if (open && amountNum > availableNum) {
    unavailableHint = `Доступно только ${formatMoney(availableNum)}.`
  }

  return (
    <AppModal
      open={open}
      title="Запросить выплату"
      onClose={handleClose}
      footer={
        <div className="app-modal__footer-actions">
          <button type="button" className="btn btn-secondary" onClick={handleClose} disabled={submitting}>
            Отмена
          </button>
          <button type="button" className="btn" onClick={handleSubmit} disabled={submitting || !canSubmit}>
            {submitting ? 'Отправка…' : 'Подтвердить'}
          </button>
        </div>
      }
    >
      <p className="courier-balance-modal__available muted">
        Доступно: <strong>{formatMoney(available)}</strong>
      </p>
      <div className="partner-program-field">
        <label className="partner-program-field__label" htmlFor="main-payout-amount">
          Сумма
        </label>
        <input
          id="main-payout-amount"
          type="number"
          min="0"
          step="0.01"
          className="partner-program-field__control"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          disabled={submitting || availableNum <= 0}
        />
        {unavailableHint && (
          <p className="partner-program-field__hint partner-program-field__hint--warn">{unavailableHint}</p>
        )}
      </div>
      <div className="partner-program-field">
        <span className="partner-program-field__label">Способ выплаты</span>
        <p className="partner-program-payout-method-static">{payoutMethodLabel('BANK_TRANSFER')}</p>
      </div>
    </AppModal>
  )
}

function PartnerManageModal({
  open,
  program,
  submitting,
  onClose,
  onSubmit,
}) {
  const [action, setAction] = useState(null)
  const [amount, setAmount] = useState('')
  const [transferType, setTransferType] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [recipientName, setRecipientName] = useState('')
  const [bankName, setBankName] = useState('')

  const available = Number(program?.balance?.eligibleForRequest) || 0
  const minPayout = Number(program?.minPayoutAmount) || 0
  const canCreate = Boolean(program?.balance?.canCreatePayoutRequest)
  const scheduledHint = formatScheduledPayoutDate(program?.balance?.nextScheduledPayoutDate)
  const methods = program?.availablePayoutMethods || []
  const canBankTransfer = methods.includes('BANK_TRANSFER')
  const canTransferToMain = methods.includes('TRANSFER_TO_MAIN_BALANCE')

  const payoutMethod = action === 'transfer' ? 'TRANSFER_TO_MAIN_BALANCE' : 'BANK_TRANSFER'

  const handleClose = () => {
    setAction(null)
    setAmount('')
    setTransferType('')
    setCardNumber('')
    setPhoneNumber('')
    setRecipientName('')
    setBankName('')
    onClose()
  }

  const amountNum = Number(amount) || 0
  const needsBankDetails = action === 'bank'
  const bankDetailsValid =
    !needsBankDetails
    || isValidBankPayoutDetails({ transferType, cardNumber, phoneNumber, recipientName, bankName })
  const canSubmit =
    canCreate &&
    available > 0 &&
    amountNum > 0 &&
    amountNum <= available &&
    (action !== 'bank' || amountNum >= minPayout) &&
    bankDetailsValid

  let unavailableHint = ''
  if (open && action && available <= 0) {
    unavailableHint = 'Сейчас нет средств, доступных к выплате.'
  } else if (open && action === 'bank' && available > 0 && available < minPayout) {
    unavailableHint = `Минимальная сумма — ${formatMoney(minPayout)}. Доступно ${formatMoney(available)}.`
  } else if (open && action && amountNum > available) {
    unavailableHint = `Доступно только ${formatMoney(available)}.`
  }

  const handleSubmit = () => {
    const body = { amount: amountNum, payoutMethod }
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
    onSubmit(body, handleClose)
  }

  return (
    <AppModal
      open={open}
      title={action ? (action === 'transfer' ? 'Перевод на общий баланс' : 'Вывод на карту') : 'Партнёрский баланс'}
      onClose={handleClose}
      footer={
        action ? (
          <div className="app-modal__footer-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setAction(null)
                setAmount('')
                setTransferType('')
                setCardNumber('')
                setPhoneNumber('')
                setRecipientName('')
                setBankName('')
              }}
              disabled={submitting}
            >
              Назад
            </button>
            <button type="button" className="btn" onClick={handleSubmit} disabled={submitting || !canSubmit}>
              {submitting ? 'Отправка…' : 'Подтвердить'}
            </button>
          </div>
        ) : null
      }
    >
      {!action ? (
        <div className="courier-balance-modal__actions">
          <p className="courier-balance-modal__available muted">
            Доступно: <strong>{formatMoney(available)}</strong>
          </p>
          {scheduledHint && (
            <p className="partner-program-field__hint muted">{scheduledHint}</p>
          )}
          {canBankTransfer && (
            <button
              type="button"
              className="btn courier-balance-modal__action-btn"
              disabled={!canCreate}
              onClick={() => {
                setTransferType(getLastTransferType())
                setAction('bank')
              }}
            >
              Вывести на карту
            </button>
          )}
          {canTransferToMain && (
            <button
              type="button"
              className="btn btn-secondary courier-balance-modal__action-btn"
              disabled={!canCreate}
              onClick={() => setAction('transfer')}
            >
              Перевести на общий баланс
            </button>
          )}
          {!canBankTransfer && !canTransferToMain && (
            <p className="muted">Способы выплаты не настроены.</p>
          )}
        </div>
      ) : (
        <>
          <p className="courier-balance-modal__available muted">
            Доступно: <strong>{formatMoney(available)}</strong>
          </p>
          {scheduledHint && (
            <p className="partner-program-field__hint muted">{scheduledHint}</p>
          )}
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
              placeholder={action === 'bank' ? `От ${formatMoney(minPayout)}` : undefined}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              disabled={submitting}
            />
            {action === 'bank' && minPayout > 0 && (
              <p className="partner-program-field__hint muted">
                Минимальная сумма: {formatMoney(minPayout)}
              </p>
            )}
            {unavailableHint && (
              <p className="partner-program-field__hint partner-program-field__hint--warn">{unavailableHint}</p>
            )}
          </div>
          <div className="partner-program-field">
            <span className="partner-program-field__label">Способ выплаты</span>
            <p className="partner-program-payout-method-static">{payoutMethodLabel(payoutMethod)}</p>
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
              idPrefix="partner-card-payout"
            />
          )}
        </>
      )}
    </AppModal>
  )
}

export default function CourierBalanceCards({
  mainBalance,
  mainAvailable,
  partnerProgram,
  partnerEnabled,
  onRequestMainPayout,
  onRequestPartnerPayout,
  mainSubmitting,
  partnerSubmitting,
}) {
  const [mainModalOpen, setMainModalOpen] = useState(false)
  const [partnerModalOpen, setPartnerModalOpen] = useState(false)
  const [snackbar, setSnackbar] = useState(null)

  const partnerBalance = partnerProgram?.balance?.balance
  const partnerAvailable = partnerProgram?.balance?.eligibleForRequest
  const partnerCanCreate = Boolean(partnerProgram?.balance?.canCreatePayoutRequest)

  const handleMainSubmit = async (amountStr, closeModal) => {
    const amount = Number(amountStr)
    if (!amount || amount <= 0) {
      setSnackbar({ message: 'Укажите сумму выплаты', variant: 'error' })
      return
    }
    try {
      await onRequestMainPayout({ amount, payoutMethod: 'BANK_TRANSFER' })
      closeModal()
      setSnackbar({ message: 'Заявка на выплату создана', variant: 'success' })
    } catch (e) {
      setSnackbar({ message: e?.message || 'Не удалось создать заявку', variant: 'error' })
    }
  }

  const handlePartnerSubmit = async (body, closeModal) => {
    try {
      await onRequestPartnerPayout(body)
      closeModal()
      setSnackbar({ message: 'Заявка создана', variant: 'success' })
    } catch (e) {
      const message =
        e?.error === 'payout_once_per_month'
          ? 'Партнёрскую выплату можно запросить не чаще одного раза в календарный месяц'
          : e?.message || 'Не удалось создать заявку'
      setSnackbar({ message, variant: 'error' })
    }
  }

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
          <p className="courier-balance-card__amount">{formatMoney(mainBalance)}</p>
          <p className="courier-balance-card__available muted">
            Доступно к выплате: <strong>{formatMoney(mainAvailable)}</strong>
          </p>
          <button type="button" className="btn courier-balance-card__btn" onClick={() => setMainModalOpen(true)}>
            Запросить выплату
          </button>
        </article>

        {partnerEnabled && (
          <article className="card courier-balance-card courier-balance-card--partner">
            <h2 className="courier-balance-card__title">Партнёрский баланс</h2>
            <p className="courier-balance-card__amount">{formatMoney(partnerBalance)}</p>
            <p className="courier-balance-card__available muted">
              Доступно к выплате: <strong>{formatMoney(partnerAvailable)}</strong>
            </p>
            <button
              type="button"
              className="btn btn-secondary courier-balance-card__btn"
              onClick={() => setPartnerModalOpen(true)}
            >
              Управлять
            </button>
          </article>
        )}
      </section>

      <MainPayoutModal
        open={mainModalOpen}
        available={mainAvailable}
        submitting={mainSubmitting}
        onClose={() => setMainModalOpen(false)}
        onSubmit={handleMainSubmit}
      />

      <PartnerManageModal
        open={partnerModalOpen}
        program={partnerProgram}
        submitting={partnerSubmitting}
        onClose={() => setPartnerModalOpen(false)}
        onSubmit={handlePartnerSubmit}
      />
    </>
  )
}
