export function channelExternalIdLabel(platformType) {
  if (platformType === 'telegram') return 'ID чата Telegram'
  return 'ID канала / группы'
}

export const CHANNEL_EXTERNAL_ID_HINT =
  'Это ID Telegram/MAX канала или группы, куда будут публиковаться заказы.'
