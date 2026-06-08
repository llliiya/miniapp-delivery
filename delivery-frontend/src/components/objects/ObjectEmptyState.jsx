import { Link } from 'react-router-dom'
import EmptyState, { EmptyStateIcon } from '../EmptyState.jsx'

export default function ObjectEmptyState() {
  return (
    <EmptyState
      icon={
        <EmptyStateIcon>
          <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8 20l16-12 16 12v18a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V20z" />
            <path d="M20 40V26h8v14" />
          </svg>
        </EmptyStateIcon>
      }
      title="У вас пока нет объектов"
      description="Создайте первый объект, чтобы публиковать заказы для доставки."
    >
      <Link to="/service/restaurants/new" className="btn">
        Создать объект
      </Link>
    </EmptyState>
  )
}
