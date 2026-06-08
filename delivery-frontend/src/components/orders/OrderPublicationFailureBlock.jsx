import { useState } from 'react'
import { republishOrder } from '../../api/deliveryService.js'
import {
  formatPlatformError,
  republishResultMessage,
} from '../../utils/orderPublicationMessages.js'

export default function OrderPublicationFailureBlock({ order, onRepublished }) {
  const [republishing, setRepublishing] = useState(false)
  const [feedback, setFeedback] = useState('')
  const failures = order?.publicationFailures || []

  const onRepublish = async () => {
    setRepublishing(true)
    setFeedback('')
    try {
      const res = await republishOrder(order.id)
      const message = republishResultMessage(res?.warnings)
      setFeedback(message)
      if (onRepublished) {
        await onRepublished(res?.order, message)
      }
    } catch (e) {
      setFeedback(e?.message || 'Не удалось повторить публикацию')
    } finally {
      setRepublishing(false)
    }
  }

  const primaryFailure = failures[0]

  return (
    <section className="card order-publication-failure" role="alert">
      <h3 className="order-publication-failure__title">Публикация не выполнена</h3>
      {primaryFailure ? (
        <p className="order-publication-failure__text">
          Не удалось отправить заказ в канал «{primaryFailure.channelName}».
          Проверьте, что бот добавлен в канал и имеет право публиковать сообщения.
        </p>
      ) : (
        <p className="order-publication-failure__text">
          Заказ не был опубликован в каналы. Проверьте настройки каналов и права бота.
        </p>
      )}
      {failures.map((failure) => {
        const errorLine = formatPlatformError(failure.platform, failure.errorMessage)
        return errorLine ? (
          <p key={failure.channelId} className="order-publication-failure__error muted">
            {errorLine}
          </p>
        ) : null
      })}
      {failures.length > 1 ? (
        <p className="order-publication-failure__text muted">
          Не удалось отправить также в:{' '}
          {failures
            .slice(1)
            .map((f) => `«${f.channelName}»`)
            .join(', ')}
          .
        </p>
      ) : null}
      {order?.canRepublish ? (
        <button
          type="button"
          className="btn order-publication-failure__btn"
          onClick={onRepublish}
          disabled={republishing}
        >
          {republishing ? 'Публикация…' : 'Повторить публикацию'}
        </button>
      ) : null}
      {feedback ? <p className="order-publication-failure__feedback">{feedback}</p> : null}
    </section>
  )
}
