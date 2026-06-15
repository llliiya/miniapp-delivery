import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const STORAGE_KEY = 'dobrovoz.serviceCity'

const ServiceCityContext = createContext(null)

function readStoredCity() {
  try {
    return sessionStorage.getItem(STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

export function ServiceCityProvider({ children }) {
  const [selectedCity, setSelectedCityState] = useState(readStoredCity)

  const setSelectedCity = useCallback((city) => {
    const value = city || ''
    setSelectedCityState(value)
    try {
      if (value) {
        sessionStorage.setItem(STORAGE_KEY, value)
      } else {
        sessionStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // ignore storage errors
    }
  }, [])

  const value = useMemo(
    () => ({
      selectedCity,
      setSelectedCity,
      cityQueryParam: selectedCity || undefined,
    }),
    [selectedCity, setSelectedCity],
  )

  return <ServiceCityContext.Provider value={value}>{children}</ServiceCityContext.Provider>
}

export function useServiceCity() {
  const ctx = useContext(ServiceCityContext)
  if (!ctx) {
    throw new Error('useServiceCity must be used within ServiceCityProvider')
  }
  return ctx
}
