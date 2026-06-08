import { useId } from 'react'

const QUICK_FILTERS = [
  { key: '', label: 'Все' },
  { key: 'waiting_for_courier', label: 'Ожидают' },
  { key: 'in_work', label: 'В работе' },
  { key: 'completed', label: 'Выполнены' },
]

function formatFilterSummary({
  statusFilter,
  restaurantFilter,
  restaurantName,
  dateFrom,
  dateTo,
}) {
  const parts = []
  const statusLabel = QUICK_FILTERS.find((f) => f.key === statusFilter)?.label || 'Все'
  parts.push(statusLabel)
  if (restaurantFilter && restaurantName) {
    parts.push(restaurantName)
  } else if (restaurantFilter) {
    parts.push('Объект выбран')
  }
  if (dateFrom || dateTo) {
    const from = dateFrom ? dateFrom.split('-').reverse().join('.') : '…'
    const to = dateTo ? dateTo.split('-').reverse().join('.') : '…'
    parts.push(`${from} – ${to}`)
  }
  return parts.join(' · ')
}

export default function ServiceOrdersFilters({
  open,
  onToggle,
  statusFilter,
  onStatusFilter,
  filteredRestaurants,
  restaurantFilter,
  onRestaurantFilter,
  objectSearch,
  onObjectSearch,
  showObjectSearch,
  dateFrom,
  dateTo,
  onDateFrom,
  onDateTo,
  restaurantNameById,
}) {
  const panelId = useId()
  const summary = formatFilterSummary({
    statusFilter,
    restaurantFilter,
    restaurantName: restaurantNameById[restaurantFilter],
    dateFrom,
    dateTo,
  })
  const hasExtraFilters = Boolean(restaurantFilter || dateFrom || dateTo)

  return (
    <section className="card service-orders-filters">
      <button
        type="button"
        className="service-orders-filters__toggle"
        onClick={onToggle}
        aria-expanded={open}
        aria-controls={panelId}
      >
        <span className="service-orders-filters__toggle-title">Фильтры</span>
        {hasExtraFilters && !open && (
          <span className="service-orders-filters__toggle-badge" aria-hidden>
            •
          </span>
        )}
        <span
          className={`service-orders-filters__chevron${open ? ' service-orders-filters__chevron--open' : ''}`}
          aria-hidden
        />
      </button>
      {!open && (
        <p className="service-orders-filters__summary muted">{summary}</p>
      )}
      <div
        id={panelId}
        className={
          open
            ? 'service-orders-filters__panel'
            : 'service-orders-filters__panel service-orders-filters__panel--collapsed'
        }
        hidden={!open}
      >
        <div className="service-orders-chips" role="group" aria-label="Быстрый фильтр по статусу">
          {QUICK_FILTERS.map((f) => (
            <button
              key={f.key || 'all'}
              type="button"
              className={
                statusFilter === f.key
                  ? 'service-orders-chips__btn service-orders-chips__btn--active'
                  : 'service-orders-chips__btn'
              }
              onClick={() => onStatusFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="service-orders-filters__block">
          <span className="service-orders-filters__label">Объект</span>
          {showObjectSearch && (
            <input
              type="search"
              className="input service-orders-filters__search"
              placeholder="Поиск объекта…"
              value={objectSearch}
              onChange={(e) => onObjectSearch(e.target.value)}
              aria-label="Поиск объекта"
            />
          )}
          <select
            className="input service-orders-filters__select"
            value={restaurantFilter}
            onChange={(e) => onRestaurantFilter(e.target.value)}
            aria-label="Фильтр по объекту"
          >
            <option value="">Все объекты</option>
            {filteredRestaurants.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>

        <div className="service-orders-filters__block">
          <span className="service-orders-filters__label">Период</span>
          <div className="service-orders-period">
            <label className="service-orders-period__field">
              <span className="service-orders-period__caption">От</span>
              <input
                type="date"
                className="input"
                value={dateFrom}
                onChange={(e) => onDateFrom(e.target.value)}
              />
            </label>
            <label className="service-orders-period__field">
              <span className="service-orders-period__caption">До</span>
              <input
                type="date"
                className="input"
                value={dateTo}
                onChange={(e) => onDateTo(e.target.value)}
              />
            </label>
          </div>
        </div>
      </div>
    </section>
  )
}
