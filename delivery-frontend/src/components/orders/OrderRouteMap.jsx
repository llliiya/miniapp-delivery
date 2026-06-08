import { useEffect, useMemo, useRef, useState } from 'react'
import { searchAddressSuggestions, resolveAddressSelection } from '../../services/geocoding.js'
import { loadYandexMaps } from '../../utils/loadYandexMaps.js'
import { buildYandexMapsRouteUrl } from '../../utils/navigatorUrl.js'
import { openYandexMapsRoute } from '../../utils/openExternalMap.js'
import '../../pages/restaurant/PickupPointsPage.css'

const YANDEX_KEY = (import.meta.env.VITE_YANDEX_MAPS_API_KEY || '').trim()

function hasCoords(lat, lon) {
  return Number.isFinite(lat) && Number.isFinite(lon)
}

async function resolvePoint(lat, lon, address) {
  if (hasCoords(lat, lon)) {
    return { lat, lon }
  }
  const text = typeof address === 'string' ? address.trim() : ''
  if (!text) return null
  const suggestions = await searchAddressSuggestions(text)
  if (!suggestions[0]) return null
  const resolved = await resolveAddressSelection(suggestions[0])
  if (!resolved || !hasCoords(resolved.lat, resolved.lon)) return null
  return { lat: resolved.lat, lon: resolved.lon }
}

export default function OrderRouteMap({ order }) {
  const hostRef = useRef(null)
  const mapRef = useRef(null)
  const routeRef = useRef(null)
  const markersRef = useRef([])
  const [phase, setPhase] = useState('loading')
  const [error, setError] = useState('')
  const [points, setPoints] = useState(null)

  const yandexRouteUrl = useMemo(() => buildYandexMapsRouteUrl(order, points), [order, points])

  const handleOpenNavigator = () => {
    if (!yandexRouteUrl) return
    openYandexMapsRoute(yandexRouteUrl)
  }

  useEffect(() => {
    let cancelled = false

    async function loadPoints() {
      setPhase('loading')
      setError('')
      try {
        const [pickup, delivery] = await Promise.all([
          resolvePoint(order.pickupLat, order.pickupLon, order.pickupAddress),
          resolvePoint(order.deliveryLat, order.deliveryLon, order.deliveryAddress),
        ])
        if (cancelled) return
        if (!pickup && !delivery) {
          setPoints(null)
          setPhase('error')
          setError('Не удалось построить маршрут')
          return
        }
        setPoints({ pickup, delivery })
      } catch {
        if (!cancelled) {
          setPoints(null)
          setPhase('error')
          setError('Ошибка загрузки маршрута')
        }
      }
    }

    loadPoints()
    return () => {
      cancelled = true
    }
  }, [
    order.pickupLat,
    order.pickupLon,
    order.pickupAddress,
    order.deliveryLat,
    order.deliveryLon,
    order.deliveryAddress,
  ])

  useEffect(() => {
    if (!points || !YANDEX_KEY) return undefined

    let cancelled = false
    setPhase('loading')
    setError('')

    loadYandexMaps(YANDEX_KEY)
      .then((ymaps) => {
        if (cancelled || !hostRef.current) return

        if (mapRef.current) {
          try {
            mapRef.current.destroy()
          } catch {
            /* ignore */
          }
          mapRef.current = null
        }
        routeRef.current = null
        markersRef.current = []

        const center = points.pickup
          ? [points.pickup.lat, points.pickup.lon]
          : [points.delivery.lat, points.delivery.lon]

        const map = new ymaps.Map(
          hostRef.current,
          {
            center,
            zoom: 12,
            controls: ['zoomControl'],
          },
          { suppressMapOpenBlock: true },
        )
        mapRef.current = map

        const clearRoute = () => {
          if (routeRef.current) {
            map.geoObjects.remove(routeRef.current)
            routeRef.current = null
          }
          markersRef.current.forEach((marker) => map.geoObjects.remove(marker))
          markersRef.current = []
        }

        clearRoute()

        if (points.pickup && points.delivery) {
          const multiRoute = new ymaps.multiRouter.MultiRoute(
            {
              referencePoints: [
                [points.pickup.lat, points.pickup.lon],
                [points.delivery.lat, points.delivery.lon],
              ],
              params: { routingMode: 'auto' },
            },
            {
              boundsAutoApply: true,
              wayPointVisible: false,
              viaPointVisible: false,
            },
          )
          map.geoObjects.add(multiRoute)
          routeRef.current = multiRoute

          const pickupMark = new ymaps.Placemark(
            [points.pickup.lat, points.pickup.lon],
            { iconContent: 'A' },
            { preset: 'islands#greenStretchyIcon' },
          )
          const deliveryMark = new ymaps.Placemark(
            [points.delivery.lat, points.delivery.lon],
            { iconContent: 'B' },
            { preset: 'islands#redStretchyIcon' },
          )
          map.geoObjects.add(pickupMark)
          map.geoObjects.add(deliveryMark)
          markersRef.current = [pickupMark, deliveryMark]
        } else {
          const point = points.pickup || points.delivery
          const placemark = new ymaps.Placemark(
            [point.lat, point.lon],
            {},
            { preset: 'islands#blueDotIcon' },
          )
          map.geoObjects.add(placemark)
          markersRef.current = [placemark]
          map.setCenter([point.lat, point.lon], 15)
        }

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
      routeRef.current = null
      markersRef.current = []
    }
  }, [points])

  const navButton = yandexRouteUrl ? (
    <button
      type="button"
      className="btn order-route-map__nav-btn"
      onClick={handleOpenNavigator}
    >
      👉 Построить маршрут
    </button>
  ) : null

  return (
    <div className="order-route-map-wrap">
      <div className="order-route-map pickup-point-map">
        {!YANDEX_KEY ? (
          <div className="pickup-point-map__placeholder">Карта недоступна</div>
        ) : (
          <>
            {phase === 'loading' ? (
              <div className="pickup-point-map__placeholder">Строим маршрут…</div>
            ) : null}
            {phase === 'error' ? (
              <div className="pickup-point-map__placeholder pickup-point-map__placeholder--error">{error}</div>
            ) : null}
            <div ref={hostRef} className="pickup-point-map__host" />
            {phase === 'ready' && points?.pickup && points?.delivery ? (
              <div className="pickup-point-map__overlay muted">A — забрать · B — доставить</div>
            ) : null}
          </>
        )}
      </div>
      {navButton}
    </div>
  )
}
