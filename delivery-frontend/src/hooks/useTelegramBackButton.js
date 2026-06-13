import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { canNavigateBack, navigateBack } from '../utils/navigationBack.js'

export function useTelegramBackButton() {
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const backButton = window.Telegram?.WebApp?.BackButton
    if (!backButton) return undefined

    const onBack = () => navigateBack(navigate)

    if (canNavigateBack(location.pathname)) {
      backButton.show()
      backButton.onClick(onBack)
      return () => {
        backButton.offClick(onBack)
        backButton.hide()
      }
    }

    backButton.hide()
    backButton.offClick(onBack)
    return undefined
  }, [location.pathname, navigate])
}
