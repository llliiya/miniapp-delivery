import { useEffect, useState } from 'react'
import { isMobileViewport } from '../utils/navigationBack.js'

export function useMobileViewport() {
  const [mobile, setMobile] = useState(() => isMobileViewport())

  useEffect(() => {
    const onResize = () => setMobile(isMobileViewport())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  return mobile
}
