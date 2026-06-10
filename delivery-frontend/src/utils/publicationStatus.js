export const PUBLICATION_STATUS = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  PUBLISHED: 'published',
  FAILED: 'failed',
}

export function publicationStatusLabel(status) {
  switch (status) {
    case PUBLICATION_STATUS.PENDING:
      return 'Ожидает публикации'
    case PUBLICATION_STATUS.PROCESSING:
      return 'Публикуется…'
    case PUBLICATION_STATUS.PUBLISHED:
      return 'Опубликован'
    case PUBLICATION_STATUS.FAILED:
      return 'Ошибка публикации'
    default:
      return '—'
  }
}

export function isPublicationInProgress(status) {
  return status === PUBLICATION_STATUS.PENDING || status === PUBLICATION_STATUS.PROCESSING
}
