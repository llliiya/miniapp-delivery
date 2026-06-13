import { useEffect } from 'react'
import './ShareMessengerSheet.css'

export default function ShareMessengerSheet({ open, onClose, onShareTelegram, onShareMax }) {
  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="share-messenger-sheet-overlay" onClick={onClose}>
      <div
        className="share-messenger-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-messenger-sheet-title"
        onClick={(event) => event.stopPropagation()}
      >
        <p id="share-messenger-sheet-title" className="share-messenger-sheet__title">
          Поделиться через
        </p>
        <div className="share-messenger-sheet__actions">
          <button type="button" className="btn" onClick={onShareTelegram}>
            Telegram
          </button>
          <button type="button" className="btn btn-secondary" onClick={onShareMax}>
            MAX
          </button>
          <button type="button" className="share-messenger-sheet__cancel" onClick={onClose}>
            Отмена
          </button>
        </div>
      </div>
    </div>
  )
}
