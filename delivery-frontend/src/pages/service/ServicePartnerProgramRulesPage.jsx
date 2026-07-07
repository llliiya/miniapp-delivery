import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchPartnerProgramRules,
  upsertPartnerProgramRule,
} from '../../api/deliveryService.js'
import CopySnackbar from '../../components/CopySnackbar.jsx'
import PartnerProgramRuleCard from '../../components/partner/PartnerProgramRuleCard.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { findServiceStaffMembership } from '../../hooks/useActiveOrg.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'
import {
  resolveCalculationBase,
  resolvePayoutMethods,
  ruleAccrualMode,
} from '../../utils/partnerProgramAdminUi.js'
import ServicePartnerProgramPayoutsTab from './ServicePartnerProgramPayoutsTab.jsx'
import ServicePartnerProgramReferralsTab from './ServicePartnerProgramReferralsTab.jsx'

const TABS = [
  { id: 'rules', label: 'Настройки' },
  { id: 'payouts', label: 'Заявки на выплату' },
  { id: 'referrals', label: 'Партнёры и приглашения' },
]

const RELATIONSHIPS = [
  { referrerType: 'COURIER', inviteeType: 'COURIER', label: 'Курьер → Курьер' },
  { referrerType: 'COURIER', inviteeType: 'RESTAURANT', label: 'Курьер → Объект' },
  { referrerType: 'RESTAURANT', inviteeType: 'COURIER', label: 'Объект → Курьер' },
  { referrerType: 'RESTAURANT', inviteeType: 'RESTAURANT', label: 'Объект → Объект' },
]

function emptyForm(rel) {
  return {
    referrerType: rel.referrerType,
    inviteeType: rel.inviteeType,
    enabled: false,
    accrualMode: 'PERCENT',
    percentValue: '',
    fixedAmount: '',
    durationMonths: '3',
    effectiveFrom: new Date().toISOString().slice(0, 10),
    minPayoutAmount: '500',
    payoutMethods: resolvePayoutMethods(rel.referrerType),
    payoutDayOfMonth: '1',
  }
}

function ruleToForm(rule, rel) {
  if (!rule) return emptyForm(rel)
  const accrualMode = ruleAccrualMode(rule)
  return {
    referrerType: rule.referrerType,
    inviteeType: rule.inviteeType,
    enabled: rule.enabled,
    accrualMode,
    percentValue: rule.percentValue ?? '',
    fixedAmount: rule.fixedAmount ?? '',
    durationMonths: rule.durationMonths ?? '',
    effectiveFrom: rule.effectiveFrom ?? new Date().toISOString().slice(0, 10),
    minPayoutAmount: rule.minPayoutAmount ?? '0',
    payoutMethods: resolvePayoutMethods(rule.referrerType ?? rel.referrerType, rule.payoutMethods),
    payoutDayOfMonth: String(rule.payoutRestrictions?.payoutDayOfMonth ?? '1'),
  }
}

