import { useCallback, useState } from 'react'
import { useAuth } from '../../context/AuthContext.jsx'

export default function PendingActivationScreen({ compactTitle, variant }) {
  const isApplication = variant === 'application'
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

      <section className="card pending-activation__welcome">
        <div className="pending-activation__welcome-row">
          <div>
            <h2 className="pending-activation__title">
              {isApplication ? 'Заявка отправлена' : 'Нет доступа к организации'}
            </h2>
            <p className="pending-activation__text">
              {isApplication
                ? 'Администратор Добровоза рассмотрит заявку и привяжет ваш Telegram/MAX аккаунт к курьеру. После одобрения вы сможете брать заказы из канала.'
                : 'Ваш аккаунт пока не добавлен в курьерскую службу. Доступ появится после одобрения заявки.'}
            </p>
          </div>
          <div className="pending-activation__illus" aria-hidden>
            <span className="pending-activation__illus-clip" />
          </div>
        </div>
      </section>

      <section className="card pending-activation__status">
        <div className="pending-activation__status-icon" aria-hidden>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <path
              d="M8 11V8a4 4 0 118 0v3"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
            />
            <rect
              x="6"
              y="11"
              width="12"
              height="9"
              rx="2"
              stroke="currentColor"
              strokeWidth="1.8"
            />
          </svg>
        </div>
        <h3 className="pending-activation__status-title">
          {isApplication ? 'Ожидание одобрения' : 'Ожидание доступа'}
        </h3>
        <p className="pending-activation__text">
          {isApplication
            ? 'Когда заявку одобрят, нажмите «Открыть заказ» в канале снова — мини-приложение откроет заказ без повторной регистрации.'
            : 'Если вам уже выдали логин и пароль, войдите с ними. Если доступа ещё нет — дождитесь одобрения заявки или оставьте новую на экране входа.'}
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

      <section className="card pending-activation__faq">
        <h3 className="pending-activation__faq-title">Почему нет доступа?</h3>
        <p className="pending-activation__text muted">
          Учётные записи в системе доставки создаёт курьерская служба. Самостоятельная регистрация
          в приложении не используется.
        </p>
      </section>
    </div>
  )
}
