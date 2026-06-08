export default function PlaceholderPage({ title, description }) {
  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>{title}</h2>
      <p className="muted">{description}</p>
      <p className="muted">Этап 1 — заглушка. Логика появится на следующих этапах.</p>
    </div>
  )
}
