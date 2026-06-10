import { Navigate, Route, Routes } from 'react-router-dom'
import RoleShell from '../layouts/RoleShell.jsx'
import RestaurantProfilePage from '../pages/restaurant/RestaurantProfilePage.jsx'
import PickupPointsPage from '../pages/restaurant/PickupPointsPage.jsx'
import RestaurantChannelsPage from '../pages/restaurant/RestaurantChannelsPage.jsx'
import RestaurantOrdersPage from '../pages/restaurant/RestaurantOrdersPage.jsx'
import NewOrderPage from '../pages/shared/NewOrderPage.jsx'
import RestaurantOrderDetailPage from '../pages/restaurant/RestaurantOrderDetailPage.jsx'
import RestaurantStaffPage from '../pages/restaurant/RestaurantStaffPage.jsx'

const NAV = [
  { to: '/restaurant/orders', label: 'Заказы', icon: 'orders', end: false },
  { to: '/restaurant/pickup', label: 'Точки', icon: 'pickup', end: false },
  { to: '/restaurant/channels', label: 'Каналы', icon: 'channels', end: false },
  { to: '/restaurant/staff', label: 'Сотрудники', icon: 'staff', end: false },
  { to: '/restaurant/profile', label: 'Профиль', icon: 'profile', end: false },
]

export default function RestaurantRoutes() {
  return (
    <Routes>
      <Route element={<RoleShell navItems={NAV} title="Объект" />}>
        <Route index element={<Navigate to="orders" replace />} />
        <Route path="orders" element={<RestaurantOrdersPage />} />
        <Route path="orders/new" element={<NewOrderPage />} />
        <Route path="orders/:orderId" element={<RestaurantOrderDetailPage />} />
        <Route path="orders/:orderId/edit" element={<RestaurantOrderDetailPage editMode />} />
        <Route path="create" element={<Navigate to="/restaurant/orders/new" replace />} />
        <Route path="pickup" element={<PickupPointsPage />} />
        <Route path="channels" element={<RestaurantChannelsPage />} />
        <Route path="staff" element={<RestaurantStaffPage />} />
        <Route path="profile" element={<RestaurantProfilePage />} />
      </Route>
    </Routes>
  )
}
