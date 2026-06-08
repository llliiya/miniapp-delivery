export function platformLabel(type) {
  if (type === 'telegram') return 'Telegram'
  if (type === 'max') return 'MAX'
  return type
}

export function chatTypeLabel(chatType) {
  if (chatType === 'channel') return 'Канал'
  if (chatType === 'group') return 'Группа'
  return chatType
}

export function channelPublicationLabel(channel) {
  const platform = platformLabel(channel?.type)
  const chat = chatTypeLabel(channel?.chatType).toLowerCase()
  return `${platform} ${chat}`
}

export function channelStatusLabel(isActive) {
  return isActive ? 'Активен' : 'Неактивен'
}

export function channelTypeLine(channel) {
  return `${platformLabel(channel?.type)} • ${chatTypeLabel(channel?.chatType)}`
}
