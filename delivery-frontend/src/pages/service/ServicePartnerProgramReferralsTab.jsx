import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchPartnerReferrals } from '../../api/deliveryService.js'
import EmptyState from '../../components/EmptyState.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import {
  formatDateTime,
  formatMoney,
  participantTypeLabel,
  referralStatusBadgeClass,
  referralStatusLabel,
} from '../../utils/partnerProgramAdminUi.js'

const RELATIONSHIP_OPTIONS = [
  { value: '', label: 'Все связки' },
  { value: 'COURIER:COURIER', label: 'Курьер → Курьер' },
  { value: 'COURIER:RESTAURANT', label: 'Курьер → Объект' },
  { value: 'RESTAURANT:COURIER', label: 'Объект → Курьер' },
  { value: 'RESTAURANT:RESTAURANT', label: 'Объект → Объект' },
]

const PARTICIPANT_TYPE_OPTIONS = [
  { value: '', label: 'Все' },
  { value: 'COURIER', label: 'Курьер' },
  { value: 'RESTAURANT', label: 'Объект' },
]

const STATUS_OPTIONS = [
  { value: '', label: 'Все статусы' },
  { value: 'ACTIVE', label: 'Активен' },
  { value: 'EXPIRED', label: 'Истёк срок начислений' },
  { value: 'RULE_DISABLED', label: 'Правило выключено' },
  { value: 'INVITEE_INACTIVE', label: 'Приглашённый неактивен' },
]

function relationshipKey(item) {
  return `${item.referrerType}:${item.inviteeType}`
}

