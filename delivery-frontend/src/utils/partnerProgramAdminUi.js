export function formatMoney(value) {
  const n = value != null ? Number(value) : 0
  if (Number.isNaN(n)) return '0 ₽'
  return `${n.toLocaleString('ru-RU')} ₽`
}

export function formatDateTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '—'
  }
}

export function formatDateOnly(value) {
  if (!value) return '—'
  try {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [year, month, day] = value.split('-').map(Number)
      return new Date(year, month - 1, day).toLocaleDateString('ru-RU')
    }
    return formatDateTime(value)
  } catch {
    return '—'
  }
}

export function participantTypeLabel(type) {
  if (type === 'COURIER') return 'Курьер'
  if (type === 'RESTAURANT') return 'Объект'
  return type || '—'
}

export function balanceSourceLabel(source) {
  if (source === 'MAIN') return 'Общий баланс'
  if (source === 'PARTNER') return 'Партнёрский баланс'
  return '—'
}

export function payoutStatusLabel(status) {
  if (status === 'PENDING') return 'Ожидает обработки'
  if (status === 'SCHEDULED') return 'Ожидает даты выплаты'
  if (status === 'PROCESSING') return 'В обработке'
  if (status === 'PAID') return 'Выплачена'
  if (status === 'REJECTED') return 'Отклонено'
  if (status === 'CANCELLED') return 'Отменено'
  return status || '—'
}

export function payoutStatusBadgeClass(status) {
  if (status === 'PENDING') return 'partner-program-badge partner-program-badge--pending'
  if (status === 'SCHEDULED') return 'partner-program-badge partner-program-badge--scheduled'
  if (status === 'PROCESSING') return 'partner-program-badge partner-program-badge--pending'
  if (status === 'PAID') return 'partner-program-badge partner-program-badge--paid'
  if (status === 'REJECTED') return 'partner-program-badge partner-program-badge--rejected'
  if (status === 'CANCELLED') return 'partner-program-badge partner-program-badge--muted'
  return 'partner-program-badge partner-program-badge--muted'
}

export function referralStatusLabel(status) {
  if (status === 'ACTIVE') return 'Активен'
  if (status === 'EXPIRED') return 'Истёк срок начислений'
  if (status === 'RULE_DISABLED') return 'Правило выключено'
  if (status === 'INVITEE_INACTIVE') return 'Приглашённый неактивен'
  return status || '—'
}

export function referralStatusBadgeClass(status) {
  if (status === 'ACTIVE') return 'partner-program-badge partner-program-badge--paid'
  if (status === 'EXPIRED') return 'partner-program-badge partner-program-badge--muted'
  if (status === 'RULE_DISABLED') return 'partner-program-badge partner-program-badge--rejected'
  if (status === 'INVITEE_INACTIVE') return 'partner-program-badge partner-program-badge--pending'
  return 'partner-program-badge partner-program-badge--muted'
}

export function payoutMethodLabel(method, participantType) {
  if (participantType === 'RESTAURANT') return 'Банковский перевод'
  if (method === 'BANK_TRANSFER') return 'Банковский перевод'
  if (method === 'TRANSFER_TO_MAIN_BALANCE') return 'На общий баланс'
  return method || '—'
}

export const RELATIONSHIP_COPY = {
  COURIER_COURIER: {
    description:
      'Курьер получает партнёрское вознаграждение за доставки курьера, которого он пригласил.',
    receives: 'пригласивший курьер',
    forWhom: 'приглашённый курьер',
    accrual: 'за завершённые доставки приглашённого курьера',
  },
  COURIER_RESTAURANT: {
    description:
      'Курьер получает партнёрское вознаграждение за заказы объекта, который он пригласил.',
    receives: 'пригласивший курьер',
    forWhom: 'приглашённый объект',
    accrual: 'за завершённые заказы приглашённого объекта',
  },
  RESTAURANT_COURIER: {
    description:
      'Объект получает партнёрское вознаграждение за доставки курьера, которого он пригласил.',
    receives: 'пригласивший объект',
    forWhom: 'приглашённый курьер',
    accrual: 'за завершённые доставки приглашённого курьера',
  },
  RESTAURANT_RESTAURANT: {
    description:
      'Объект получает партнёрское вознаграждение за заказы объекта, который он пригласил.',
    receives: 'пригласивший объект',
    forWhom: 'приглашённый объект',
    accrual: 'за завершённые заказы приглашённого объекта',
  },
}

