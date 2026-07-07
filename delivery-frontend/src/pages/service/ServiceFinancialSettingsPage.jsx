import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchCourierServiceFinancialSettings,
  upsertCourierServiceFinancialSettings,
} from '../../api/deliveryService.js'
import CopySnackbar from '../../components/CopySnackbar.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { findServiceStaffMembership } from '../../hooks/useActiveOrg.js'
import { mapDeliveryApiError } from '../../utils/mapApiError.js'

function formatMoney(value) {
  const n = value != null ? Number(value) : 0
  if (Number.isNaN(n)) return '0 ₽'
  return `${n.toLocaleString('ru-RU')} ₽`
}

export default function ServiceFinancialSettingsPage() {
  const { deliveryMe } = useAuth()
  const serviceMembership = useMemo(
    () => findServiceStaffMembership(deliveryMe?.memberships),
    [deliveryMe],
  )
  const courierServiceId = serviceMembership?.organizationId

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [snackbar, setSnackbar] = useState(null)
  const [enabled, setEnabled] = useState(false)
  const [feeType, setFeeType] = useState('PERCENT')
  const [feeValue, setFeeValue] = useState('0')

  const reload = useCallback(async () => {
    if (!courierServiceId) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const data = await fetchCourierServiceFinancialSettings(courierServiceId)
      setEnabled(Boolean(data?.platformFeeEnabled))
      setFeeType(data?.platformFeeType || 'PERCENT')
      setFeeValue(data?.platformFeeValue != null ? String(data.platformFeeValue) : '0')
    } catch (e) {
      setSnackbar({ message: mapDeliveryApiError(e), variant: 'error' })
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const previewFor500 = useMemo(() => {
    const price = 500
    const value = Number(feeValue) || 0
    if (!enabled) return 0
    if (feeType === 'FIXED') return value
    return Math.round((price * value) / 100 * 100) / 100
  }, [enabled, feeType, feeValue])

  const onSave = async () => {
    if (!courierServiceId) return
    setSaving(true)
    try {
      await upsertCourierServiceFinancialSettings(courierServiceId, {
        platformFeeEnabled: enabled,
        platformFeeType: feeType,
        platformFeeValue: Number(feeValue) || 0,
      })
      setSnackbar({ message: 'Настройки сохранены', variant: 'success' })
      await reload()
    } catch (e) {
      setSnackbar({ message: mapDeliveryApiError(e), variant: 'error' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="service-profile-page">
      <CopySnackbar
        message={snackbar?.message}
        variant={snackbar?.variant}
        onDismiss={() => setSnackbar(null)}
      />

      <header className="service-profile-page__header">
        <Link to="/service/profile" className="order-detail-page__back">
          ← Профиль службы
        </Link>
        <h1 className="service-profile-page__title">Финансовые настройки</h1>
      </header>

      {loading ? (
        <p className="muted">Загрузка…</p>
      ) : (
        <section className="card partner-program-block">
          <h2 className="role-profile-section__title">Вознаграждение платформы</h2>
          <p className="partner-program-block__lead muted">
            Удерживается из стоимости доставки при завершении заказа. Партнёрское вознаграждение
            рассчитывается отдельно по правилам партнёрской программы.
          </p>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Включено</span>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
          </label>

          <div className="partner-program-field">
            <span className="partner-program-field__label">Тип</span>
            <select
              className="partner-program-field__control"
              value={feeType}
              onChange={(e) => setFeeType(e.target.value)}
              disabled={!enabled}
            >
              <option value="PERCENT">Процент</option>
              <option value="FIXED">Фиксированная сумма</option>
            </select>
          </div>

          <div className="partner-program-field">
            <label className="partner-program-field__label" htmlFor="platform-fee-value">
              {feeType === 'FIXED' ? 'Сумма, ₽' : 'Процент, %'}
            </label>
            <input
              id="platform-fee-value"
              type="number"
              min="0"
              step={feeType === 'FIXED' ? '0.01' : '0.1'}
              className="partner-program-field__control"
              value={feeValue}
              onChange={(e) => setFeeValue(e.target.value)}
              disabled={!enabled}
            />
          </div>

          <p className="muted">
            Пример для доставки 500 ₽: удержание платформы — {formatMoney(previewFor500)}.
          </p>

          <button type="button" className="btn" disabled={saving} onClick={onSave}>
            {saving ? 'Сохранение…' : 'Сохранить'}
          </button>
        </section>
      )}
    </div>
  )
}
