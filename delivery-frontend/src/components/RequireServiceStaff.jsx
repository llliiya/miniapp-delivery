import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { canManageServiceSettings } from '../hooks/useActiveOrg.js'

export default function RequireServiceStaff({ children }) {
  const { loading, activeMembership, deliveryMe } = useAuth()

  if (loading) {
    return <div className="card" style={{ margin: 16 }}>Загрузка…</div>
  }

  if (!canManageServiceSettings(activeMembership, deliveryMe?.memberships)) {
    return (
      <div className="card service-access-denied">
        <h2 className="service-access-denied__title">Нет доступа</h2>
        <p className="service-access-denied__text">
          Раздел партнёрской программы доступен только собственнику или менеджеру курьерской службы.
        </p>
        <Link to="/service/profile" className="btn btn-secondary">
          Вернуться в профиль службы
        </Link>
      </div>
    )
  }

  return children
}
