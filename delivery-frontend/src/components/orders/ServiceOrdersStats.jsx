export default function ServiceOrdersStats({ stats, loading }) {
  const items = [
    {
      key: 'waiting',
      label: 'Ожидают курьера',
      shortLabel: 'Ожидают',
      value: stats?.waiting,
      tone: 'waiting',
    },
    {
      key: 'inWork',
      label: 'В работе',
      shortLabel: 'В работе',
      value: stats?.inWork,
      tone: 'work',
    },
    {
      key: 'completedToday',
      label: 'Выполнено сегодня',
      shortLabel: 'Сегодня',
      value: stats?.completedToday,
      tone: 'done',
    },
    {
      key: 'cancelled',
      label: 'Отменено',
      shortLabel: 'Отменено',
      value: stats?.cancelled,
      tone: 'cancelled',
    },
  ]

  return (
    <section className="service-orders-stats" aria-label="Показатели службы">
      {items.map((item) => (
        <article
          key={item.key}
          className={`card service-orders-stats__card service-orders-stats__card--${item.tone}`}
        >
          <p className="service-orders-stats__value" aria-label={item.label}>
            {loading ? '—' : (item.value ?? 0)}
          </p>
          <p className="service-orders-stats__label">
            <span className="service-orders-stats__label-full">{item.label}</span>
            <span className="service-orders-stats__label-short">{item.shortLabel}</span>
          </p>
        </article>
      ))}
    </section>
  )
}
