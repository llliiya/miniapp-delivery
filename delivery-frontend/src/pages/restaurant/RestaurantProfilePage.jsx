import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DEV_AUTH_ENABLED } from '../../config.js'
import {
  fetchRestaurantPartnerProgram,
  createRestaurantPartnerPayout,
  listOrders,
  listOrganizationMembers,
  listPickupPoints,
} from '../../api/deliveryService.js'
import PartnerProgramSection from '../../components/partner/PartnerProgramSection.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import { labelForRole, labelForStatus } from '../../utils/displayLabels.js'
import { routeForInterfaceMode } from '../../utils/deliverySession.js'
import {
  resolvePendingPhone,
  resolvePendingUserId,
} from '../../utils/pendingCourier.js'

function statusBadgeClass(status) {
  if (status === 'active') return 'role-profile-badge role-profile-badge--active'
  if (status === 'blocked') return 'role-profile-badge role-profile-badge--blocked'
  return 'role-profile-badge'
}

export default function RestaurantProfilePage() {
  const {
    accountUser,
    deliveryMe,
    activeMembership,
    logout,
    devUiRole,
    setDevUiRole,
  } = useAuth()

  const restaurantId = useRestaurantId()

  const [quickStats, setQuickStats] = useState({
    pickupPoints: 0,
    orders: 0,
    staff: 0,
  })

  const userId = resolvePendingUserId(deliveryMe, accountUser)
  const phone = resolvePendingPhone(accountUser)
  const displayName =
    accountUser?.fullName?.trim() ||
    accountUser?.firstName?.trim() ||
    'Пользователь'

  const restaurantMembership = useMemo(() => {
    if (activeMembership?.organizationType === 'client_restaurant') {
      return activeMembership
    }
    return (deliveryMe?.memberships || []).find(
      (m) => m.organizationType === 'client_restaurant' && m.accessKind !== 'service_scope',
    )
  }, [activeMembership, deliveryMe])

  const objectName = restaurantMembership?.organizationName?.trim() || 'Объект'
  const objectPublicId = restaurantMembership?.organizationPublicId
  const memberRole = restaurantMembership?.role
  const memberStatus = restaurantMembership?.status

  useEffect(() => {
    if (!restaurantId) {
      setQuickStats({ pickupPoints: 0, orders: 0, staff: 0 })
      return
    }
    let cancelled = false

    const load = async () => {
      const [pickupResult, ordersResult, staffResult] = await Promise.allSettled([
        listPickupPoints(restaurantId),
        listOrders({ scope: 'restaurant', restaurantId }),
        listOrganizationMembers(restaurantId),
      ])

      if (cancelled) return

      setQuickStats({
        pickupPoints:
          pickupResult.status === 'fulfilled' ? (pickupResult.value?.length ?? 0) : 0,
        orders: ordersResult.status === 'fulfilled' ? (ordersResult.value?.length ?? 0) : 0,
        staff: staffResult.status === 'fulfilled' ? (staffResult.value?.length ?? 0) : 0,
      })
    }

    load()
    return () => {
      cancelled = true
    }
  }, [restaurantId])

  const loadPartnerProgram = useCallback(
    () => fetchRestaurantPartnerProgram(restaurantId),
    [restaurantId],
  )
  const createPayout = useCallback(
    (body) => createRestaurantPartnerPayout(restaurantId, body),
    [restaurantId],
  )

  return (
    <div className="role-profile-page">
      <header className="role-profile-page__header">
        <h1 className="role-profile-page__title">Профиль объекта</h1>
        <p className="role-profile-page__subtitle">Информация об объекте и вашем доступе</p>
      </header>

      <section className="card role-profile-hero role-profile-hero--object">
        <div className="role-profile-hero__title-row">
          <h2 className="role-profile-hero__name">{objectName}</h2>
          <span className={statusBadgeClass(memberStatus)}>{labelForStatus(memberStatus)}</span>
        </div>
        {objectPublicId != null && (
          <p className="role-profile-hero__id">Объект ID {objectPublicId}</p>
        )}
        <p className="role-profile-hero__role">
          Ваша роль: <strong>{labelForRole(memberRole)}</strong>
        </p>
      </section>

      <section className="card role-profile-section">
        <h2 className="role-profile-section__title">Ваш профиль</h2>
        <dl className="role-profile-dl">
          <div className="role-profile-dl__row">
            <dt>Имя</dt>
            <dd>{displayName}</dd>
          </div>
          {userId != null && (
            <div className="role-profile-dl__row">
              <dt>ID пользователя</dt>
              <dd>{userId}</dd>
            </div>
          )}
          {phone && (
            <div className="role-profile-dl__row">
              <dt>Телефон</dt>
              <dd>{phone}</dd>
            </div>
          )}
        </dl>
      </section>

      <section className="role-profile-stats role-profile-stats--three">
        <article className="card role-profile-stat">
          <p className="role-profile-stat__label">Точки забора</p>
          <p className="role-profile-stat__value">{quickStats.pickupPoints}</p>
        </article>
        <article className="card role-profile-stat">
          <p className="role-profile-stat__label">Заказы</p>
          <p className="role-profile-stat__value">{quickStats.orders}</p>
        </article>
        <article className="card role-profile-stat">
          <p className="role-profile-stat__label">Сотрудники</p>
          <p className="role-profile-stat__value">{quickStats.staff}</p>
        </article>
      </section>

      <PartnerProgramSection
        loadProgram={loadPartnerProgram}
        createPayout={createPayout}
        disabled={!restaurantId}
        disabledMessage="Раздел доступен сотрудникам объекта."
      />

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

      <footer className="role-profile-footer">
        <button
          type="button"
          className="btn btn-danger role-profile-footer__logout"
          onClick={logout}
        >
          Выйти из аккаунта
        </button>
      </footer>
    </div>
  )
}
