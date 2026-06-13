import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { canNavigateBack, isMobileViewport, navigateBack } from '../utils/navigationBack.js'

function isModalOpen() {
  return Boolean(
    document.querySelector('.app-modal-overlay')
      || document.querySelector('.share-messenger-sheet-overlay'),
  )
}

function isInteractiveTarget(target) {
  if (!target || !(target instanceof Element)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || tag === 'BUTTON') {
    return true
  }
  return Boolean(
    target.closest('button')
      || target.closest('input')
      || target.closest('textarea')
      || target.closest('select')
      || target.closest('a'),
  )
}

/**
 * Жест «свайп от левого края вправо» для возврата назад (только mobile).
 */
export function useSwipeBack(options = {}) {
  const navigate = useNavigate()
  const location = useLocation()
  const touchStartX = useRef(null)
  const touchStartY = useRef(null)
  const { shouldDisable, onBeforeNavigate } = options

  useEffect(() => {
    if (!isMobileViewport()) return undefined

    const handleTouchStart = (event) => {
      if (shouldDisable?.()) return
      if (isModalOpen()) return
      if (isInteractiveTarget(event.target)) return

      const touch = event.touches[0]
      touchStartX.current = touch.clientX
      touchStartY.current = touch.clientY
    }

    const handleTouchMove = (event) => {
      if (touchStartX.current === null || touchStartX.current > 20) return

      const touch = event.touches[0]
      const deltaX = touch.clientX - touchStartX.current
      const deltaY = touch.clientY - (touchStartY.current || 0)

      if (deltaX > 10 && Math.abs(deltaX) > Math.abs(deltaY) * 2) {
        event.preventDefault()
      }
    }

    const handleTouchEnd = (event) => {
      if (touchStartX.current === null || touchStartY.current === null) return

      if (shouldDisable?.() || isModalOpen()) {
        touchStartX.current = null
        touchStartY.current = null
        return
      }

      const touch = event.changedTouches[0]
      const deltaX = touch.clientX - touchStartX.current
      const deltaY = touch.clientY - touchStartY.current
      const startsFromLeft = touchStartX.current <= 50
      const minSwipeDistance = 100
      const maxVerticalDistance = 50

      if (
        startsFromLeft
        && deltaX > minSwipeDistance
        && Math.abs(deltaY) < maxVerticalDistance
        && Math.abs(deltaX) > Math.abs(deltaY)
      ) {
        if (onBeforeNavigate?.() === false) {
          touchStartX.current = null
          touchStartY.current = null
          return
        }

        navigateBack(navigate)
      }

      touchStartX.current = null
      touchStartY.current = null
    }

    document.addEventListener('touchstart', handleTouchStart, { passive: true })
    document.addEventListener('touchmove', handleTouchMove, { passive: false })
    document.addEventListener('touchend', handleTouchEnd, { passive: true })

    return () => {
      document.removeEventListener('touchstart', handleTouchStart)
      document.removeEventListener('touchmove', handleTouchMove)
      document.removeEventListener('touchend', handleTouchEnd)
    }
  }, [navigate, location.pathname, shouldDisable, onBeforeNavigate])
}

export function useSwipeBackDisabledOnRoots() {
  const location = useLocation()

  useSwipeBack({
    shouldDisable: () => !canNavigateBack(location.pathname),
  })
}
