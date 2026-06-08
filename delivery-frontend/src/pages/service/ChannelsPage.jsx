import { useCallback, useEffect, useState } from 'react'
import {
  createChannel,
  deactivateChannel,
  listChannels,
  patchChannel,
} from '../../api/deliveryService.js'
import ChannelCard from '../../components/channels/ChannelCard.jsx'
import EmptyState, { EmptyStateIcon } from '../../components/EmptyState.jsx'
import { useCourierServiceId } from '../../hooks/useActiveOrg.js'
import { CHANNEL_EXTERNAL_ID_HINT, channelExternalIdLabel } from '../../utils/channelFormLabels.js'

const emptyForm = {
  type: 'telegram',
  chatType: 'channel',
  name: '',
  externalId: '',
  city: '',
  isActive: true,
}

export default function ChannelsPage() {
  const courierServiceId = useCourierServiceId()
  const [channels, setChannels] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [form, setForm] = useState(emptyForm)
  const [editId, setEditId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [showChannelHelp, setShowChannelHelp] = useState(false)

  const reload = useCallback(async () => {
    if (!courierServiceId) return
    setLoading(true)
    try {
      setChannels((await listChannels(courierServiceId)) || [])
    } catch (e) {
      setMessage(e?.message || 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [courierServiceId])

  useEffect(() => {
    reload()
  }, [reload])

  const openCreate = () => {
    setEditId(null)
    setForm(emptyForm)
    setShowForm(true)
  }

  const openEdit = (ch) => {
    setEditId(ch.id)
    setForm({
      type: ch.type,
      chatType: ch.chatType,
      name: ch.name,
      externalId: ch.externalId,
      city: ch.city || '',
      isActive: ch.isActive,
    })
    setShowForm(true)
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setMessage('')
    try {
      if (editId) {
        await patchChannel(editId, {
          name: form.name,
          externalId: form.externalId,
          city: form.city || null,
          chatType: form.chatType,
          isActive: form.isActive,
        })
      } else {
        await createChannel({
          courierServiceId,
          type: form.type,
          chatType: form.chatType,
          name: form.name,
          externalId: form.externalId,
          city: form.city || null,
          isActive: form.isActive,
        })
      }
      setShowForm(false)
      await reload()
    } catch (err) {
      setMessage(err?.message || 'Ошибка сохранения')
    }
  }

  const onDeactivate = async (id) => {
    if (!window.confirm('Отключить канал?')) return
    try {
      await deactivateChannel(id)
      await reload()
    } catch (err) {
      setMessage(err?.message || 'Ошибка')
    }
  }

  if (!courierServiceId) {
    return <div className="card">Выберите курьерскую службу в профиле.</div>
  }

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>Каналы / группы</h2>
          <button type="button" className="btn" onClick={openCreate}>
            Добавить
          </button>
        </div>
        <p className="muted" style={{ margin: '12px 0 0', fontSize: 14 }}>
          {CHANNEL_EXTERNAL_ID_HINT}
        </p>
        <p className="muted" style={{ margin: '8px 0 0', fontSize: 14 }}>
          Важно: бот должен быть добавлен в канал или группу и иметь право отправлять сообщения.
        </p>
        <p className="muted" style={{ margin: '4px 0 0', fontSize: 14 }}>
          Для канала Telegram добавьте бота администратором канала.
        </p>
        <button
          type="button"
          className="btn btn-secondary"
          style={{ marginTop: 12 }}
          onClick={() => setShowChannelHelp((v) => !v)}
        >
          Как проверить канал?
        </button>
        {showChannelHelp ? (
          <ol className="muted" style={{ margin: '12px 0 0', paddingLeft: 20, fontSize: 14 }}>
            <li>Убедитесь, что в настройках указан правильный ID канала.</li>
            <li>Добавьте бота в канал.</li>
            <li>Назначьте бота администратором.</li>
            <li>Разрешите боту публиковать сообщения.</li>
            <li>После этого откройте заказ и нажмите «Повторить публикацию».</li>
          </ol>
        ) : null}
      </div>

      {message && <p style={{ color: '#b91c1c' }}>{message}</p>}

      {showForm && (
        <form className="card form-stack" onSubmit={onSubmit}>
          <h3 style={{ marginTop: 0 }}>{editId ? 'Редактировать канал' : 'Новый канал'}</h3>
          <label>
            Платформа
            <select
              className="input"
              value={form.type}
              disabled={!!editId}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
            >
              <option value="telegram">Telegram</option>
              <option value="max">MAX</option>
            </select>
          </label>
          <label>
            Тип чата
            <select
              className="input"
              value={form.chatType}
              onChange={(e) => setForm({ ...form, chatType: e.target.value })}
            >
              <option value="channel">Канал</option>
              <option value="group">Группа</option>
            </select>
          </label>
          <label>
            Название
            <input
              className="input"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
          </label>
          <label>
            {channelExternalIdLabel(form.type)}
            <input
              className="input"
              value={form.externalId}
              onChange={(e) => setForm({ ...form, externalId: e.target.value })}
              required
            />
          </label>
          <p className="muted" style={{ margin: '0 0 12px', fontSize: 13 }}>
            {CHANNEL_EXTERNAL_ID_HINT}
          </p>
          <label>
            Город
            <input
              className="input"
              value={form.city}
              onChange={(e) => setForm({ ...form, city: e.target.value })}
            />
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.isActive}
              onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
            />
            Активен
          </label>
          <div className="form-actions">
            <button type="submit" className="btn">
              Сохранить
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>
              Отмена
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="card">Загрузка…</div>
      ) : channels.length === 0 ? (
        <EmptyState
          icon={
            <EmptyStateIcon>
              <svg viewBox="0 0 48 48" fill="none" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10 14h28v20a2 2 0 0 1-2 2H12a2 2 0 0 1-2-2V14z" />
                <path d="M10 14l14 10 14-10" />
              </svg>
            </EmptyStateIcon>
          }
          title="Каналов пока нет"
          description="Подключите Telegram-канал для публикации заказов курьерам."
        />
      ) : (
        channels.map((ch) => (
          <ChannelCard
            key={ch.id}
            channel={ch}
            showTechnicalId
            onEdit={openEdit}
            onDeactivate={onDeactivate}
          />
        ))
      )}
    </div>
  )
}
