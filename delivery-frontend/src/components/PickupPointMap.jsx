import { useEffect, useRef, useState } from 'react'
import { loadYandexMaps } from '../utils/loadYandexMaps.js'

const DEFAULT_CENTER = { lat: 55.7558, lon: 37.6173 }

/**
 * @param {{
 *   apiKey: string
 *   position: { lat: number, lon: number } | null
 *   className?: string
 * }} props
 */
export default function PickupPointMap({ apiKey, position, className = '' }) {
  const hostRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const [phase, setPhase] = useState('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiKey?.trim()) {
      setPhase('error')
      setError('Карта недоступна без API-ключа')
      return
    }

    let cancelled = false
    setPhase('loading')
    setError('')

    loadYandexMaps(apiKey)
      .then((ymaps) => {
        if (cancelled || !hostRef.current) return
        const center = position
          ? [position.lat, position.lon]
          : [DEFAULT_CENTER.lat, DEFAULT_CENTER.lon]
        const map = new ymaps.Map(
          hostRef.current,
          {
            center,
            zoom: position ? 16 : 10,
            controls: ['zoomControl'],
          },
          { suppressMapOpenBlock: true },
        )
        mapRef.current = map
        setPhase('ready')
      })
      .catch((e) => {
        if (cancelled) return
        setPhase('error')
        setError(e instanceof Error ? e.message : 'Ошибка карты')
      })

    return () => {
      cancelled = true
      if (mapRef.current) {
        try {
          mapRef.current.destroy()
        } catch {
          /* ignore */
        }
        mapRef.current = null
      }
      markerRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- init once per apiKey
  }, [apiKey])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !window.ymaps || phase !== 'ready') return

    if (markerRef.current) {
      map.geoObjects.remove(markerRef.current)
      markerRef.current = null
    }

    if (!position || !Number.isFinite(position.lat) || !Number.isFinite(position.lon)) {
      return
    }

    const placemark = new window.ymaps.Placemark(
      [position.lat, position.lon],
      {},
      { preset: 'islands#redDotIcon' },
    )
    map.geoObjects.add(placemark)
    markerRef.current = placemark
    map.setCenter([position.lat, position.lon], 16, { duration: 200 })
  }, [position?.lat, position?.lon, phase])

  return (
    <div className={`pickup-point-map ${className}`.trim()}>
      {phase === 'loading' ? (
        <div className="pickup-point-map__placeholder">Загрузка карты…</div>
      ) : null}
      {phase === 'error' ? (
        <div className="pickup-point-map__placeholder pickup-point-map__placeholder--error">
          {error}
        </div>
      ) : null}
      {!position && phase === 'ready' ? (
        <div className="pickup-point-map__overlay muted">Выберите адрес из списка</div>
      ) : null}
      <div ref={hostRef} className="pickup-point-map__host" />
    </div>
  )
}
