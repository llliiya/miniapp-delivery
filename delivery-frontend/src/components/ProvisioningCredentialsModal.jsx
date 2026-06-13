import { useCallback, useEffect, useState } from 'react'
import CopySnackbar from './CopySnackbar.jsx'
import AppModal from './AppModal.jsx'
import ShareMessengerSheet from './ShareMessengerSheet.jsx'
import {
  buildCredentialsCopyText,
  buildCredentialsShareText,
  copyToClipboard,
} from '../utils/copyToClipboard.js'
import { shareViaMax, shareViaTelegram } from '../utils/shareText.js'
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
  const displayLogin = (login || '').trim()
  const displayPassword = (temporaryPassword || '').trim()
  const shareText = buildCredentialsShareText(displayLogin, displayPassword, { personHint })

  const [snackbar, setSnackbar] = useState(null)
  const [shareSheetOpen, setShareSheetOpen] = useState(false)

  useEffect(() => {
    if (!open) {
      setSnackbar(null)
      setShareSheetOpen(false)
    }
  }, [open])

  const dismissSnackbar = useCallback(() => {
    setSnackbar(null)
  }, [])

  const onCopy = useCallback(async () => {
    const text = buildCredentialsCopyText(displayLogin, displayPassword)
    try {
      await copyToClipboard(text)
      setSnackbar({ message: 'Скопировано', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось скопировать', variant: 'error' })
    }
  }, [displayLogin, displayPassword])

  const onShare = useCallback(() => {
    setShareSheetOpen(true)
  }, [])

  const onShareTelegram = useCallback(async () => {
    try {
      const result = await shareViaTelegram(shareText)
      setShareSheetOpen(false)
      if (result === 'cancelled') return
      setSnackbar({ message: 'Открыт Telegram', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось поделиться', variant: 'error' })
    }
  }, [shareText])

  const onShareMax = useCallback(async () => {
    try {
      const result = await shareViaMax(shareText)
      setShareSheetOpen(false)
      if (result === 'cancelled') return
      setSnackbar({ message: 'Открыт MAX', variant: 'success' })
    } catch {
      setSnackbar({ message: 'Не удалось поделиться', variant: 'error' })
    }
  }, [shareText])

  if (!open || !displayLogin || !displayPassword) return null

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
            <button type="button" className="btn btn-secondary" onClick={onShare}>
              Поделиться
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
      <ShareMessengerSheet
        open={shareSheetOpen}
        onClose={() => setShareSheetOpen(false)}
        onShareTelegram={onShareTelegram}
        onShareMax={onShareMax}
      />
      <CopySnackbar
        message={snackbar?.message ?? null}
        variant={snackbar?.variant ?? 'success'}
        onDismiss={dismissSnackbar}
      />
    </>
  )
}
