import { isValidRussianPhone, toE164 } from './phone.js'

export const PAYOUT_TRANSFER_TYPE_CARD = 'CARD'
export const PAYOUT_TRANSFER_TYPE_SBP = 'SBP_PHONE'
const LAST_TRANSFER_TYPE_KEY = 'bankPayoutTransferType'

export function getLastTransferType() {
  try {
    const value = localStorage.getItem(LAST_TRANSFER_TYPE_KEY)
    if (value === PAYOUT_TRANSFER_TYPE_CARD || value === PAYOUT_TRANSFER_TYPE_SBP) {
      return value
    }
  } catch {
    // ignore
  }
  return ''
}

export function saveLastTransferType(transferType) {
  if (transferType !== PAYOUT_TRANSFER_TYPE_CARD && transferType !== PAYOUT_TRANSFER_TYPE_SBP) {
    return
  }
  try {
    localStorage.setItem(LAST_TRANSFER_TYPE_KEY, transferType)
  } catch {
    // ignore
  }
}

export function transferTypeLabel(transferType) {
  if (transferType === PAYOUT_TRANSFER_TYPE_SBP) return 'По номеру телефона — СБП'
  if (transferType === PAYOUT_TRANSFER_TYPE_CARD) return 'По номеру карты'
  return '—'
}

export function normalizeCardNumber(value) {
  if (!value) return ''
  return String(value).replace(/\D/g, '')
}

export function isValidCardPayoutDetails({ cardNumber, recipientName }) {
  const digits = normalizeCardNumber(cardNumber)
  const name = recipientName?.trim()
  return digits.length >= 13 && digits.length <= 19 && Boolean(name)
}

export function isValidSbpPayoutDetails({ phoneNumber, recipientName, bankName }) {
  const name = recipientName?.trim()
  const bank = bankName?.trim()
  return isValidRussianPhone(phoneNumber) && Boolean(name) && Boolean(bank)
}

export function isValidBankPayoutDetails({ transferType, cardNumber, phoneNumber, recipientName, bankName }) {
  if (transferType === PAYOUT_TRANSFER_TYPE_SBP) {
    return isValidSbpPayoutDetails({ phoneNumber, recipientName, bankName })
  }
  if (transferType === PAYOUT_TRANSFER_TYPE_CARD) {
    return isValidCardPayoutDetails({ cardNumber, recipientName })
  }
  return false
}

/** @deprecated use buildPayoutDetailsPayload */
export function buildBankPayoutDetailsPayload({ cardNumber, recipientName, bankName }) {
  return buildPayoutDetailsPayload({
    transferType: PAYOUT_TRANSFER_TYPE_CARD,
    cardNumber,
    recipientName,
    bankName,
  })
}

export function buildPayoutDetailsPayload({
  transferType,
  cardNumber,
  phoneNumber,
  recipientName,
  bankName,
}) {
  const payload = {
    transferType,
    recipientName: recipientName?.trim() || '',
  }

  if (transferType === PAYOUT_TRANSFER_TYPE_CARD) {
    payload.cardNumber = normalizeCardNumber(cardNumber)
    const bank = bankName?.trim()
    if (bank) {
      payload.bankName = bank
    }
    return payload
  }

  if (transferType === PAYOUT_TRANSFER_TYPE_SBP) {
    payload.phoneNumber = toE164(phoneNumber) || phoneNumber?.trim() || ''
    payload.bankName = bankName?.trim() || ''
    return payload
  }

  return payload
}

export function formatCardNumberForDisplay(cardNumber) {
  const digits = normalizeCardNumber(cardNumber)
  if (!digits) return '—'
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim()
}

export function formatPhoneNumberForDisplay(phoneNumber) {
  const e164 = toE164(phoneNumber)
  if (!e164) return '—'
  const digits = e164.replace(/\D/g, '')
  if (digits.length !== 11 || !digits.startsWith('7')) return e164
  const sub = digits.slice(1)
  return `+7 (${sub.slice(0, 3)}) ${sub.slice(3, 6)}-${sub.slice(6, 8)}-${sub.slice(8, 10)}`
}

export function formatCardInput(value) {
  const digits = normalizeCardNumber(value).slice(0, 19)
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim()
}

export function getCardNumberError(cardNumber, { touched = false } = {}) {
  const digits = normalizeCardNumber(cardNumber)
  if (!digits) {
    return touched ? 'Укажите номер карты' : null
  }
  if (digits.length < 13) {
    return 'Введите полный номер карты (13–19 цифр)'
  }
  if (digits.length > 19) {
    return 'Номер карты не может быть длиннее 19 цифр'
  }
  return null
}

export function getPhoneNumberError(phoneNumber, { touched = false } = {}) {
  if (!phoneNumber?.trim()) {
    return touched ? 'Укажите номер телефона' : null
  }
  if (!isValidRussianPhone(phoneNumber)) {
    return touched ? 'Введите полный номер телефона' : null
  }
  return null
}

export function getRecipientNameError(recipientName, { touched = false } = {}) {
  if (!recipientName?.trim()) {
    return touched ? 'Укажите имя получателя' : null
  }
  return null
}

export function getBankNameError(bankName, { touched = false, required = false } = {}) {
  if (!bankName?.trim()) {
    return touched || required ? 'Укажите банк получателя' : null
  }
  return null
}

export function maskCardForSummary(cardNumber) {
  const digits = normalizeCardNumber(cardNumber)
  if (!digits) return '—'
  if (digits.length <= 4) return `•••• ${digits}`
  return `•••• ${digits.slice(-4)}`
}

export function maskPhoneForSummary(phoneNumber) {
  const e164 = toE164(phoneNumber)
  if (!/^\+79\d{9}$/.test(e164)) return '—'
  const last4 = e164.slice(-4)
  return `+7 *** ***-${last4.slice(0, 2)}-${last4.slice(2)}`
}

export function maskRequisitesForSummary({ transferType, cardNumber, phoneNumber }) {
  if (transferType === PAYOUT_TRANSFER_TYPE_SBP) {
    return maskPhoneForSummary(phoneNumber)
  }
  return maskCardForSummary(cardNumber)
}
