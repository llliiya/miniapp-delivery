/** Максимум цифр в российском номере: код страны 7 + 10 цифр абонента. */
export const PHONE_MAX_DIGITS = 11

/**
 * Извлекает до 11 цифр и нормализует префикс:
 * 963… → 7963…, 8963… → 7963…
 * @param {string} raw
 * @returns {string}
 */
export function extractPhoneDigits(raw) {
  if (raw == null || typeof raw !== 'string') return ''
  let digits = raw.replace(/\D/g, '').slice(0, PHONE_MAX_DIGITS)
  if (digits.length === 10 && digits.startsWith('9')) {
    digits = '7' + digits
  }
  if (digits.length === 11 && digits.startsWith('8')) {
    digits = '7' + digits.slice(1)
  }
  return digits
}

/**
 * Нормализация номера к E.164 (с плюсом). РФ: 8/7/9 + 10 цифр → +79XXXXXXXXX.
 * @param {string} raw
 * @returns {string} E.164 или пустая строка
 */
export function toE164(raw) {
  const digits = extractPhoneDigits(raw)
  if (!digits.length) return ''
  if (digits.length === 11 && digits.startsWith('7')) {
    return `+${digits}`
  }
  if (digits.length > 0) return `+${digits}`
  return ''
}

export function looksLikePhone(raw) {
  if (raw == null || typeof raw !== 'string') return false
  const digits = raw.replace(/\D/g, '')
  return digits.length >= 10
}

/** Полный российский мобильный номер: +79XXXXXXXXX */
export function isValidRussianPhone(raw) {
  const e164 = toE164(raw)
  return /^\+79\d{9}$/.test(e164)
}

/**
 * Формат для отображения: +7 (963) 216-54-54
 * @param {string} raw
 * @returns {string}
 */
export function formatPhoneDisplay(raw) {
  if (raw == null || typeof raw !== 'string') return '—'
  const trimmed = raw.trim()
  if (!trimmed) return '—'
  const masked = formatPhoneMask(trimmed)
  return masked || trimmed
}

/**
 * Маска ввода: +7 (963) 216-54-54 (частичный ввод поддерживается)
 * @param {string} raw
 * @returns {string}
 */
export function formatPhoneMask(raw) {
  const digits = extractPhoneDigits(raw)
  if (!digits.length) return ''

  const sub = digits.startsWith('7') ? digits.slice(1) : digits
  if (!sub.length) return '+7'

  let out = `+7 (${sub.slice(0, 3)}`
  if (sub.length <= 3) return out

  out += `) ${sub.slice(3, 6)}`
  if (sub.length <= 6) return out

  out += `-${sub.slice(6, 8)}`
  if (sub.length <= 8) return out

  return `${out}-${sub.slice(8, 10)}`
}

/** Позиция курсора после ввода n цифр абонента (без ведущей 7). */
export function phoneMaskCursorAfterSubDigits(subCount) {
  if (subCount <= 0) return 3
  if (subCount <= 3) return 3 + subCount + 1
  if (subCount <= 6) return 5 + subCount + 1
  if (subCount <= 8) return 6 + subCount + 1
  return 7 + subCount + 1
}