function matchesSearch(item, query) {
  if (!query) return true
  const q = query.trim().toLowerCase()
  const haystack = [
    item.referrerName,
    item.inviteeName,
    item.inviteePhone,
    item.relationshipLabel,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return haystack.includes(q)
}

function ReferralRowCard({ item }) {
  return (
    <article className="partner-program-referral-card card">
      <div className="partner-program-referral-card__head">
        <span className="partner-program-badge partner-program-badge--type">
          {item.relationshipLabel}
        </span>
        <span className={referralStatusBadgeClass(item.status)}>
          {referralStatusLabel(item.status)}
        </span>
      </div>

      <div className="partner-program-referral-card__party">
        <p className="partner-program-referral-card__party-label muted">Пригласивший</p>
        <p className="partner-program-referral-card__party-type muted">
          {participantTypeLabel(item.referrerType)}
        </p>
        <strong>{item.referrerName || '—'}</strong>
      </div>

      <div className="partner-program-referral-card__party">
        <p className="partner-program-referral-card__party-label muted">Приглашённый</p>
        <p className="partner-program-referral-card__party-type muted">
          {participantTypeLabel(item.inviteeType)}
        </p>
        <strong>{item.inviteeName || '—'}</strong>
        {item.inviteePhone && <p className="muted">{item.inviteePhone}</p>}
      </div>

      <dl className="partner-program-referral-card__meta">
        <div>
          <dt className="muted">Создано</dt>
          <dd>{formatDateTime(item.createdAt)}</dd>
        </div>
        <div>
          <dt className="muted">Одобрено</dt>
          <dd>{formatDateTime(item.connectedAt)}</dd>
        </div>
        <div>
          <dt className="muted">Начисления</dt>
          <dd>{item.accrualCount ?? 0}</dd>
        </div>
        <div>
          <dt className="muted">Последнее</dt>
          <dd>{formatDateTime(item.lastAccrualAt)}</dd>
        </div>
      </dl>

      <div className="partner-program-referral-card__amounts">
        <div>
          <span className="muted">Начислено</span>
          <strong>{formatMoney(item.accruedAmount)}</strong>
        </div>
        <div>
          <span className="muted">Отменено</span>
          <strong>{formatMoney(item.reversedAmount)}</strong>
        </div>
        <div>
          <span className="muted">Итого</span>
          <strong>{formatMoney(item.netAmount)}</strong>
        </div>
      </div>
    </article>
  )
}

export default function ServicePartnerProgramReferralsTab() {
  const courierServiceId = useCourierServiceId()
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [relationshipFilter, setRelationshipFilter] = useState('')
  const [referrerTypeFilter, setReferrerTypeFilter] = useState('')
  const [inviteeTypeFilter, setInviteeTypeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [createdFrom, setCreatedFrom] = useState('')
  const [createdTo, setCreatedTo] = useState('')
  const [search, setSearch] = useState('')

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setItems([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      setItems((await fetchPartnerReferrals(courierServiceId)) || [])
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить приглашения')
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (relationshipFilter && relationshipKey(item) !== relationshipFilter) return false
      if (referrerTypeFilter && item.referrerType !== referrerTypeFilter) return false
      if (inviteeTypeFilter && item.inviteeType !== inviteeTypeFilter) return false
      if (statusFilter && item.status !== statusFilter) return false
      if (createdFrom) {
        const from = new Date(`${createdFrom}T00:00:00`)
        if (new Date(item.createdAt) < from) return false
      }
      if (createdTo) {
        const to = new Date(`${createdTo}T23:59:59`)
        if (new Date(item.createdAt) > to) return false
      }
      return matchesSearch(item, search)
    })
  }, [
    items,
    relationshipFilter,
    referrerTypeFilter,
    inviteeTypeFilter,
    statusFilter,
    createdFrom,
    createdTo,
    search,
  ])

  if (!courierServiceId) {
    return (
      <div className="partner-program-state card">
        <p className="muted">Служба доставки не выбрана.</p>
      </div>
    )
  }

  return (
    <div className="partner-program-referrals-admin">
      <div className="partner-program-referrals-admin__toolbar">
        <button type="button" className="btn btn-secondary" onClick={reload} disabled={loading}>
          {loading ? 'Обновление…' : 'Обновить'}
        </button>
      </div>

      <section className="card partner-program-filters">
        <h3 className="partner-program-filters__title">Фильтры</h3>
        <div className="partner-program-filters__grid">
          <label className="partner-program-field">
            <span className="partner-program-field__label">Связка</span>
            <select
              className="input partner-program-field__control"
              value={relationshipFilter}
              onChange={(e) => setRelationshipFilter(e.target.value)}
            >
              {RELATIONSHIP_OPTIONS.map((option) => (
                <option key={option.value || 'all'} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Тип пригласившего</span>
            <select
              className="input partner-program-field__control"
              value={referrerTypeFilter}
              onChange={(e) => setReferrerTypeFilter(e.target.value)}
            >
              <option value="">Все</option>
              {PARTICIPANT_TYPE_OPTIONS.filter((o) => o.value).map((option) => (
                <option key={`ref-${option.value}`} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Тип приглашённого</span>
            <select
              className="input partner-program-field__control"
              value={inviteeTypeFilter}
              onChange={(e) => setInviteeTypeFilter(e.target.value)}
            >
              <option value="">Все</option>
              {PARTICIPANT_TYPE_OPTIONS.filter((o) => o.value).map((option) => (
                <option key={`inv-${option.value}`} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Статус</span>
            <select
              className="input partner-program-field__control"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.value || 'all-status'} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Создано с</span>
            <input
              type="date"
              className="input partner-program-field__control"
              value={createdFrom}
              onChange={(e) => setCreatedFrom(e.target.value)}
            />
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Создано по</span>
            <input
              type="date"
              className="input partner-program-field__control"
              value={createdTo}
              onChange={(e) => setCreatedTo(e.target.value)}
            />
          </label>

          <label className="partner-program-field partner-program-field--wide">
            <span className="partner-program-field__label">Поиск</span>
            <input
              type="search"
              className="input partner-program-field__control"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Имя, телефон или связка"
            />
          </label>
        </div>
      </section>

      {loading && (
        <div className="partner-program-state card">
          <p className="muted">Загрузка приглашений…</p>
        </div>
      )}

      {error && (
        <div className="partner-program-state partner-program-state--error card">
          <p className="partner-program-state__error-text">{error}</p>
          <button type="button" className="btn btn-secondary" onClick={reload}>
            Повторить
          </button>
        </div>
      )}

      {!loading && !error && !items.length && (
        <EmptyState title="Приглашений пока нет" />
      )}

      {!loading && !error && items.length > 0 && !filteredItems.length && (
        <div className="partner-program-state card">
          <p className="muted">По выбранным фильтрам ничего не найдено.</p>
        </div>
      )}

      {!loading && !error && filteredItems.length > 0 && (
        <>
          <div className="partner-program-referrals-admin__cards">
            {filteredItems.map((item) => (
              <ReferralRowCard key={item.id} item={item} />
            ))}
          </div>

          <div className="partner-program-referrals-admin__table-wrap">
            <table className="partner-program-referrals-admin__table">
              <thead>
                <tr>
                  <th>Связка</th>
                  <th>Пригласивший</th>
                  <th>Приглашённый</th>
                  <th>Создано</th>
                  <th>Одобрено</th>
                  <th>Статус</th>
                  <th>Начисления</th>
                  <th>Суммы</th>
                  <th>Последнее начисление</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((item) => (
                  <tr key={item.id}>
                    <td>{item.relationshipLabel}</td>
                    <td>
                      <div className="muted">{participantTypeLabel(item.referrerType)}</div>
                      <strong>{item.referrerName || '—'}</strong>
                    </td>
                    <td>
                      <div className="muted">{participantTypeLabel(item.inviteeType)}</div>
                      <strong>{item.inviteeName || '—'}</strong>
                      {item.inviteePhone && <div className="muted">{item.inviteePhone}</div>}
                    </td>
                    <td>{formatDateTime(item.createdAt)}</td>
                    <td>{formatDateTime(item.connectedAt)}</td>
                    <td>
                      <span className={referralStatusBadgeClass(item.status)}>
                        {referralStatusLabel(item.status)}
                      </span>
                    </td>
                    <td>{item.accrualCount ?? 0}</td>
                    <td>
                      <div>Начислено: {formatMoney(item.accruedAmount)}</div>
                      <div>Отменено: {formatMoney(item.reversedAmount)}</div>
                      <div><strong>Итого: {formatMoney(item.netAmount)}</strong></div>
                    </td>
                    <td>{formatDateTime(item.lastAccrualAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
