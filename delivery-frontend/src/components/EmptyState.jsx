export default function EmptyState({ icon, title, description, children }) {
  return (
    <section className="empty-state card">
      {icon}
      <h3 className="empty-state__title">{title}</h3>
      {description && <p className="empty-state__text">{description}</p>}
      {children && <div className="empty-state__actions">{children}</div>}
    </section>
  )
}

export function EmptyStateIcon({ children }) {
  return (
    <div className="empty-state__icon-wrap" aria-hidden="true">
      {children}
    </div>
  )
}
