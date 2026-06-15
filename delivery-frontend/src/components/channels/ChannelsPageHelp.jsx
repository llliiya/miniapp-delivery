import { CHANNEL_EXTERNAL_ID_HINT } from '../../utils/channelFormLabels.js'

const VERIFY_STEPS = [
  'Убедитесь, что в настройках указан правильный ID канала.',
  'Добавьте бота в канал.',
  'Назначьте бота администратором.',
  'Разрешите боту публиковать сообщения.',
  'После этого откройте заказ и нажмите «Повторить публикацию».',
]

export default function ChannelsPageHelp() {
  return (
    <section className="channels-page__help" aria-label="Справка по подключению каналов">
      <p className="channels-page__help-text">{CHANNEL_EXTERNAL_ID_HINT}</p>
      <p className="channels-page__help-text">
        Важно: бот должен быть добавлен в канал или группу и иметь право отправлять сообщения.
        Для канала Telegram добавьте бота администратором канала.
      </p>
      <p className="channels-page__help-title">Как проверить канал?</p>
      <ol className="channels-page__help-steps">
        {VERIFY_STEPS.map((step) => (
          <li key={step}>{step}</li>
        ))}
      </ol>
    </section>
  )
}
