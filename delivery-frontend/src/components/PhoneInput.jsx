import { useRef } from 'react'
import {
  extractPhoneDigits,
  formatPhoneMask,
  phoneMaskCursorAfterSubDigits,
  toE164,
} from '../utils/phone'

export { PHONE_MAX_DIGITS as PHONE_INPUT_MAX_DIGITS } from '../utils/phone'

/**
 * Поле ввода телефона с маской +7 (___) ___-__-__
 * value — E.164 или цифры; onChange — E.164 при полном номере, иначе цифры.
 */
export default function PhoneInput({
  value = '',
  onChange,
  className,
  placeholder = '+7 (___) ___-__-__',
  invalid = false,
  ...rest
}) {
  const inputRef = useRef(null)
  const digitsRef = useRef('')

  const digits = extractPhoneDigits(value)
  digitsRef.current = digits
  const display = digits.length ? formatPhoneMask(digits) : ''

  const emitChange = (nextDigits) => {
    const normalized = toE164(nextDigits)
    onChange(normalized || nextDigits)
  }

  const setCursor = (subCount) => {
    const input = inputRef.current
    if (!input) return
    const pos = phoneMaskCursorAfterSubDigits(subCount)
    requestAnimationFrame(() => {
      input.setSelectionRange(pos, pos)
    })
  }

  const handleChange = (e) => {
    const prevDigits = digitsRef.current
    const nextDigits = extractPhoneDigits(e.target.value)
    const prevSub = prevDigits.startsWith('7') ? prevDigits.slice(1) : prevDigits
    const nextSub = nextDigits.startsWith('7') ? nextDigits.slice(1) : nextDigits
    emitChange(nextDigits)
    setCursor(nextSub.length)
    if (nextSub.length < prevSub.length) {
      setCursor(nextSub.length)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key !== 'Backspace' && e.key !== 'Delete') return
    const input = inputRef.current
    if (!input) return

    const selectionStart = input.selectionStart ?? 0
    const selectionEnd = input.selectionEnd ?? 0
    if (selectionStart !== selectionEnd) return

    const currentDigits = digitsRef.current
    const sub = currentDigits.startsWith('7') ? currentDigits.slice(1) : currentDigits
    if (!sub.length) return

    const pos = selectionStart
    const formatted = formatPhoneMask(currentDigits)

    if (e.key === 'Backspace') {
      const charBefore = formatted[pos - 1]
      if (charBefore && !/\d/.test(charBefore)) {
        e.preventDefault()
        const nextSub = sub.slice(0, -1)
        const nextDigits = nextSub.length ? `7${nextSub}` : ''
        emitChange(nextDigits)
        setCursor(nextSub.length)
      }
    }
  }

  const handleFocus = () => {
    const sub = digits.startsWith('7') ? digits.slice(1) : digits
    setCursor(sub.length)
  }

  return (
    <input
      ref={inputRef}
      type="tel"
      inputMode="tel"
      className={[className, invalid ? 'input--invalid' : ''].filter(Boolean).join(' ')}
      placeholder={placeholder}
      value={display}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      onFocus={handleFocus}
      autoComplete="tel"
      {...rest}
    />
  )
}
