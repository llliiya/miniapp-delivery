import { useCallback, useState } from 'react'
import { useAuth } from '../../context/AuthContext.jsx'

export default function BlockedCourierScreen({ compactTitle }) {
  const { refreshDeliveryStatus } = useAuth()
  const [refreshing, setRefreshing] = useState(false)

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    try {
      await refreshDeliveryStatus()
    } finally {
      setRefreshing(false)
    }
  }, [refreshDeliveryStatus])

  return (
    <div className="pending-activation">
      {compactTitle && (
        <h2 className="pending-activation__page-title">{compactTitle}</h2>
      )}

      <section className="card pending-activation__status">
        <h2 className="pending-activation__title">Доступ заблокирован</h2>
        <p className="pending-activation__text">
          Ваш доступ к заказам временно ограничен.
        </p>
        <button
          type="button"
          className="btn pending-activation__refresh-btn"
          onClick={onRefresh}
          disabled={refreshing}
        >
          {refreshing ? 'Проверяем…' : 'Обновить статус'}
        </button>
      </section>
    </div>
  )
}
