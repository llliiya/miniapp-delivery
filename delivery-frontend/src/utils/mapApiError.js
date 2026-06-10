/**
 * Понятные сообщения для ответов delivery-backend (ProblemDetail / error).
 */
export function mapDeliveryApiError(err, fallback = 'Не удалось выполнить операцию') {
  const msg = (err?.message || err?.error || '').trim()
  if (err?.status === 409) {
    if (/телефон/i.test(msg)) return 'Пользователь с таким телефоном уже существует'
    if (msg && !/^HTTP\s\d+/i.test(msg)) return msg
    return 'Не удалось выполнить действие'
  }
  if (err?.status === 401) return 'Сессия истекла. Войдите снова.'
  if (err?.status === 403) return 'Недостаточно прав'
  if (err?.status === 502 || err?.status === 503 || err?.status === 504) {
    return 'Не удалось создать пользователя. Попробуйте позже'
  }
  if (err?.status === 400) {
    if (/ФИО|телефон/i.test(msg)) return msg
    return msg || 'Заполните ФИО и телефон'
  }
  if (msg && !/^HTTP\s\d+/i.test(msg)) return msg
  return fallback
}

