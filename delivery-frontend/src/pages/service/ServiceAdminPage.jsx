import { useState } from 'react'
import {
  addCourier,
  addOrganizationMember,
  createCourierService,
  createRestaurant,
  listCouriers,
  listOrganizations,
  listRestaurants,
} from '../../api/deliveryService.js'
import { useAuth } from '../../context/AuthContext.jsx'

export default function ServiceAdminPage() {
  const { deliveryMe } = useAuth()
  const [serviceName, setServiceName] = useState('')
  const [restaurantName, setRestaurantName] = useState('')
  const [courierUserId, setCourierUserId] = useState('')
  const [courierName, setCourierName] = useState('')
  const [managerUserId, setManagerUserId] = useState('')
  const [message, setMessage] = useState('')
  const [orgs, setOrgs] = useState([])
  const [restaurants, setRestaurants] = useState([])
  const [couriers, setCouriers] = useState([])

  const serviceOrg = orgs.find((o) => o.type === 'courier_service')
  const serviceId = serviceOrg?.id || deliveryMe?.activeOrganizationId

  const reload = async () => {
    const [o, r] = await Promise.all([listOrganizations(), listRestaurants()])
    setOrgs(o || [])
    setRestaurants(r || [])
    const svc = (o || []).find((x) => x.type === 'courier_service')
    if (svc?.id) {
      setCouriers(await listCouriers(svc.id))
    }
  }

  const onCreateService = async () => {
    setMessage('')
    try {
      await createCourierService(serviceName)
      setMessage('Служба создана')
      await reload()
    } catch (e) {
      setMessage(e?.message || 'Ошибка')
    }
  }

  const onCreateRestaurant = async () => {
    if (!serviceId) {
      setMessage('Нет courierServiceId')
      return
    }
    try {
      await createRestaurant(restaurantName, serviceId)
      setMessage('Объект создан')
      await reload()
    } catch (e) {
      setMessage(e?.message || 'Ошибка')
    }
  }

  const onAddCourier = async () => {
    if (!serviceId) return
    try {
      await addCourier({
        courierServiceId: serviceId,
        userId: Number(courierUserId),
        displayName: courierName || undefined,
      })
      setMessage('Курьер добавлен')
      await reload()
    } catch (e) {
      setMessage(e?.message || 'Ошибка')
    }
  }

  const onAddManager = async (restaurantId) => {
    try {
      await addOrganizationMember(restaurantId, {
        userId: Number(managerUserId),
        role: 'manager',
      })
      setMessage('Менеджер объекта добавлен')
    } catch (e) {
      setMessage(e?.message || 'Ошибка')
    }
  }

  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>Админ (этап 2)</h2>
      <p className="muted">Быстрые действия для проверки API. В проде — отдельные экраны.</p>
      {message && <p>{message}</p>}

      <h3>Служба</h3>
      <input
        placeholder="Название службы"
        value={serviceName}
        onChange={(e) => setServiceName(e.target.value)}
        style={{ width: '100%', marginBottom: 8 }}
      />
      <button type="button" className="btn" onClick={onCreateService}>
        Создать службу
      </button>

      <h3 style={{ marginTop: 16 }}>Объект</h3>
      <input
        placeholder="Название объекта"
        value={restaurantName}
        onChange={(e) => setRestaurantName(e.target.value)}
        style={{ width: '100%', marginBottom: 8 }}
      />
      <button type="button" className="btn" onClick={onCreateRestaurant}>
        Создать объект
      </button>

      <h3 style={{ marginTop: 16 }}>Курьер</h3>
      <input
        placeholder="userId"
        value={courierUserId}
        onChange={(e) => setCourierUserId(e.target.value)}
        style={{ width: '100%', marginBottom: 8 }}
      />
      <input
        placeholder="displayName"
        value={courierName}
        onChange={(e) => setCourierName(e.target.value)}
        style={{ width: '100%', marginBottom: 8 }}
      />
      <button type="button" className="btn" onClick={onAddCourier}>
        Добавить курьера
      </button>

      <h3 style={{ marginTop: 16 }}>Менеджер объекта</h3>
      <input
        placeholder="userId менеджера"
        value={managerUserId}
        onChange={(e) => setManagerUserId(e.target.value)}
        style={{ width: '100%', marginBottom: 8 }}
      />
      {restaurants.map((r) => (
        <button
          key={r.id}
          type="button"
          className="btn btn-secondary"
          style={{ display: 'block', width: '100%', marginBottom: 8 }}
          onClick={() => onAddManager(r.id)}
        >
          + менеджер в {r.name}
        </button>
      ))}

      <button type="button" className="btn btn-secondary" style={{ marginTop: 16 }} onClick={reload}>
        Обновить списки
      </button>
      <pre style={{ fontSize: 11, marginTop: 12, overflow: 'auto' }}>
        {JSON.stringify({ orgs, restaurants, couriers }, null, 2)}
      </pre>
    </div>
  )
}
