import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { DEV_AUTH_ENABLED } from '../config.js'
import { USER_STATUS } from '../utils/deliverySession.js'
import StandaloneLayout from '../layouts/StandaloneLayout.jsx'

export default function NoAccessPage() {
  const { logout, deliveryMe, devUiRole, setDevUiRole } = useAuth()

  return (
    <StandaloneLayout>
    <div className="login-page card">
      <h1 style={{ marginTop: 0, fontSize: '1.25rem' }}>Нет доступа к доставке</h1>
      <p className="muted">
        {deliveryMe?.status === USER_STATUS.BLOCKED
          ? 'Ваш доступ временно ограничен.'
          : 'У вас пока нет доступа к интерфейсу доставки.'}
      </p>
      <p style={{ marginTop: 12 }}>
        <Link to="/login?apply=1" className="btn">
          Оставить заявку
        </Link>
      </p>
      {DEV_AUTH_ENABLED && (
        <div style={{ marginTop: 16 }}>
          <p className="muted">Dev: принудительный интерфейс</p>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {['courier', 'restaurant', 'service'].map((r) => (
              <button
                key={r}
                type="button"
                className={devUiRole === r ? 'btn' : 'btn btn-secondary'}
                onClick={() => setDevUiRole(r)}
              >
                {r}
              </button>
            ))}
            <button type="button" className="btn btn-secondary" onClick={() => setDevUiRole('')}>
              сброс
            </button>
          </div>
          {devUiRole && (
            <p style={{ marginTop: 12 }}>
              <Link to={`/${devUiRole}`}>Открыть dev UI →</Link>
            </p>
          )}
        </div>
      )}
      <button type="button" className="btn btn-secondary" style={{ marginTop: 16 }} onClick={logout}>
        Выйти
      </button>
    </div>
    </StandaloneLayout>
  )
}
