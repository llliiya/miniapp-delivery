import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import './CopySnackbar.css'

const SNACKBAR_MS = 2500

/**
 * Toast внизу экрана; не блокирует UI, исчезает автоматически.
 * @param {{ message: string | null, variant?: 'success' | 'error', onDismiss: () => void }} props
 */
export default function CopySnackbar({ message, variant = 'success', onDismiss }) {
  useEffect(() => {
    if (!message) return undefined
    const timer = window.setTimeout(() => {
      onDismiss()
    }, SNACKBAR_MS)
    return () => window.clearTimeout(timer)
  }, [message, onDismiss])

  if (!message || typeof document === 'undefined') {
    return null
  }

  return createPortal(
    <div
      className={`copy-snackbar copy-snackbar--${variant}`}
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      {message}
    </div>,
    document.body,
  )
}
