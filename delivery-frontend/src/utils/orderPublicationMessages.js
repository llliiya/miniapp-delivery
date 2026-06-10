export const NO_ACTIVE_CHANNELS_WARNING = 'no_active_channels'
export const PUBLICATION_FAILED_WARNING = 'publication_failed'
export const PUBLICATION_PARTIAL_FAILED_WARNING = 'publication_partial_failed'

export const ORDER_CREATED_MESSAGE = 'Заказ создан'

export const ORDER_PUBLISHED_MESSAGE = 'Заказ опубликован'

export const ORDER_CREATED_PUBLISHED_MESSAGE = 'Заказ создан и опубликован'

export const ORDER_CREATED_NOT_PUBLISHED_MESSAGE =
  'Заказ создан, но не опубликован: каналы публикации не подключены'

export const ORDER_CREATED_PUBLICATION_FAILED_MESSAGE =
  'Заказ создан, но не опубликован: не удалось отправить сообщение в канал'

export const ORDER_CREATED_PARTIAL_PUBLISHED_MESSAGE =
  'Заказ создан, но опубликован не во все каналы'

export const ORDER_REPUBLISHED_MESSAGE = 'Заказ опубликован'

export const ORDER_REPUBLISH_FAILED_MESSAGE =
  'Не удалось опубликовать заказ. Проверьте настройки канала.'

export const ORDER_REPUBLISH_NO_CHANNELS_MESSAGE = 'Каналы публикации не подключены'

export const ORDER_UPDATED_PUBLISHED_MESSAGE = 'Заказ обновлен и опубликован'

export const ORDER_UPDATED_PUBLICATION_FAILED_MESSAGE =
  'Заказ обновлен, но не удалось обновить сообщение в канале'

export function orderCreatedMessage(warnings = []) {
  return publicationResultMessage(warnings, {
    success: ORDER_CREATED_PUBLISHED_MESSAGE,
    noChannels: ORDER_CREATED_NOT_PUBLISHED_MESSAGE,
    failed: ORDER_CREATED_PUBLICATION_FAILED_MESSAGE,
    partial: ORDER_CREATED_PARTIAL_PUBLISHED_MESSAGE,
  })
}

export function republishResultMessage(warnings = []) {
  return publicationResultMessage(warnings, {
    success: ORDER_REPUBLISHED_MESSAGE,
    noChannels: ORDER_REPUBLISH_NO_CHANNELS_MESSAGE,
    failed: ORDER_REPUBLISH_FAILED_MESSAGE,
    partial: ORDER_REPUBLISH_FAILED_MESSAGE,
  })
}

export function orderUpdatedMessage(warnings = []) {
  return publicationResultMessage(warnings, {
    success: ORDER_UPDATED_PUBLISHED_MESSAGE,
    noChannels: ORDER_UPDATED_PUBLISHED_MESSAGE,
    failed: ORDER_UPDATED_PUBLICATION_FAILED_MESSAGE,
    partial: ORDER_UPDATED_PUBLICATION_FAILED_MESSAGE,
  })
}

function publicationResultMessage(warnings, messages) {
  if (warnings.includes(NO_ACTIVE_CHANNELS_WARNING)) {
    return messages.noChannels
  }
  if (warnings.includes(PUBLICATION_FAILED_WARNING)) {
    return messages.failed
  }
  if (warnings.includes(PUBLICATION_PARTIAL_FAILED_WARNING)) {
    return messages.partial
  }
  return messages.success
}

export function isOrderPublicationSuccessMessage(message) {
  return (
    message === ORDER_CREATED_MESSAGE ||
    message === ORDER_PUBLISHED_MESSAGE ||
    message === ORDER_CREATED_PUBLISHED_MESSAGE ||
    message === ORDER_REPUBLISHED_MESSAGE ||
    message === ORDER_UPDATED_PUBLISHED_MESSAGE
  )
}

export function formatPlatformError(platform, errorMessage) {
  if (!errorMessage) return null
  const short = String(errorMessage).replace(/^Bad Request:\s*/i, '').trim()
  const label = platform === 'telegram' ? 'Telegram' : platform === 'max' ? 'MAX' : platform
  return `Ошибка ${label}: ${short}`
}

export function shouldShowPublicationFailureBlock(order) {
  if (!order) return false
  if (order.publicationStatus === 'processing' || order.publicationStatus === 'pending') {
    return false
  }
  if (!order.canRepublish) return false
  return order.publicationStatus === 'failed' || (order.publicationFailures?.length ?? 0) > 0
}
