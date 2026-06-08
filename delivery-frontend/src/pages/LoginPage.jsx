import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import StandaloneLayout from '../layouts/StandaloneLayout.jsx'
import AuthPage from './auth/AuthPage.jsx'
import { DEV_AUTH_ENABLED } from '../config.js'
import { setToken } from '../utils/tokenStorage.js'
import { resolveClientPlatform } from '../utils/clientPlatform.js'
import { capturePendingOrderDeeplink } from '../utils/deeplink.js'
import { fetchMessengerRegistrationStatus } from '../api/deliveryService.js'
import { isMessengerContext, resolveMessengerIdentity } from '../utils/messengerIdentity.js'
import { getMessengerNeedLink } from './auth/messengerLinkSession.js'
import '../pages/auth/AuthPage.css'

function DevTokenFallback({ onSuccess }) {
  const [open, setOpen] = useState(false)
  const [tokenInput, setTokenInput] = useState('')
  const [error, setError] = useState('')

  if (!DEV_AUTH_ENABLED) {
    return null
  }

  const onContinue = async () => {
    setError('')
    const trimmed = tokenInput.trim()
    if (!trimmed) {
      setError('Вставьте access token.')
      return
    }
    setToken(trimmed)
    window.dispatchEvent(new Event('reauth'))
    await onSuccess()
  }

  return (
    <div style={{ maxWidth: 400, margin: '24px auto 0', padding: '0 16px' }}>
      <button
        type="button"
        className="auth-link"
        style={{ fontSize: 12, opacity: 0.6 }}
        onClick={() => setOpen((v) => !v)}
      >
        {open ? 'Скрыть dev token' : 'Dev: вставить token'}
      </button>
      {open && (
        <div className="card" style={{ marginTop: 12 }}>
          <textarea
            rows={3}
            value={tokenInput}
            onChange={(e) => setTokenInput(e.target.value)}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }}
            placeholder="eyJhbGciOiJIUzI1NiIs..."
          />
          {error && <p style={{ color: '#b91c1c', fontSize: 13 }}>{error}</p>}
          <button type="button" className="btn" style={{ marginTop: 8, width: '100%' }} onClick={onContinue}>
            Применить token
          </button>
        </div>
      )}
    </div>
  )
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { loading, isAuthenticated, refresh } = useAuth()
  const [platformResolving, setPlatformResolving] = useState(true)
  const [messengerIdentity, setMessengerIdentity] = useState(null)
  const [messengerApplicationPending, setMessengerApplicationPending] = useState(false)

  const initialOpenRegistration = searchParams.get('apply') === '1'

  useEffect(() => {
    capturePendingOrderDeeplink()
  }, [])

  useEffect(() => {
    let cancelled = false
    resolveClientPlatform()
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setPlatformResolving(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!isMessengerContext() || platformResolving) return
    let cancelled = false
    resolveMessengerIdentity()
      .then(async (identity) => {
        if (cancelled || !identity) return
        setMessengerIdentity(identity)
        try {
          const status = await fetchMessengerRegistrationStatus(
            identity.provider,
            identity.externalId,
          )
          if (cancelled) return
          if (status?.applicationPending) {
            setMessengerApplicationPending(true)
          }
        } catch {
          /* ignore */
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [platformResolving])

  useEffect(() => {
    if (!loading && isAuthenticated) {
      navigate('/', { replace: true })
    }
  }, [loading, isAuthenticated, navigate])

  useEffect(() => {
    if (loading || isAuthenticated || platformResolving) return
    if (isMessengerContext() && getMessengerNeedLink()) {
      navigate('/messenger/link', { replace: true })
    }
  }, [loading, isAuthenticated, platformResolving, navigate])

  const onAuthSuccess = async () => {
    await refresh()
    navigate('/', { replace: true })
  }

  if (loading && isAuthenticated) {
    return (
      <div className="auth-page-platform-overlay" aria-busy="true">
        <div className="auth-page-platform-spinner-card">
          <div className="auth-page-platform-spinner-ring" aria-hidden />
          <p className="auth-page-platform-spinner-text">Вход выполнен</p>
        </div>
      </div>
    )
  }

  return (
    <StandaloneLayout>
      <AuthPage
        brandTitle="Добровоз"
        platformResolving={platformResolving}
        onAuthSuccess={onAuthSuccess}
        initialOpenRegistration={initialOpenRegistration}
        messengerIdentity={messengerIdentity}
        messengerApplicationPending={messengerApplicationPending}
      />
      <DevTokenFallback onSuccess={onAuthSuccess} />
    </StandaloneLayout>
  )
}
