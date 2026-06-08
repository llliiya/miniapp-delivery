/**
 * Открывает Яндекс.Карты с маршрутом во внешнем браузере/приложении.
 * Внутри миниаппа навигация не меняется.
 *
 * @param {string} yandexUrl
 */
export function openYandexMapsRoute(yandexUrl) {
  if (!yandexUrl) return false

  const tg = window.Telegram?.WebApp
  if (typeof tg?.openLink === 'function') {
    tg.openLink(yandexUrl, { try_browser: true })
    return true
  }

  const wa = window.WebApp
  if (wa && typeof wa.openLink === 'function') {
    wa.openLink(yandexUrl)
    return true
  }

  window.open(yandexUrl, '_blank', 'noopener,noreferrer')
  return true
}
