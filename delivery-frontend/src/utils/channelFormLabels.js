export function channelExternalIdLabel(platformType) {
  if (platformType === 'telegram') return 'ID чата Telegram'
  return 'ID канала / группы'
}

export const CHANNEL_EXTERNAL_ID_HINT =
  'Это ID Telegram/MAX канала или группы, куда будут публиковаться заказы. ' +
  'Для Telegram: добавьте бота в канал или группу и отправьте команду /get_chat_id.'

export const CHANNEL_TELEGRAM_ID_STEPS =
  '1. Добавьте бота Добровоз администратором в канал или группу\n' +
  '2. Опубликуйте в канале только команду /get_chat_id (отдельным сообщением)\n' +
  '3. Скопируйте Chat ID из ответа бота в поле ниже'
