import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageContainer from '../../components/PageContainer'
import StandaloneLayout from '../../layouts/StandaloneLayout.jsx'
import { messengerLink } from '../../api/messengerAuthService.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { resolveClientPlatform } from '../../utils/clientPlatform.js'
import { readMessengerAuthContext } from '../../utils/messengerAuthContext.js'
import { isMessengerContext } from '../../utils/messengerIdentity.js'
import { getMaxUserIdWithSource, waitForMaxUserIdWithSource } from '../../utils/maxEnv.js'
import { getPlatform } from '../../utils/platform.js'
import { setToken } from '../../utils/tokenStorage.js'
import {
  clearMessengerNeedLink,
  getMessengerNeedLink,
} from './messengerLinkSession.js'
import './AuthPage.css'

export default function MessengerLinkPage() {
  const navigate = useNavigate()
  const { loading, isAuthenticated, refresh } = useAuth()
  const [platformResolving, setPlatformResolving] = useState(true)
  const [login, setLogin] = useState('')
  const [password, setPasswordVal] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [ctx, setCtx] = useState(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      await resolveClientPlatform().catch(() => {})
      if (cancelled) return
      if (getPlatform() === 'max' && !getMaxUserIdWithSource().userId) {
        await waitForMaxUserIdWithSource(8000, 100).catch(() => {})
      }
      if (cancelled) return
      setCtx(readMessengerAuthContext())
      setPlatformResolving(false)
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!loading && isAuthenticated) {
      clearMessengerNeedLink()
      navigate('/', { replace: true })
    }
  }, [loading, isAuthenticated, navigate])

  useEffect(() => {
    if (loading || isAuthenticated || platformResolving) return
    if (!isMessengerContext()) {
      navigate('/login', { replace: true })
      return
    }
    if (!getMessengerNeedLink()) {
      navigate('/login', { replace: true })
    }
  }, [loading, isAuthenticated, platformResolving, navigate])

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const authCtx = ctx || readMessengerAuthContext()
    if (!authCtx?.messengerUserId) {
      setError('Не удалось получить данные мессенджера. Закройте и откройте мини-приложение снова.')
      return
    }
    const trimmedLogin = login.trim()
    const trimmedPassword = password.trim()
    if (!trimmedLogin || !trimmedPassword) {
      setError('Введите логин и пароль.')
      return
    }
    setSubmitting(true)
    try {
      const data = await messengerLink({
        login: trimmedLogin,
        password: trimmedPassword,
        platform: authCtx.platform,
        messengerUserId: authCtx.messengerUserId,
        messengerUsername: authCtx.messengerUsername,
        initData: authCtx.initData,
      })
      const token = data?.accessToken ?? data?.token
      if (!token) {
        throw new Error('Токен не получен от сервера')
      }
      setToken(token)
      clearMessengerNeedLink()
      window.dispatchEvent(new Event('reauth'))
      await refresh()
      navigate('/', { replace: true })
    } catch (err) {
      if (err?.status === 409) {
        setError(err.message || 'Этот мессенджер уже используется другим аккаунтом.')
      } else if (err?.status === 401) {
        setError('Неверный логин или пароль.')
      } else {
        setError(err?.message || 'Не удалось привязать аккаунт.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (platformResolving || (loading && isAuthenticated)) {
    return (
      <div className="auth-page-platform-overlay" aria-busy="true">
        <div className="auth-page-platform-spinner-card">
          <div className="auth-page-platform-spinner-ring" aria-hidden />
          <p className="auth-page-platform-spinner-text">Загрузка</p>
        </div>
      </div>
    )
  }

  return (
    <StandaloneLayout>
      <PageContainer>
        <div className="auth-page">
          <h1 className="auth-title">Добро пожаловать в Добровоз</h1>
          <p className="auth-hint">
            Чтобы использовать Mini App, необходимо один раз подтвердить аккаунт.
          </p>
          <form onSubmit={onSubmit} className="auth-form">
            <label className="auth-label" htmlFor="messenger-link-login">
              Логин
            </label>
            <input
              id="messenger-link-login"
              className="auth-input"
              type="text"
              autoComplete="username"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              disabled={submitting}
            />
            <label className="auth-label" htmlFor="messenger-link-password">
              Пароль
            </label>
            <input
              id="messenger-link-password"
              className="auth-input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPasswordVal(e.target.value)}
              disabled={submitting}
            />
            {error && <p className="auth-error">{error}</p>}
            <button type="submit" className="btn auth-submit" disabled={submitting}>
              {submitting ? 'Привязка…' : 'Привязать аккаунт'}
            </button>
          </form>
        </div>
      </PageContainer>
    </StandaloneLayout>
  )
}
