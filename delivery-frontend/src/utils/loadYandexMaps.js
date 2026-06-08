const SCRIPT_ID = 'yandex-maps-delivery'

/**
 * @param {string} apiKey
 * @returns {Promise<typeof window.ymaps>}
 */
export function loadYandexMaps(apiKey) {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Yandex Maps: нет window'))
  }
  if (!apiKey?.trim()) {
    return Promise.reject(new Error('Не задан VITE_YANDEX_MAPS_API_KEY'))
  }
  if (window.ymaps) {
    return new Promise((resolve, reject) => {
      window.ymaps.ready(() => resolve(window.ymaps), (e) => reject(e || new Error('ymaps.ready')))
    })
  }

  const existing = document.getElementById(SCRIPT_ID)
  if (existing) {
    return new Promise((resolve, reject) => {
      const done = () => {
        if (window.ymaps) {
          window.ymaps.ready(() => resolve(window.ymaps), (e) => reject(e || new Error('ymaps.ready')))
        } else {
          reject(new Error('Скрипт Яндекс.Карт без ymaps'))
        }
      }
      if (window.ymaps) {
        done()
        return
      }
      existing.addEventListener('load', done)
      existing.addEventListener('error', () => reject(new Error('Ошибка загрузки скрипта')))
    })
  }

  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.id = SCRIPT_ID
    s.async = true
    s.src = `https://api-maps.yandex.ru/2.1/?apikey=${encodeURIComponent(apiKey.trim())}&lang=ru_RU`
    s.onload = () => {
      if (!window.ymaps) {
        reject(new Error('Yandex Maps: ymaps не определён'))
        return
      }
      window.ymaps.ready(() => resolve(window.ymaps), (e) => reject(e || new Error('ymaps.ready')))
    }
    s.onerror = () => reject(new Error('Не удалось загрузить JS API Яндекс.Карт'))
    document.head.appendChild(s)
  })
}
