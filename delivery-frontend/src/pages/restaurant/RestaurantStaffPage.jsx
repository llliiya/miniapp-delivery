import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  addOrganizationMember,
  listOrganizationMembers,
  patchOrganizationMember,
  removeOrganizationMember,
  resetOrganizationMemberAccess,
} from '../../api/deliveryService.js'
import ProvisioningCredentialsModal from '../../components/ProvisioningCredentialsModal.jsx'
import PhoneInput from '../../components/PhoneInput.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { useRestaurantId } from '../../hooks/useActiveOrg.js'
import { labelForRole, labelForStatus } from '../../utils/displayLabels.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'
import { resolvePendingUserId } from '../../utils/pendingCourier.js'
import { runLatestResetAccess } from '../../utils/runLatestResetAccess.js'

const ROLE_OPTIONS = [
  { value: 'manager', label: 'Менеджер' },
  { value: 'owner', label: 'Собственник' },
]

function statusBadgeClass(status) {
  if (status === 'active') return 'badge'
  if (status === 'blocked') return 'badge badge-warn'
  return 'badge'
}

function isSameUser(memberUserId, currentUserId) {
  if (memberUserId == null || currentUserId == null) return false
  return Number(memberUserId) === Number(currentUserId)
}

function StaffMemberCard({
  member,
  currentUserId,
  canManage,
  actionBusyUserId,
  onResetAccess,
  onSetStatus,
  onRemoveFromObject,
}) {
  const isSelf = isSameUser(member.userId, currentUserId)
  const showActions = canManage && !isSelf
  const busy = actionBusyUserId === member.userId

  return (
    <article
      className={`card restaurant-staff-card${member.status === 'blocked' ? ' card-inactive' : ''}`}
    >
      <div className="restaurant-staff-card__head">
        <div className="restaurant-staff-card__identity">
          <div className="restaurant-staff-card__title-row">
            <strong className="restaurant-staff-card__name">
              {member.displayName || `Сотрудник ${member.publicId ?? ''}`}
            </strong>
            {isSelf && <span className="badge restaurant-staff-card__you-badge">Это вы</span>}
          </div>
        </div>
        <span className={statusBadgeClass(member.status)}>{labelForStatus(member.status)}</span>
      </div>
      <p className="restaurant-staff-card__meta">Роль: {labelForRole(member.role)}</p>
      <p className="restaurant-staff-card__meta restaurant-staff-card__meta--last">
        ID пользователя: {member.userId}
      </p>
      {showActions && (
        <div className="restaurant-staff-card__actions">
          <button
            type="button"
            className="btn btn-secondary restaurant-staff-card__action"
            disabled={busy}
            onClick={() => onResetAccess(member)}
          >
            Сбросить доступ
          </button>
          {member.status === 'active' ? (
            <button
              type="button"
              className="btn btn-secondary restaurant-staff-card__action"
              disabled={busy}
              onClick={() => onSetStatus(member, 'blocked')}
            >
              Заблокировать
            </button>
          ) : (
            <button
              type="button"
              className="btn btn-secondary restaurant-staff-card__action"
              disabled={busy}
              onClick={() => onSetStatus(member, 'active')}
            >
              Разблокировать
            </button>
          )}
          {member.role !== 'owner' && (
            <button
              type="button"
              className="btn btn-danger restaurant-staff-card__action"
              disabled={busy}
              onClick={() => onRemoveFromObject(member)}
            >
              Удалить из объекта
            </button>
          )}
        </div>
      )}
    </article>
  )
}

