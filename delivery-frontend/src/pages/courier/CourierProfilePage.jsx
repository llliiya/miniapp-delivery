import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DEV_AUTH_ENABLED } from '../../config.js'
import { fetchCourierPartnerProgram, getCourier } from '../../api/deliveryService.js'
import CourierBalanceSection from '../../components/courier/CourierBalanceSection.jsx'
import PartnerProgramSection from '../../components/partner/PartnerProgramSection.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { labelForStatus } from '../../utils/displayLabels.js'
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

export default function CourierProfilePage() {
  const {
    accountUser,
    deliveryMe,
    activeMembership,
    isPendingCourier,
    isBlockedCourier,
    logout,
    refreshDeliveryStatus,
    devUiRole,
    setDevUiRole,
  } = useAuth()

  const [courierStats, setCourierStats] = useState({ balance: 0, completedOrdersCount: 0 })
  const [refreshing, setRefreshing] = useState(false)
  const [balanceReloadKey, setBalanceReloadKey] = useState(0)

  const userId = resolvePendingUserId(deliveryMe, accountUser)
  const phone = resolvePendingPhone(accountUser)
  const displayName =
    accountUser?.fullName?.trim() ||
    accountUser?.firstName?.trim() ||
    'Пользователь'

  const courierMembership = useMemo(() => {
    if (activeMembership?.organizationType === 'courier_service' && activeMembership?.role === 'courier') {
      return activeMembership
    }
    return (deliveryMe?.memberships || []).find(
      (m) =>
        m.organizationType === 'courier_service' &&
        m.role === 'courier' &&
        m.accessKind !== 'service_scope',
    )
  }, [activeMembership, deliveryMe])

  const serviceName = courierMembership?.organizationName?.trim() || 'Служба доставки'
  const courierPublicId = courierMembership?.memberPublicId
  const memberStatus = courierMembership?.status
  const statusLabel = isBlockedCourier
    ? 'Заблокирован'
    : isPendingCourier
      ? 'Ожидание активации'
      : labelForStatus(memberStatus)

  useEffect(() => {
    const memberId = courierMembership?.memberId
    if (!memberId || isPendingCourier) {
      setCourierStats({ balance: 0, completedOrdersCount: 0 })
      return
    }
    let cancelled = false
    getCourier(memberId)
      .then((dto) => {
        if (cancelled) return
        setCourierStats({
          balance: dto?.balance ?? 0,
          completedOrdersCount: dto?.completedOrdersCount ?? 0,
        })
      })
      .catch(() => {
        if (!cancelled) {
          setCourierStats({ balance: 0, completedOrdersCount: 0 })
        }
      })
    return () => {
      cancelled = true
    }
  }, [courierMembership?.memberId, isPendingCourier, balanceReloadKey])

  const onRefreshStatus = async () => {
    setRefreshing(true)
    try {
      await refreshDeliveryStatus()
    } finally {
      setRefreshing(false)
    }
  }

  const memberId = courierMembership?.memberId
  const loadPartnerProgram = useCallback(
    () => fetchCourierPartnerProgram(memberId),
    [memberId],
  )

  const onBalanceChange = useCallback(() => {
    setBalanceReloadKey((k) => k + 1)
  }, [])

  return (
    <div className="role-profile-page">
      <header className="role-profile-page__header">
        <h1 className="role-profile-page__title">Профиль</h1>
        <p className="role-profile-page__subtitle">Ваши данные и информация о работе</p>
      </header>

      <section className="card role-profile-hero role-profile-hero--courier">
        <h2 className="role-profile-hero__name">{displayName}</h2>
        <div className="role-profile-hero__badges">
          <span className="role-profile-badge role-profile-badge--role">Курьер</span>
          <span
            className={
              isBlockedCourier
                ? 'role-profile-badge role-profile-badge--blocked'
                : isPendingCourier
                  ? 'role-profile-badge role-profile-badge--pending'
                  : statusBadgeClass(memberStatus)
            }
          >
            {statusLabel}
          </span>
        </div>
      </section>

      {isBlockedCourier ? (
        <section className="card role-profile-pending">
          <h2 className="role-profile-section__title">Доступ заблокирован</h2>
          <p className="role-profile-pending__text">
            Ваш доступ к заказам временно заблокирован. Обратитесь к администратору службы
            доставки.
          </p>
          <button
            type="button"
            className="btn"
            disabled={refreshing}
            onClick={onRefreshStatus}
          >
            {refreshing ? 'Проверяем…' : 'Обновить статус'}
          </button>
        </section>
      ) : isPendingCourier ? (
        <section className="card role-profile-pending">
          <p className="role-profile-pending__text">
            Ожидает активации администратором службы доставки. Сообщите ID или телефон
            администратору, затем обновите статус.
          </p>
          <button
            type="button"
            className="btn"
            disabled={refreshing}
            onClick={onRefreshStatus}
          >
            {refreshing ? 'Обновление…' : 'Обновить статус'}
          </button>
        </section>
      ) : (
        <>
          <CourierBalanceSection
            memberId={memberId}
            disabled={!memberId}
            onBalanceChange={onBalanceChange}
          />

          <section className="card role-profile-section">
            <h2 className="role-profile-section__title">Данные курьера</h2>
            <dl className="role-profile-dl">
              {courierPublicId != null && (
                <div className="role-profile-dl__row">
                  <dt>ID курьера</dt>
                  <dd>{courierPublicId}</dd>
                </div>
              )}
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
              <div className="role-profile-dl__row">
                <dt>Служба</dt>
                <dd>{serviceName}</dd>
              </div>
            </dl>
          </section>

          <section className="role-profile-stats role-profile-stats--one">
            <article className="card role-profile-stat">
              <p className="role-profile-stat__label">Выполнено заказов</p>
              <p className="role-profile-stat__value">{courierStats.completedOrdersCount ?? 0}</p>
            </article>
          </section>

          <PartnerProgramSection
            loadProgram={loadPartnerProgram}
            disabled={!memberId}
            disabledMessage="Раздел доступен активным курьерам службы доставки."
          />
        </>
      )}

      {isPendingCourier && (userId != null || phone) && (
        <section className="card role-profile-section">
          <h2 className="role-profile-section__title">Данные для активации</h2>
          <dl className="role-profile-dl">
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
