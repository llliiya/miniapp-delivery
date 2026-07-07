/**
 * Понятные сообщения для ответов delivery-backend (ProblemDetail / error).
 */
const CONFLICT_MESSAGES = {
  courier_already_in_service: 'Курьер уже есть в вашей службе',
  member_other_role: 'Пользователь уже состоит в службе с другой ролью',
  email_already_used: 'Пользователь с таким email уже существует',
  phone_already_used: 'Пользователь с таким телефоном уже существует',
  login_already_used: 'Этот логин уже занят',
  payout_once_per_month:
    'Партнёрскую выплату можно запросить не чаще одного раза в календарный месяц',
}

export function mapDeliveryApiError(err, fallback = 'Не удалось выполнить операцию') {
  const code = (err?.error || '').trim()
  const msg = (err?.message || '').trim()

  if (err?.status === 409) {
    if (code && CONFLICT_MESSAGES[code]) return CONFLICT_MESSAGES[code]
    if (/телефон|номером телефона/i.test(msg)) return 'Пользователь с таким телефоном уже существует'
    if (/email/i.test(msg)) return msg
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
