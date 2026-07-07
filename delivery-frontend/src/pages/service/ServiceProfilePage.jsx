import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DEV_AUTH_ENABLED } from '../../config.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { canManageServiceSettings } from '../../hooks/useActiveOrg.js'
import {
  displayOrganizationName,
  isCurrentOrganization,
  labelForOrgStatus,
  labelForOrgType,
  labelForRole,
  resolveServiceMembership,
} from '../../utils/displayLabels.js'
import { getSwitchableMemberships, routeForInterfaceMode } from '../../utils/deliverySession.js'
import {
  resolvePendingPhone,
  resolvePendingUserId,
} from '../../utils/pendingCourier.js'

export default function ServiceProfilePage() {
  const navigate = useNavigate()
  const {
    accountUser,
    deliveryMe,
    activeMembership,
    selectOrganization,
    logout,
    devUiRole,
    setDevUiRole,
  } = useAuth()

  const [switchBusyId, setSwitchBusyId] = useState(null)

  const userId = resolvePendingUserId(deliveryMe, accountUser)
  const phone = resolvePendingPhone(accountUser)
  const displayName =
    accountUser?.fullName?.trim() ||
    accountUser?.firstName?.trim() ||
    'Пользователь'

  const switchableMemberships = useMemo(
    () => getSwitchableMemberships(deliveryMe?.memberships),
    [deliveryMe],
  )

  const serviceMembership = useMemo(
    () => resolveServiceMembership(activeMembership, deliveryMe?.memberships),
    [activeMembership, deliveryMe],
  )

  const canManageService = useMemo(
    () => canManageServiceSettings(activeMembership, deliveryMe?.memberships),
    [activeMembership, deliveryMe],
  )

  const serviceRole = activeMembership?.role || serviceMembership?.role
  const serviceStatus = serviceMembership?.status || activeMembership?.status
  const statusBadge = serviceStatus ? labelForOrgStatus(serviceStatus) : null

  const onSwitchOrg = async (organizationId) => {
    if (isCurrentOrganization({ organizationId }, deliveryMe?.activeOrganizationId)) return
    setSwitchBusyId(organizationId)
    try {
      const route = await selectOrganization(organizationId)
      navigate(route)
    } finally {
      setSwitchBusyId(null)
    }
  }

  const serviceName = displayOrganizationName(
    serviceMembership?.organizationName || activeMembership?.organizationName,
  )

  return (
    <div className="service-profile-page">
      <header className="service-profile-page__header">
        <h1 className="service-profile-page__title">Профиль службы</h1>
      </header>

      <section className="card service-profile-hero">
        <div className="service-profile-hero__top">
          <p className="service-profile-hero__type">{labelForOrgType('courier_service')}</p>
          {statusBadge && (
            <span className="service-profile-hero__badge">{statusBadge}</span>
          )}
        </div>
        <h2 className="service-profile-hero__name">{serviceName}</h2>
        {serviceMembership?.organizationPublicId != null && (
          <p className="service-profile-hero__id">
            ID службы {serviceMembership.organizationPublicId}
          </p>
        )}
        <p className="service-profile-hero__role">
          Ваша роль: <strong>{labelForRole(serviceRole)}</strong>
        </p>
      </section>

      {canManageService && (
        <section className="card service-profile-management">
          <h2 className="service-profile-section__title">Управление</h2>
          <div className="service-profile-management__links">
            <Link to="/service/partner-program" className="service-profile-management__item">
              <span className="service-profile-management__item-title">
                Партнёрская программа — настройки
              </span>
              <span className="service-profile-management__item-hint muted">
                Правила начислений, заявки на выплату и журнал приглашений
              </span>
            </Link>
            <Link to="/service/financial-settings" className="service-profile-management__item">
              <span className="service-profile-management__item-title">
                Вознаграждение платформы
              </span>
              <span className="service-profile-management__item-hint muted">
                Процент или фиксированная сумма, удерживаемые из стоимости доставки
              </span>
            </Link>
          </div>
        </section>
      )}

      <section className="card service-profile-user">
        <h2 className="service-profile-section__title">Ваш профиль</h2>
        <dl className="service-profile-dl">
          <div className="service-profile-dl__row">
            <dt>Имя</dt>
            <dd>{displayName}</dd>
          </div>
          {userId != null && (
            <div className="service-profile-dl__row">
              <dt>ID пользователя</dt>
              <dd>{userId}</dd>
            </div>
          )}
          {phone && (
            <div className="service-profile-dl__row">
              <dt>Телефон</dt>
              <dd>{phone}</dd>
            </div>
          )}
        </dl>
      </section>

      {switchableMemberships.length > 1 && (
        <section className="service-profile-orgs">
          <h2 className="service-profile-section__title">Организации</h2>
          <div className="service-profile-orgs__list">
            {switchableMemberships.map((m) => {
              const isCurrent = isCurrentOrganization(m, deliveryMe?.activeOrganizationId)

              return (
                <article
                  key={`${m.organizationId}-${m.accessKind || 'member'}`}
                  className={`card service-profile-org-card${isCurrent ? ' service-profile-org-card--current' : ''}`}
                >
                  <div className="service-profile-org-card__main">
                    <p className="service-profile-org-card__type">{labelForOrgType(m.organizationType)}</p>
                    <p className="service-profile-org-card__role">{labelForRole(m.role)}</p>
                  </div>
                  {isCurrent ? (
                    <span className="service-profile-badge">Текущая</span>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-secondary service-profile-org-card__switch"
                      disabled={switchBusyId != null}
                      onClick={() => onSwitchOrg(m.organizationId)}
                    >
                      {switchBusyId === m.organizationId ? '…' : 'Переключиться'}
                    </button>
                  )}
                </article>
              )
            })}
          </div>
        </section>
      )}

      {DEV_AUTH_ENABLED && (
        <section className="card service-profile-dev">
          <p className="muted">Dev fallback UI</p>
          <div className="service-profile-dev__roles">
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
          </div>
          {devUiRole && (
            <p className="service-profile-dev__link">
              <Link to={routeForInterfaceMode(devUiRole)}>Dev UI</Link>
            </p>
          )}
        </section>
      )}

      <footer className="service-profile-footer">
        <button type="button" className="btn btn-danger service-profile-footer__logout" onClick={logout}>
          Выйти
        </button>
      </footer>
    </div>
  )
}
