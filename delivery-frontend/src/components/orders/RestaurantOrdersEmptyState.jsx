import { Link } from 'react-router-dom'
import EmptyState, { EmptyStateIcon } from '../EmptyState.jsx'

export default function RestaurantOrdersEmptyState({ filteredOut = false, onResetFilters }) {
  return (
    <EmptyState
      icon={
        <EmptyStateIcon>
          <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14 8h20l4 6v22a2 2 0 0 1-2 2H12a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z" />
            <path d="M34 14H14M20 22h12M20 28h8" />
          </svg>
        </EmptyStateIcon>
      }
      title={filteredOut ? 'Нет заказов по фильтру' : 'Заказов пока нет'}
      description={
        filteredOut
          ? 'Измените фильтр, чтобы увидеть другие заказы.'
          : 'Создайте первый заказ, чтобы он появился в списке и был опубликован для курьеров.'
      }
    >
      {filteredOut ? (
        <button type="button" className="btn" onClick={onResetFilters}>
          Сбросить фильтр
        </button>
      ) : (
        <Link to="/restaurant/orders/new" className="btn">
          Создать заказ
        </Link>
      )}
    </EmptyState>
  )
}