/** @deprecated use RELATIONSHIP_COPY[key].description */
export const RELATIONSHIP_DESCRIPTIONS = Object.fromEntries(
  Object.entries(RELATIONSHIP_COPY).map(([key, copy]) => [key, copy.description]),
)

export function relationshipKey(referrerType, inviteeType) {
  return `${referrerType}_${inviteeType}`
}

export function isPartnerProgramEnabledForService(rules) {
  return (rules || []).some((rule) => rule?.enabled)
}

export function isPartnerProgramEnabledForReferrer(rules, referrerType) {
  return (rules || []).some((rule) => rule?.enabled && rule.referrerType === referrerType)
}

export function isFixedAccrualRule(form) {
  return form.accrualMode === 'FIXED_PER_DELIVERY'
}

export function ruleAccrualMode(rule) {
  if (!rule) return 'PERCENT'
  if (rule.calculationBase === 'FIXED_PER_DELIVERY' || rule.calculationType === 'FIXED') {
    return 'FIXED_PER_DELIVERY'
  }
  return 'PERCENT'
}

/** Backend PartnerCalculationBase по типу приглашённого и режиму начисления */
export function resolveCalculationBase(inviteeType, accrualMode) {
  if (accrualMode === 'FIXED_PER_DELIVERY') {
    return 'FIXED_PER_DELIVERY'
  }
  return inviteeType === 'RESTAURANT' ? 'DELIVERY_PRICE' : 'COURIER_EARNING'
}

export function percentFieldHelper(inviteeType) {
  if (inviteeType === 'RESTAURANT') {
    return 'Процент считается от стоимости доставки по заказу.'
  }
  return 'Процент считается от заработка курьера за завершённую доставку.'
}

export function fixedAmountFieldHelper() {
  return 'Сумма начисляется за каждую завершённую доставку приглашённого участника.'
}

export function buildAccrualExample(form, inviteeType) {
  if (isFixedAccrualRule(form)) {
    const amount = Number(form.fixedAmount)
    const displayAmount = Number.isFinite(amount) && amount > 0 ? amount : 20
    return `За каждую завершённую доставку пригласивший участник получит ${formatMoney(displayAmount)}.`
  }

  const percent = Number(form.percentValue)
  const displayPercent = Number.isFinite(percent) && percent > 0 ? percent : 10

  if (inviteeType === 'RESTAURANT') {
    const baseAmount = 500
    const payout = (baseAmount * displayPercent) / 100
    return `Стоимость доставки ${formatMoney(baseAmount)} × ${displayPercent}% = ${formatMoney(payout)} получит пригласивший участник.`
  }

  const baseAmount = 100
  const payout = (baseAmount * displayPercent) / 100
  return `Заработок курьера ${formatMoney(baseAmount)} × ${displayPercent}% = ${formatMoney(payout)} получит пригласивший участник.`
}

export const PAYOUT_METHOD_OPTIONS = [
  {
    value: 'TRANSFER_TO_MAIN_BALANCE',
    title: 'На общий баланс',
    hint: 'Доступно только для курьеров. Деньги попадут на основной баланс курьера.',
    courierOnly: true,
  },
  {
    value: 'BANK_TRANSFER',
    title: 'Банковский перевод',
    hint: 'Выплата по реквизитам участника.',
    courierOnly: false,
  },
]

export const RESTAURANT_PAYOUT_METHODS = ['BANK_TRANSFER']

export const DEFAULT_COURIER_PAYOUT_METHODS = ['TRANSFER_TO_MAIN_BALANCE', 'BANK_TRANSFER']

const VALID_PAYOUT_METHODS = new Set(['BANK_TRANSFER', 'TRANSFER_TO_MAIN_BALANCE'])

/** Backend UpsertPartnerProgramRuleRequest.payoutMethods — List<PartnerPayoutMethod> */
export function resolvePayoutMethods(referrerType, payoutMethods) {
  if (referrerType === 'RESTAURANT') {
    return [...RESTAURANT_PAYOUT_METHODS]
  }
  const list = Array.isArray(payoutMethods)
    ? payoutMethods.filter((method) => VALID_PAYOUT_METHODS.has(method))
    : []
  return list.length > 0 ? list : [...DEFAULT_COURIER_PAYOUT_METHODS]
}
