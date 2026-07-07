import {
  buildAccrualExample,
  fixedAmountFieldHelper,
  isFixedAccrualRule,
  PAYOUT_METHOD_OPTIONS,
  percentFieldHelper,
  RELATIONSHIP_COPY,
  relationshipKey,
  resolvePayoutMethods,
} from '../../utils/partnerProgramAdminUi.js'

function FieldHint({ children }) {
  if (!children) return null
  return <span className="partner-program-field__hint muted">{children}</span>
}

export default function PartnerProgramRuleCard({
  rel,
  form,
  saving = false,
  error = '',
  onFieldChange,
  onTogglePayoutMethod,
  onSave,
}) {
  if (!form) return null

  const key = relationshipKey(rel.referrerType, rel.inviteeType)
  const copy = RELATIONSHIP_COPY[key]
  const fieldsMuted = !form.enabled
  const isRestaurantReferrer = rel.referrerType === 'RESTAURANT'
  const isPercentMode = form.accrualMode === 'PERCENT'
  const showFixedField = isFixedAccrualRule(form)
  const payoutMethodOptions = isRestaurantReferrer ? [] : PAYOUT_METHOD_OPTIONS
  const selectedMethods = isRestaurantReferrer
    ? []
    : resolvePayoutMethods(rel.referrerType, form.payoutMethods)

  return (
    <article className="partner-program-rule-card card">
      <header className="partner-program-rule-card__header">
        <div className="partner-program-rule-card__heading">
          <h3 className="partner-program-rule-card__title">{rel.label}</h3>
          {copy && (
            <div className="partner-program-rule-card__summary">
              <p className="partner-program-rule-card__desc">{copy.description}</p>
              <dl className="partner-program-rule-card__summary-lines">
                <div className="partner-program-rule-card__summary-line">
                  <dt>Получает</dt>
                  <dd>{copy.receives}</dd>
                </div>
                <div className="partner-program-rule-card__summary-line">
                  <dt>За кого</dt>
                  <dd>{copy.forWhom}</dd>
                </div>
                <div className="partner-program-rule-card__summary-line">
                  <dt>Начисление</dt>
                  <dd>{copy.accrual}</dd>
                </div>
              </dl>
            </div>
          )}
        </div>
        <label className="partner-program-toggle">
          <input
            type="checkbox"
            className="partner-program-toggle__input"
            checked={form.enabled}
            onChange={(e) => onFieldChange('enabled', e.target.checked)}
          />
          <span className="partner-program-toggle__track" aria-hidden="true" />
          <span className="partner-program-toggle__text">
            {form.enabled ? 'Программа включена' : 'Программа выключена'}
          </span>
        </label>
      </header>

      <div
        className={`partner-program-rule-card__body${
          fieldsMuted ? ' partner-program-rule-card__body--muted' : ''
        }`}
      >
        <aside className="partner-program-rule-card__example" aria-label="Пример расчёта">
          <strong className="partner-program-rule-card__example-title">Пример расчёта</strong>
          <p className="partner-program-rule-card__example-text muted">
            {buildAccrualExample(form, rel.inviteeType)}
          </p>
        </aside>

        <div className="partner-program-rule-card__grid">
          <label className="partner-program-field">
            <span className="partner-program-field__label">Как начислять вознаграждение?</span>
            <select
              className="input partner-program-field__control"
              value={form.accrualMode}
              onChange={(e) => onFieldChange('accrualMode', e.target.value)}
            >
              <option value="PERCENT">Процент</option>
              <option value="FIXED_PER_DELIVERY">Фиксированная сумма за доставку</option>
            </select>
          </label>

          {isPercentMode && (
            <label className="partner-program-field">
              <span className="partner-program-field__label">Процент начисления</span>
              <div className="partner-program-field__suffix-wrap">
                <input
                  type="number"
                  className="input partner-program-field__control"
                  min="0"
                  step="0.01"
                  placeholder="0"
                  value={form.percentValue}
                  onChange={(e) => onFieldChange('percentValue', e.target.value)}
                />
                <span className="partner-program-field__suffix">%</span>
              </div>
              <FieldHint>{percentFieldHelper(rel.inviteeType)}</FieldHint>
            </label>
          )}

          {showFixedField && (
            <label className="partner-program-field">
              <span className="partner-program-field__label">Сумма за одну завершённую доставку</span>
              <div className="partner-program-field__suffix-wrap">
                <input
                  type="number"
                  className="input partner-program-field__control"
                  min="0"
                  step="0.01"
                  placeholder="0"
                  value={form.fixedAmount}
                  onChange={(e) => onFieldChange('fixedAmount', e.target.value)}
                />
                <span className="partner-program-field__suffix">₽</span>
              </div>
              <FieldHint>{fixedAmountFieldHelper()}</FieldHint>
            </label>
          )}

          <label className="partner-program-field">
            <span className="partner-program-field__label">Срок действия, месяцев</span>
            <input
              type="number"
              className="input partner-program-field__control"
              min="1"
              step="1"
              placeholder="3"
              value={form.durationMonths}
              onChange={(e) => onFieldChange('durationMonths', e.target.value)}
            />
            <FieldHint>
              Сколько месяцев после одобрения приглашённого участника будут идти начисления.
            </FieldHint>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Дата старта</span>
            <input
              type="date"
              className="input partner-program-field__control"
              value={form.effectiveFrom}
              onChange={(e) => onFieldChange('effectiveFrom', e.target.value)}
            />
            <FieldHint>
              С какой даты правило начинает действовать для новых начислений.
            </FieldHint>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">Минимальная сумма выплаты</span>
            <div className="partner-program-field__suffix-wrap">
              <input
                type="number"
                className="input partner-program-field__control"
                min="0"
                step="0.01"
                placeholder="500"
                value={form.minPayoutAmount}
                onChange={(e) => onFieldChange('minPayoutAmount', e.target.value)}
              />
              <span className="partner-program-field__suffix">₽</span>
            </div>
            <FieldHint>
              Минимальная сумма партнёрского баланса, с которой можно запросить выплату.
            </FieldHint>
          </label>

          <label className="partner-program-field">
            <span className="partner-program-field__label">День выплаты в месяце</span>
            <input
              type="number"
              className="input partner-program-field__control"
              min="1"
              max="28"
              step="1"
              placeholder="1"
              value={form.payoutDayOfMonth}
              onChange={(e) => onFieldChange('payoutDayOfMonth', e.target.value)}
            />
            <FieldHint>
              День месяца, когда можно запросить выплату. Значение от 1 до 28. Само ограничение —
              не чаще одного раза в календарный месяц.
            </FieldHint>
          </label>
        </div>

        {isRestaurantReferrer ? (
          <div className="partner-program-payout-methods">
            <p className="partner-program-payout-methods__legend">Способ выплаты</p>
            <p className="partner-program-payout-method-static">Способ выплаты: банковский перевод</p>
          </div>
        ) : (
          <fieldset className="partner-program-payout-methods">
            <legend className="partner-program-payout-methods__legend">Способы выплаты</legend>
            <div className="partner-program-payout-methods__grid">
              {payoutMethodOptions.map((method) => {
                const checked = selectedMethods.includes(method.value)
                return (
                  <label
                    key={method.value}
                    className={`partner-program-check-card${
                      checked ? ' partner-program-check-card--checked' : ''
                    }`}
                  >
                    <input
                      type="checkbox"
                      className="partner-program-check-card__input"
                      checked={checked}
                      onChange={() => onTogglePayoutMethod(method.value)}
                    />
                    <span className="partner-program-check-card__title">{method.title}</span>
                    <span className="partner-program-check-card__hint muted">{method.hint}</span>
                  </label>
                )
              })}
            </div>
          </fieldset>
        )}
      </div>

      {error && (
        <p className="partner-program-rule-card__error" role="alert">
          {error}
        </p>
      )}

      <footer className="partner-program-rule-card__footer">
        <button
          type="button"
          className="btn partner-program-rule-card__save"
          disabled={saving}
          onClick={onSave}
        >
          {saving ? 'Сохранение…' : 'Сохранить'}
        </button>
      </footer>
    </article>
  )
}
