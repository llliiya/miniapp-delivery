import { Link } from 'react-router-dom'
import EmptyState, { EmptyStateIcon } from '../EmptyState.jsx'

export default function ServiceOrdersEmptyState({
  filteredOut = false,
  restaurantFilter = '',
  restaurantName = '',
  onResetFilters,
}) {
  const newOrderHref = restaurantFilter
    ? `/service/orders/new?object=${restaurantFilter}`
    : '/service/orders/new'

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
          ? 'Измените фильтр или период, чтобы увидеть другие заказы.'
          : restaurantFilter
            ? `Создайте первый заказ для объекта${restaurantName ? ` «${restaurantName}»` : ''}.`
            : 'Создайте заказ для объекта или выберите объект в фильтре.'
      }
    >
      {filteredOut ? (
        <button type="button" className="btn" onClick={onResetFilters}>
          Сбросить фильтры
        </button>
      ) : restaurantFilter ? (
        <Link to={newOrderHref} className="btn">
          Создать заказ
        </Link>
      ) : (
        <Link to="/service/restaurants/new" className="btn">
          Создать объект
        </Link>
      )}
      {!filteredOut && (
        <Link to="/service/restaurants" className="btn btn-secondary">
          Перейти к объектам
        </Link>
      )}
    </EmptyState>
  )
}