export default function ServicePartnerProgramRulesPage() {
  const { activeMembership, deliveryMe } = useAuth()
  const staffMembership = useMemo(
    () => findServiceStaffMembership(activeMembership, deliveryMe?.memberships),
    [activeMembership, deliveryMe],
  )
  const courierServiceId = staffMembership?.organizationId ?? null
  const [activeTab, setActiveTab] = useState('rules')
  const [forms, setForms] = useState({})
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [savingKey, setSavingKey] = useState(null)
  const [cardErrors, setCardErrors] = useState({})
  const [toast, setToast] = useState(null)

  const serviceEnabled = useMemo(
    () => RELATIONSHIPS.some((rel) => forms[`${rel.referrerType}_${rel.inviteeType}`]?.enabled),
    [forms],
  )

  const reload = useCallback(async () => {
    if (!courierServiceId) return
    setLoading(true)
    setLoadError('')
    try {
      const data = await fetchPartnerProgramRules(courierServiceId)
      const nextForms = {}
      for (const rel of RELATIONSHIPS) {
        const existing = (data || []).find(
          (r) => r.referrerType === rel.referrerType && r.inviteeType === rel.inviteeType,
        )
        nextForms[`${rel.referrerType}_${rel.inviteeType}`] = ruleToForm(existing, rel)
      }
      setForms(nextForms)
    } catch (e) {
      setLoadError(mapDeliveryApiError(e, 'Не удалось загрузить правила'))
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const updateField = (key, field, value) => {
    setForms((prev) => ({
      ...prev,
      [key]: { ...prev[key], [field]: value },
    }))
    setCardErrors((prev) => ({ ...prev, [key]: '' }))
  }

  const togglePayoutMethod = (key, method) => {
    setForms((prev) => {
      const rel = RELATIONSHIPS.find((item) => `${item.referrerType}_${item.inviteeType}` === key)
      if (rel?.referrerType === 'RESTAURANT') {
        return {
          ...prev,
          [key]: { ...prev[key], payoutMethods: resolvePayoutMethods(rel.referrerType) },
        }
      }
      const current = prev[key]?.payoutMethods || []
      const next = current.includes(method)
        ? current.filter((m) => m !== method)
        : [...current, method]
      return { ...prev, [key]: { ...prev[key], payoutMethods: next } }
    })
    setCardErrors((prev) => ({ ...prev, [key]: '' }))
  }

  const onSave = async (rel) => {
    if (!courierServiceId) return
    const key = `${rel.referrerType}_${rel.inviteeType}`
    const form = forms[key]
    const payoutMethods = resolvePayoutMethods(rel.referrerType, form.payoutMethods)
    const calculationBase = resolveCalculationBase(rel.inviteeType, form.accrualMode)
    const calculationType = form.accrualMode === 'FIXED_PER_DELIVERY' ? 'FIXED' : 'PERCENT'
    setSavingKey(key)
    setCardErrors((prev) => ({ ...prev, [key]: '' }))
    try {
      await upsertPartnerProgramRule(courierServiceId, {
        referrerType: rel.referrerType,
        inviteeType: rel.inviteeType,
        enabled: form.enabled,
        calculationType,
        percentValue: form.accrualMode === 'PERCENT' && form.percentValue ? Number(form.percentValue) : null,
        fixedAmount:
          form.accrualMode === 'FIXED_PER_DELIVERY' && form.fixedAmount
            ? Number(form.fixedAmount)
            : null,
        calculationBase,
        durationMonths: form.durationMonths ? Number(form.durationMonths) : null,
        effectiveFrom: form.effectiveFrom,
        accrualConditions: { onlyCompleted: true },
        payoutRestrictions: { payoutDayOfMonth: Number(form.payoutDayOfMonth) || 1 },
        minPayoutAmount: form.minPayoutAmount ? Number(form.minPayoutAmount) : 0,
        payoutMethods,
      })
      setToast({ message: 'Настройки сохранены', variant: 'success' })
      await reload()
    } catch (e) {
      setCardErrors((prev) => ({
        ...prev,
        [key]: mapDeliveryApiError(e, 'Не удалось сохранить настройки'),
      }))
    } finally {
      setSavingKey(null)
    }
  }

  if (!courierServiceId) {
    return (
      <div className="card service-access-denied">
        <h2 className="service-access-denied__title">Нет доступа</h2>
        <p className="service-access-denied__text">
          Раздел партнёрской программы доступен только собственнику или менеджеру курьерской службы.
        </p>
      </div>
    )
  }

  return (
    <div className="partner-program-page">
      <header className="partner-program-page__header">
        <Link to="/service/profile" className="objects-page__back muted">
          ← К профилю службы
        </Link>
        <h1 className="partner-program-page__title">Партнёрская программа</h1>
        <p className="partner-program-page__desc muted">
          Управление правилами начислений, заявками на выплату и журналом приглашений.
        </p>
      </header>

      {!loading && !loadError && (
        <div
          className={`partner-program-status-banner${
            serviceEnabled ? ' partner-program-status-banner--enabled' : ' partner-program-status-banner--disabled'
          }`}
          role="status"
        >
          <p className="partner-program-status-banner__title">
            {serviceEnabled ? 'Партнёрская программа включена' : 'Партнёрская программа выключена'}
          </p>
          <p className="partner-program-status-banner__text muted">
            {serviceEnabled
              ? 'Курьеры и объекты видят партнёрский блок в профиле по включённым направлениям.'
              : 'Включите хотя бы одно направление приглашений, чтобы блок появился у курьеров и объектов.'}
          </p>
        </div>
      )}

      <div className="partner-program-tabs" role="tablist" aria-label="Разделы партнёрской программы">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={`partner-program-tabs__btn${
              activeTab === tab.id ? ' partner-program-tabs__btn--active' : ''
            }`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="partner-program-page__content">
        {activeTab === 'payouts' ? (
          <ServicePartnerProgramPayoutsTab />
        ) : activeTab === 'referrals' ? (
          <ServicePartnerProgramReferralsTab />
        ) : (
          <div className="partner-program-rules">
            <p className="partner-program-rules__lead muted">
              Настройте, кто получает партнёрское вознаграждение, за кого оно начисляется и как
              считается процент или фиксированная сумма.
            </p>

            {loading && (
              <div className="partner-program-state card">
                <p className="muted">Загрузка настроек…</p>
              </div>
            )}

            {!loading && loadError && (
              <div className="partner-program-state partner-program-state--error card">
                <p className="partner-program-state__error-text">{loadError}</p>
                <button type="button" className="btn btn-secondary" onClick={reload}>
                  Повторить
                </button>
              </div>
            )}

            {!loading && !loadError && (
              <div className="partner-program-rules__list">
                {RELATIONSHIPS.map((rel) => {
                  const key = `${rel.referrerType}_${rel.inviteeType}`
                  const form = forms[key]
                  if (!form) return null
                  const normalizedForm = {
                    ...form,
                    payoutMethods: resolvePayoutMethods(rel.referrerType, form.payoutMethods),
                  }
                  return (
                    <PartnerProgramRuleCard
                      key={key}
                      rel={rel}
                      form={normalizedForm}
                      saving={savingKey === key}
                      error={cardErrors[key]}
                      onFieldChange={(field, value) => updateField(key, field, value)}
                      onTogglePayoutMethod={(method) => togglePayoutMethod(key, method)}
                      onSave={() => onSave(rel)}
                    />
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>

      <CopySnackbar
        message={toast?.message}
        variant={toast?.variant}
        onDismiss={() => setToast(null)}
      />
    </div>
  )
}
