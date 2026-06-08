import { chatTypeLabel, platformLabel } from '../../utils/channelLabels.js'

export default function ChannelCard({
  channel,
  onEdit,
  onDeactivate,
  showTechnicalId = false,
  showActions = true,
}) {
  return (
    <div className={`card channel-card${channel.isActive ? '' : ' card-inactive'}`}>
      <div className="channel-card__head">
        <strong className="channel-card__name">{channel.name}</strong>
        <span
          className={
            channel.isActive
              ? 'channel-card__status channel-card__status--active'
              : 'channel-card__status channel-card__status--inactive'
          }
        >
          {channel.isActive ? 'Активен' : 'Отключён'}
        </span>
      </div>

      <p className="channel-card__meta muted">
        {platformLabel(channel.type)} • {chatTypeLabel(channel.chatType)}
      </p>

      {channel.city ? <p className="channel-card__city muted">Город: {channel.city}</p> : null}

      {showTechnicalId && channel.externalId ? (
        <p className="channel-card__tech-id muted">Технический ID: {channel.externalId}</p>
      ) : null}

      {showActions && (onEdit || (onDeactivate && channel.isActive)) ? (
        <div className="channel-card__actions">
          {onEdit ? (
            <button type="button" className="btn btn-secondary" onClick={() => onEdit(channel)}>
              Редактировать
            </button>
          ) : null}
          {onDeactivate && channel.isActive ? (
            <button type="button" className="btn btn-secondary" onClick={() => onDeactivate(channel.id)}>
              Отключить
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
