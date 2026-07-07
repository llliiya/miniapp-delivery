import { useState } from 'react'
import PhoneInput from '../PhoneInput.jsx'
import {
  formatCardInput,
  getBankNameError,
  getCardNumberError,
  getPhoneNumberError,
  getRecipientNameError,
  isValidBankPayoutDetails,
  PAYOUT_TRANSFER_TYPE_CARD,
  PAYOUT_TRANSFER_TYPE_SBP,
  transferTypeLabel,
} from '../../utils/partnerBankPayout.js'

export default function PartnerBankPayoutFields({
  transferType,
  onTransferTypeChange,
  cardNumber,
  phoneNumber,
  recipientName,
  bankName,
  onCardNumberChange,
  onPhoneNumberChange,
  onRecipientNameChange,
  onBankNameChange,
  disabled = false,
  idPrefix = 'partner-bank',
  compact = false,
  serverFieldErrors = {},
}) {
  const [cardTouched, setCardTouched] = useState(false)
  const [phoneTouched, setPhoneTouched] = useState(false)
  const [recipientTouched, setRecipientTouched] = useState(false)
  const [bankTouched, setBankTouched] = useState(false)

  const isCard = transferType === PAYOUT_TRANSFER_TYPE_CARD
  const isSbp = transferType === PAYOUT_TRANSFER_TYPE_SBP
  const bankRequired = isSbp

  const cardError = isCard
    ? serverFieldErrors.cardNumber || getCardNumberError(cardNumber, { touched: cardTouched })
    : null
  const phoneError = isSbp
    ? serverFieldErrors.phoneNumber || getPhoneNumberError(phoneNumber, { touched: phoneTouched })
    : null
  const recipientError =
    serverFieldErrors.recipientName || getRecipientNameError(recipientName, { touched: recipientTouched })
  const bankError = isSbp
    ? serverFieldErrors.bankName || getBankNameError(bankName, { touched: bankTouched, required: true })
    : null

  const detailsValid = isValidBankPayoutDetails({
    transferType,
    cardNumber,
    phoneNumber,
    recipientName,
    bankName,
  })

  const handleCardChange = (value) => {
    onCardNumberChange(formatCardInput(value))
  }

  const formClass = compact
    ? 'partner-program-payout-form partner-program-payout-form--compact'
    : 'partner-program-payout-form'

  return (
    <div className={formClass}>
      <fieldset className="courier-payout-transfer-type" disabled={disabled}>
        <legend className="courier-payout-modal__field-label">Способ получения</legend>
        <div className="courier-payout-transfer-type__grid">
          <label
            className={`courier-payout-transfer-type__option${
              isCard ? ' courier-payout-transfer-type__option--selected' : ''
            }`}
          >
            <input
              type="radio"
              name={`${idPrefix}-transfer-type`}
              value={PAYOUT_TRANSFER_TYPE_CARD}
              checked={isCard}
              onChange={() => onTransferTypeChange(PAYOUT_TRANSFER_TYPE_CARD)}
              disabled={disabled}
            />
            <span>{transferTypeLabel(PAYOUT_TRANSFER_TYPE_CARD)}</span>
          </label>
          <label
            className={`courier-payout-transfer-type__option${
              isSbp ? ' courier-payout-transfer-type__option--selected' : ''
            }`}
          >
            <input
              type="radio"
              name={`${idPrefix}-transfer-type`}
              value={PAYOUT_TRANSFER_TYPE_SBP}
              checked={isSbp}
              onChange={() => onTransferTypeChange(PAYOUT_TRANSFER_TYPE_SBP)}
              disabled={disabled}
            />
            <span>{transferTypeLabel(PAYOUT_TRANSFER_TYPE_SBP)}</span>
          </label>
        </div>
        {serverFieldErrors.transferType && (
          <p className="courier-payout-modal__field-error">{serverFieldErrors.transferType}</p>
        )}
      </fieldset>

      {isCard && (
        <div className="courier-payout-modal__field">
          <label className="courier-payout-modal__field-label" htmlFor={`${idPrefix}-card`}>
            Номер банковской карты
          </label>
          <input
            id={`${idPrefix}-card`}
            type="text"
            inputMode="numeric"
            autoComplete="cc-number"
            className={`courier-payout-modal__control${cardError ? ' courier-payout-modal__control--error' : ''}`}
            placeholder="0000 0000 0000 0000"
            value={cardNumber}
            onChange={(e) => handleCardChange(e.target.value)}
            onBlur={() => setCardTouched(true)}
            disabled={disabled}
            aria-invalid={Boolean(cardError)}
            aria-describedby={cardError ? `${idPrefix}-card-error` : undefined}
          />
          {cardError && (
            <p id={`${idPrefix}-card-error`} className="courier-payout-modal__field-error">
              {cardError}
            </p>
          )}
        </div>
      )}

      {isSbp && (
        <div className="courier-payout-modal__field">
          <label className="courier-payout-modal__field-label" htmlFor={`${idPrefix}-phone`}>
            Номер телефона
          </label>
          <PhoneInput
            id={`${idPrefix}-phone`}
            className={`courier-payout-modal__control${phoneError ? ' courier-payout-modal__control--error' : ''}`}
            value={phoneNumber}
            onChange={onPhoneNumberChange}
            onBlur={() => setPhoneTouched(true)}
            disabled={disabled}
            invalid={Boolean(phoneError)}
            aria-invalid={Boolean(phoneError)}
            aria-describedby={phoneError ? `${idPrefix}-phone-error` : undefined}
          />
          {phoneError && (
            <p id={`${idPrefix}-phone-error`} className="courier-payout-modal__field-error">
              {phoneError}
            </p>
          )}
        </div>
      )}

      {(isCard || isSbp) && (
        <div className="courier-payout-modal__details-grid">
          <div className="courier-payout-modal__field">
            <label className="courier-payout-modal__field-label" htmlFor={`${idPrefix}-recipient`}>
              Имя получателя
            </label>
            <input
              id={`${idPrefix}-recipient`}
              type="text"
              autoComplete="name"
              className={`courier-payout-modal__control${recipientError ? ' courier-payout-modal__control--error' : ''}`}
              value={recipientName}
              onChange={(e) => onRecipientNameChange(e.target.value)}
              onBlur={() => setRecipientTouched(true)}
              disabled={disabled}
              aria-invalid={Boolean(recipientError)}
              aria-describedby={recipientError ? `${idPrefix}-recipient-error` : undefined}
            />
            {recipientError && (
              <p id={`${idPrefix}-recipient-error`} className="courier-payout-modal__field-error">
                {recipientError}
              </p>
            )}
          </div>

          <div className="courier-payout-modal__field">
            <label className="courier-payout-modal__field-label" htmlFor={`${idPrefix}-bank`}>
              {isSbp ? 'Банк получателя' : 'Банк'}
              {!bankRequired && <span className="muted"> (необязательно)</span>}
            </label>
            <input
              id={`${idPrefix}-bank`}
              type="text"
              className={`courier-payout-modal__control${bankError ? ' courier-payout-modal__control--error' : ''}`}
              value={bankName}
              onChange={(e) => onBankNameChange(e.target.value)}
              onBlur={() => setBankTouched(true)}
              disabled={disabled}
              aria-invalid={Boolean(bankError)}
              aria-describedby={bankError ? `${idPrefix}-bank-error` : undefined}
            />
            {bankError && (
              <p id={`${idPrefix}-bank-error`} className="courier-payout-modal__field-error">
                {bankError}
              </p>
            )}
          </div>
        </div>
      )}

      {transferType && !detailsValid && !cardError && !phoneError && !recipientError && !bankError && (
        <p className="courier-payout-modal__field-error">
          Проверьте реквизиты для выбранного способа получения.
        </p>
      )}
    </div>
  )
}

export { isValidBankPayoutDetails }
