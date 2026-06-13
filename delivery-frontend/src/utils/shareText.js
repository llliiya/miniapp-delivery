/**
 * @param {string} text
 */
export function buildTelegramShareUrl(text) {
  return `https://t.me/share/url?text=${encodeURIComponent(text)}`
}

/**
 * @param {string} text
 */
export function buildMaxShareUrl(text) {
  return `https://max.ru/:share?text=${encodeURIComponent(text)}`
}

/**
 * @param {string} url
 * @param {{ preferTelegramLink?: boolean }} [options]
 */
function openShareUrl(url, options = {}) {
  if (options.preferTelegramLink) {
    const tg = window.Telegram?.WebApp
    if (typeof tg?.openTelegramLink === 'function') {
      tg.openTelegramLink(url)
      return
    }
  }

  const wa = window.WebApp
  if (wa && typeof wa.openLink === 'function') {
    wa.openLink(url)
    return
  }

  const tg = window.Telegram?.WebApp
  if (typeof tg?.openLink === 'function') {
    tg.openLink(url, { try_browser: true })
    return
  }

  window.open(url, '_blank', 'noopener,noreferrer')
}

/**
 * @param {string} text
 * @returns {Promise<'shared' | 'cancelled' | 'opened'>}
 */
export async function shareViaTelegram(text) {
  openShareUrl(buildTelegramShareUrl(text), { preferTelegramLink: true })
  return 'opened'
}

/**
 * @param {string} text
 * @returns {Promise<'shared' | 'cancelled' | 'opened'>}
 */
export async function shareViaMax(text) {
  const wa = window.WebApp
  if (typeof wa?.shareMaxContent === 'function') {
    const result = await wa.shareMaxContent({ text })
    return result?.status === 'shared' ? 'shared' : 'cancelled'
  }
  if (typeof wa?.shareContent === 'function') {
    const result = await wa.shareContent({ text })
    return result?.status === 'shared' ? 'shared' : 'cancelled'
  }

  openShareUrl(buildMaxShareUrl(text))
  return 'opened'
}
