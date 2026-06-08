import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import StandaloneLayout from '../layouts/StandaloneLayout.jsx'
import { getSwitchableMemberships, labelForMembership } from '../utils/deliverySession.js'

export default function OrganizationPickerPage() {
  const navigate = useNavigate()
  const { deliveryMe, selectOrganization } = useAuth()
  const switchableMemberships = useMemo(
    () => getSwitchableMemberships(deliveryMe?.memberships),
    [deliveryMe],
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const onPick = async (organizationId) => {
    setBusy(true)
    setError('')
    try {
      const route = await selectOrganization(organizationId)
      navigate(route, { replace: true })
    } catch (e) {
      setError(e?.message || 'Не удалось выбрать организацию')
    } finally {
      setBusy(false)
    }
  }

  return (
    <StandaloneLayout>
    <div className="login-page">
      <div className="card">
        <h1 style={{ marginTop: 0, fontSize: '1.25rem' }}>Выберите организацию</h1>
        <p className="muted">
          У вас несколько ролей в системе доставки. Выберите, с чем работать сейчас.
        </p>
        <div className="role-picker">
          {switchableMemberships.map((m) => (
            <button
              key={`${m.organizationId}-${m.accessKind || 'member'}`}
              type="button"
              disabled={busy}
              onClick={() => onPick(m.organizationId)}
            >
              <div>{labelForMembership(m)}</div>
            </button>
          ))}
        </div>
        {error && <p className="text-error">{error}</p>}
      </div>
    </div>
    </StandaloneLayout>
  )
}
