import { useCallback, useEffect, useState } from 'react'
import CopySnackbar from './CopySnackbar.jsx'
import AppModal from './AppModal.jsx'
import { buildCredentialsCopyText, copyToClipboard } from '../utils/copyToClipboard.js'
import './ProvisioningCredentialsModal.css'

const DEFAULT_FIRST_LOGIN_NOTE =
  'При первом входе пользователь должен будет сменить пароль. Данные показываются только один раз.'

export default function ProvisioningCredentialsModal({
  open,
  title,
  intro,
  login,
  temporaryPassword,
  personHint,
  hideDefaultLabel = false,
  firstLoginNote = DEFAULT_FIRST_LOGIN_NOTE,
  primaryAction,
  onPrimaryAction,
  onClose,
}) {
  const [snackbar, setSnackbar] = useState(null)

  useEffect(() => {
    if (!open) {
      setSnackbar(null)
    }
  }, [open])

  const dismissSnackbar = useCallback(() => {
    setSnackbar(null)
  }, [])

  const onCopy = useCallback(async () => {
    const safeLogin = (login || '').trim()
    const safePassword = (temporaryPassword || '').trim()
    const text = buildCredentialsCopyText(safeLogin, safePassword)
    try {
      await copyToClipboard(text)
      setSnackbar({ message: 'Скопировано', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }, [login, temporaryPassword])

  if (!open || !login || !temporaryPassword) return null

  const displayLogin = (login || '').trim()
  const displayPassword = (temporaryPassword || '').trim()

  return (
    <>
      <AppModal
        open
        title={title}
        onClose={onClose}
        footer={(
          <div className="prov-creds-modal__actions">
            <button type="button" className="btn" onClick={onCopy}>
              Скопировать логин и пароль
            </button>
            {primaryAction && onPrimaryAction && (
              <button type="button" className="btn btn-secondary" onClick={onPrimaryAction}>
                {primaryAction}
              </button>
            )}
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
        <p className="prov-creds-modal__note muted">{firstLoginNote}</p>
      </AppModal>
      <CopySnackbar
        message={snackbar?.message ?? null}
        variant={snackbar?.variant ?? 'success'}
        onDismiss={dismissSnackbar}
      />
    </>
  )
}