export default function RestaurantStaffPage() {
  const restaurantId = useRestaurantId()
  const { activeMembership, accountUser, deliveryMe } = useAuth()
  const canManage = activeMembership?.role === 'owner'
  const currentUserId = resolvePendingUserId(deliveryMe, accountUser)

  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('manager')
  const [submitting, setSubmitting] = useState(false)
  const [credentials, setCredentials] = useState(null)
  const [credentialsTitle, setCredentialsTitle] = useState('Сотрудник добавлен')
  const [credentialsIntro, setCredentialsIntro] = useState('Передайте сотруднику данные для входа:')
  const [actionBusyUserId, setActionBusyUserId] = useState(null)
  const resetAccessSeqRef = useRef(0)

  const owners = useMemo(() => members.filter((m) => m.role === 'owner'), [members])
  const managers = useMemo(() => members.filter((m) => m.role === 'manager'), [members])

  const reload = useCallback(async () => {
    if (!restaurantId) return
    setLoading(true)
    setError('')
    try {
      const list = (await listOrganizationMembers(restaurantId)) || []
      setMembers(list.filter((m) => m.role === 'owner' || m.role === 'manager'))
    } catch (e) {
      setError(mapDeliveryApiError(e, 'Не удалось загрузить сотрудников'))
      setMembers([])
    } finally {
      setLoading(false)
    }
  }, [restaurantId])

  useEffect(() => {
    reload()
  }, [reload])

  const openAddForm = () => {
    setFullName('')
    setPhone('')
    setEmail('')
    setRole('manager')
    setShowForm(true)
  }

  const onAdd = async (e) => {
    e.preventDefault()
    const trimmedName = fullName.trim()
    const trimmedPhone = phone.trim()
    if (!trimmedName || !trimmedPhone) return
    setSubmitting(true)
    try {
      const result = await addOrganizationMember(restaurantId, {
        role,
        fullName: trimmedName,
        phone: trimmedPhone,
        email: email.trim() || undefined,
      })
      setCredentialsTitle('Сотрудник добавлен')
      setCredentialsIntro('Передайте сотруднику данные для входа:')
      setCredentials(result?.credentials || null)
      setShowForm(false)
      await reload()
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось добавить сотрудника'))
    } finally {
      setSubmitting(false)
    }
  }

  const onResetAccess = async (member) => {
    if (
      !window.confirm(
        'Сбросить доступ? Сотруднику будет выдан новый временный пароль для входа.',
      )
    ) {
      return
    }
    setCredentials(null)
    setActionBusyUserId(member.userId)
    setError('')
    try {
      const { applied, creds } = await runLatestResetAccess(resetAccessSeqRef, () =>
        resetOrganizationMemberAccess(restaurantId, member.userId),
      )
      if (!applied || !creds?.login || !creds?.temporaryPassword) return
      setCredentialsTitle('Доступ обновлен')
      setCredentialsIntro('Передайте сотруднику новые данные для входа:')
      setCredentials(creds)
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось сбросить доступ'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  const onSetStatus = async (member, nextStatus) => {
    setActionBusyUserId(member.userId)
    setError('')
    try {
      await patchOrganizationMember(restaurantId, member.userId, { status: nextStatus })
      await reload()
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось изменить статус'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  const onRemoveFromObject = async (member) => {
    if (
      !window.confirm(
        'Удалить сотрудника из объекта?\n\nСотрудник потеряет доступ к объекту, но его аккаунт сохранится.',
      )
    ) {
      return
    }
    setActionBusyUserId(member.userId)
    setError('')
    try {
      await removeOrganizationMember(restaurantId, member.userId)
      await reload()
    } catch (err) {
      setError(mapDeliveryApiError(err, 'Не удалось удалить сотрудника'))
    } finally {
      setActionBusyUserId(null)
    }
  }

  const cardProps = {
    currentUserId,
    canManage,
    actionBusyUserId,
    onResetAccess,
    onSetStatus,
    onRemoveFromObject,
  }

  if (!restaurantId) {
    return (
      <section className="card restaurant-staff-page__notice">
        <p className="muted">Выберите объект в профиле.</p>
      </section>
    )
  }

  return (
    <div className="restaurant-staff-page">
      <ProvisioningCredentialsModal
        key={
          credentials
            ? `${credentials.login}:${credentials.temporaryPassword}`
            : 'credentials-closed'
        }
        open={Boolean(credentials)}
        title={credentialsTitle}
        intro={credentialsIntro}
        login={credentials?.login}
        temporaryPassword={credentials?.temporaryPassword}
        firstLoginNote="При первом входе сотрудник должен будет сменить пароль. Данные показываются только один раз."
        hideDefaultLabel
        onClose={() => setCredentials(null)}
      />

      <header className="restaurant-staff-page__header">
        <div>
          <h1 className="restaurant-staff-page__title">Сотрудники</h1>
          <p className="restaurant-staff-page__subtitle">
            Добавляйте менеджеров, чтобы они могли создавать и контролировать заказы объекта.
          </p>
        </div>
        {canManage && !showForm && !loading && (
          <button type="button" className="btn restaurant-staff-page__add-btn" onClick={openAddForm}>
            Добавить менеджера
          </button>
        )}
      </header>

      {!canManage && (
        <section className="card restaurant-staff-page__readonly">
          <p className="muted" style={{ margin: 0 }}>
            Управление сотрудниками доступно только собственнику объекта.
          </p>
        </section>
      )}

      {error && (
        <section className="card restaurant-staff-page__error" role="alert">
          <p>{error}</p>
        </section>
      )}

      {showForm && canManage && (
        <form className="card restaurant-staff-form" onSubmit={onAdd}>
          <h2 className="restaurant-staff-form__title">Новый сотрудник</h2>
          <label className="restaurant-staff-form__label">
            ФИО
            <input
              className="input"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
            />
          </label>
          <label className="restaurant-staff-form__label">
            Телефон
            <PhoneInput className="input" value={phone} onChange={setPhone} />
          </label>
          <label className="restaurant-staff-form__label">
            Email
            <input
              type="email"
              className="input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="restaurant-staff-form__label">
            Роль
            <select className="input" value={role} onChange={(e) => setRole(e.target.value)}>
              {ROLE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <div className="restaurant-staff-form__actions">
            <button type="submit" className="btn" disabled={submitting}>
              {submitting ? 'Добавление…' : 'Добавить менеджера'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setShowForm(false)}
            >
              Отмена
            </button>
          </div>
        </form>
      )}

      {loading && (
        <section className="card restaurant-staff-page__loading">
          <p className="muted">Загрузка…</p>
        </section>
      )}

      {!loading && (
        <>
          <section className="restaurant-staff-section">
            <h2 className="restaurant-staff-section__title">Собственник объекта</h2>
            {owners.length === 0 ? (
              <div className="card restaurant-staff-section__placeholder muted">
                Собственник не найден
              </div>
            ) : (
              owners.map((m) => <StaffMemberCard key={m.id} member={m} {...cardProps} />)
            )}
          </section>

          <section className="restaurant-staff-section">
            <h2 className="restaurant-staff-section__title">Менеджеры</h2>
            {managers.length === 0 ? (
              <div className="card restaurant-staff-empty restaurant-staff-empty--managers">
                <h3 className="restaurant-staff-empty__title">Менеджеров пока нет</h3>
                <p className="restaurant-staff-empty__text">
                  Добавьте менеджера, чтобы он мог помогать с заказами объекта.
                </p>
                {canManage && (
                  <button
                    type="button"
                    className="btn restaurant-staff-empty__btn"
                    onClick={openAddForm}
                  >
                    Добавить менеджера
                  </button>
                )}
              </div>
            ) : (
              <div className="restaurant-staff-list">
                {managers.map((m) => (
                  <StaffMemberCard key={m.id} member={m} {...cardProps} />
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
