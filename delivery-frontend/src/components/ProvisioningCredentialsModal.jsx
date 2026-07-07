import { useCallback, useEffect, useState } from 'react'
import CopySnackbar from './CopySnackbar.jsx'
import AppModal from './AppModal.jsx'
import {
  buildCredentialsCopyText,
  copyToClipboard,
} from '../utils/copyToClipboard.js'
import './ProvisioningCredentialsModal.css'

const DEFAULT_TRANSFER_NOTE =
  'Передайте данные самостоятельно. Временный пароль будет показан только один раз.'

const DEFAULT_FIRST_LOGIN_NOTE =
  'При первом входе пользователь должен установить новый пароль.'

export default function ProvisioningCredentialsModal({
  open,
  title,
  intro,
  login,
  temporaryPassword,
  personHint,
  hideDefaultLabel = false,
  transferNote = DEFAULT_TRANSFER_NOTE,
  firstLoginNote = DEFAULT_FIRST_LOGIN_NOTE,
  primaryAction,
  onPrimaryAction,
  onClose,
}) {
  const displayLogin = (login || '').trim()
  const displayPassword = (temporaryPassword || '').trim()

  const [snackbar, setSnackbar] = useState(null)

  useEffect(() => {
    if (!open) {
      setSnackbar(null)
    }
  }, [open])

  const dismissSnackbar = useCallback(() => {
    setSnackbar(null)
  }, [])

  const onCopyLogin = useCallback(async () => {
    try {
      await copyToClipboard(displayLogin)
      setSnackbar({ message: 'Логин скопирован', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }, [displayLogin])

  const onCopyPassword = useCallback(async () => {
    try {
      await copyToClipboard(displayPassword)
      setSnackbar({ message: 'Пароль скопирован', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }, [displayPassword])

  const onCopyAll = useCallback(async () => {
    const text = buildCredentialsCopyText(displayLogin, displayPassword)
    try {
      await copyToClipboard(text)
      setSnackbar({ message: 'Скопировано', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }, [displayLogin, displayPassword])

  if (!open || !displayLogin || !displayPassword) return null

  return (
    <>
      <AppModal
        open
        title={title}
        onClose={onClose}
        footer={(
          <div className="prov-creds-modal__actions">
            <button type="button" className="btn btn-secondary" onClick={onCopyLogin}>
              Скопировать логин
            </button>
            <button type="button" className="btn btn-secondary" onClick={onCopyPassword}>
              Скопировать пароль
            </button>
            <button type="button" className="btn" onClick={onCopyAll}>
              Скопировать всё
            </button>
            {primaryAction && onPrimaryAction && (
              <button type="button" className="btn btn-secondary" onClick={onPrimaryAction}>
                {primaryAction}
              </button>
            )}
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Закрыть
            </button>
          </div>
        )}
      >
        {intro && <p className="prov-creds-modal__intro">{intro}</p>}
        {personHint && <p className="prov-creds-modal__person muted">{personHint}</p>}
        {!hideDefaultLabel && <p className="prov-creds-modal__label">Передайте данные для входа:</p>}
        <dl className="prov-creds-modal__kv">
          <div>
            <dt>Логин</dt>
            <dd>
              <code>{displayLogin}</code>
            </dd>
          </div>
          <div>
            <dt>Временный пароль</dt>
            <dd>
              <code>{displayPassword}</code>
            </dd>
          </div>
        </dl>
        {transferNote && <p className="prov-creds-modal__note muted">{transferNote}</p>}
        {firstLoginNote && <p className="prov-creds-modal__note muted">{firstLoginNote}</p>}
      </AppModal>
      <CopySnackbar
        message={snackbar?.message ?? null}
        variant={snackbar?.variant ?? 'success'}
        onDismiss={dismissSnackbar}
      />
    </>
  )
}
