import { useCallback, useEffect, useState } from 'react'
import { listServiceCities } from '../../api/deliveryService.js'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { useServiceCity } from '../../context/ServiceCityContext.jsx'

function cityInList(cities, selected) {
  if (!selected) return true
  const norm = selected.trim().toLowerCase()
  return cities.some((c) => c.trim().toLowerCase() === norm)
}

function CityPinIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M12 21s7-4.5 7-11a7 7 0 1 0-14 0c0 6.5 7 11 7 11Z"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="10" r="2.25" stroke="currentColor" strokeWidth="1.75" />
    </svg>
  )
}

export default function ServiceCitySwitch() {
  const courierServiceId = useCourierServiceId()
  const { selectedCity, setSelectedCity } = useServiceCity()
  const [cities, setCities] = useState([])
  const [loading, setLoading] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setCities([])
      return
    }
    setLoading(true)
    setLoadFailed(false)
    try {
      setCities((await listServiceCities(courierServiceId)) || [])
    } catch {
      setCities([])
      setLoadFailed(true)
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  useEffect(() => {
    if (loading) return
    if (loadFailed || !cityInList(cities, selectedCity)) {
      if (selectedCity) setSelectedCity('')
    }
  }, [cities, selectedCity, setSelectedCity, loading, loadFailed])

  if (!courierServiceId) {
    return null
  }

  return (
    <label
      className={`service-city-switch${selectedCity ? ' service-city-switch--active' : ''}`}
    >
      <span className="service-city-switch__icon">
        <CityPinIcon />
      </span>
      <select
        className="service-city-switch__select"
        value={selectedCity}
        onChange={(e) => setSelectedCity(e.target.value)}
        disabled={loading}
        aria-label="Фильтр по городу"
      >
        <option value="">{loading ? 'Загрузка…' : 'Все города'}</option>
        {!loading &&
          cities.map((city) => (
            <option key={city} value={city}>
              {city}
            </option>
          ))}
      </select>
    </label>
  )
}
