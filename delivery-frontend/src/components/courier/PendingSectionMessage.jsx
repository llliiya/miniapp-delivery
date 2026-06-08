export default function PendingSectionMessage({ title, message }) {
  return (
    <div className="pending-activation">
      <section className="card pending-activation__placeholder">
        <h2 className="pending-activation__page-title">{title}</h2>
        <p className="pending-activation__text muted">{message}</p>
      </section>
    </div>
  )
}
