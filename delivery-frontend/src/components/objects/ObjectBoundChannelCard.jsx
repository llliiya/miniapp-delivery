import { channelTypeLine } from '../../utils/channelLabels.js'

export default function ObjectBoundChannelCard({ channel }) {
  return (
    <article className="object-bound-channel-card">
      <div className="object-bound-channel-card__head">
        <strong className="object-bound-channel-card__name">{channel.name}</strong>
        <span className="objects-status objects-status--active object-bound-channel-card__badge">
          Подключён
        </span>
      </div>
      <p className="object-bound-channel-card__meta muted">{channelTypeLine(channel)}</p>
      {channel.city ? (
        <p className="object-bound-channel-card__city muted">{channel.city}</p>
      ) : null}
    </article>
  )
}
