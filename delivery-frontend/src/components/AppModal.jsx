import { useEffect } from 'react'
import './AppModal.css'

export default function AppModal({ open, title, onClose, children, footer }) {
  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = prevOverflow
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="app-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'app-modal-title' : undefined}
      onClick={onClose}
    >
      <div className="app-modal card" onClick={(event) => event.stopPropagation()}>
        <div className="app-modal__header">
          {title ? (
            <h2 id="app-modal-title" className="app-modal__title">
              {title}
            </h2>
          ) : (
            <span />
          )}
          <button type="button" className="app-modal__close" onClick={onClose} aria-label="Закрыть">
            ×
          </button>
        </div>
        <div className="app-modal__body">{children}</div>
        {footer ? <div className="app-modal__footer">{footer}</div> : null}
      </div>
    </div>
  )
}
