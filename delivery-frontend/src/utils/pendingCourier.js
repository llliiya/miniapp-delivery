export function resolvePendingUserId(deliveryMe, accountUser) {
  return deliveryMe?.userId ?? accountUser?.id ?? null
}

export function resolvePendingPhone(accountUser) {
  const phone = accountUser?.phone
  if (typeof phone === 'string' && phone.trim()) return phone.trim()
  return null
}
