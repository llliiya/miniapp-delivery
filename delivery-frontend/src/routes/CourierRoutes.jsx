import { Navigate, Route, Routes } from 'react-router-dom'
import RoleShell from '../layouts/RoleShell.jsx'
import PendingSectionMessage from '../components/courier/PendingSectionMessage.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import CourierOrdersPage from '../pages/courier/CourierOrdersPage.jsx'
import CourierOrderDetailPage from '../pages/courier/CourierOrderDetailPage.jsx'
import CourierMyOrdersPage from '../pages/courier/CourierMyOrdersPage.jsx'
import CourierMyOrderDetailPage from '../pages/courier/CourierMyOrderDetailPage.jsx'
import CourierProfilePage from '../pages/courier/CourierProfilePage.jsx'

const NAV = [
  { to: '/courier/orders', label: 'Заказы', icon: 'orders', end: false },
  { to: '/courier/my-orders', label: 'Мои', icon: 'my', end: false },
  { to: '/courier/profile', label: 'Профиль', icon: 'profile', end: false },
]

function CourierMapPage() {
  const { isPendingCourier } = useAuth()
  if (isPendingCourier) {
    return (
      <PendingSectionMessage
        title="Карта"
        message="Карта будет доступна после активации аккаунта администратором службы доставки."
      />
    )
  }
  return (
    <div className="courier-page">
      <div className="card">
        <h2 className="courier-page__title">Карта</h2>
        <p className="muted">Точки свободных заказов на карте появятся в следующих версиях.</p>
      </div>
    </div>
  )
}

export default function CourierRoutes() {
  return (
    <Routes>
      <Route element={<RoleShell navItems={NAV} title="Курьер" />}>
        <Route index element={<Navigate to="orders" replace />} />
        <Route path="orders" element={<CourierOrdersPage />} />
        <Route path="orders/:orderId" element={<CourierOrderDetailPage />} />
        <Route path="my-orders" element={<CourierMyOrdersPage />} />
        <Route path="my-orders/:orderId" element={<CourierMyOrderDetailPage />} />
        <Route path="my" element={<Navigate to="/courier/my-orders" replace />} />
        <Route path="map" element={<CourierMapPage />} />
        <Route path="profile" element={<CourierProfilePage />} />
      </Route>
    </Routes>
  )
}
