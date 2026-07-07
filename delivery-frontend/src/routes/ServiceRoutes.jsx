import { Navigate, Route, Routes } from 'react-router-dom'
import RoleShell from '../layouts/RoleShell.jsx'
import { ServiceCityProvider } from '../context/ServiceCityContext.jsx'
import ServiceOrdersPage from '../pages/service/ServiceOrdersPage.jsx'
import NewOrderPage from '../pages/shared/NewOrderPage.jsx'
import ServiceOrderDetailPage from '../pages/service/ServiceOrderDetailPage.jsx'
import ServiceProfilePage from '../pages/service/ServiceProfilePage.jsx'
import ServiceAdminPage from '../pages/service/ServiceAdminPage.jsx'
import ServicePartnerProgramRulesPage from '../pages/service/ServicePartnerProgramRulesPage.jsx'
import ServiceFinancialSettingsPage from '../pages/service/ServiceFinancialSettingsPage.jsx'
import ServiceCouriersPage from '../pages/service/ServiceCouriersPage.jsx'
import ServiceCourierDetailPage from '../pages/service/ServiceCourierDetailPage.jsx'
import ChannelsPage from '../pages/service/ChannelsPage.jsx'
import ObjectsPage from '../pages/service/ObjectsPage.jsx'
import AddObjectPage from '../pages/service/AddObjectPage.jsx'
import ObjectDetailPage from '../pages/service/ObjectDetailPage.jsx'
import ObjectStaffPage from '../pages/service/ObjectStaffPage.jsx'
import ObjectChannelsPage from '../pages/service/ObjectChannelsPage.jsx'
import PickupPointsPage from '../pages/restaurant/PickupPointsPage.jsx'
import RequireServiceStaff from '../components/RequireServiceStaff.jsx'
import { DEV_AUTH_ENABLED } from '../config.js'

const NAV = [
  { to: '/service/orders', label: 'Заказы', icon: 'orders', end: false },
  { to: '/service/restaurants', label: 'Объекты', icon: 'objects', end: false },
  { to: '/service/couriers', label: 'Курьеры', icon: 'couriers', end: false },
  { to: '/service/channels', label: 'Каналы', icon: 'channels', end: false },
  { to: '/service/profile', label: 'Профиль', icon: 'profile', end: false },
]

export default function ServiceRoutes() {
  return (
    <ServiceCityProvider>
      <Routes>
        <Route element={<RoleShell navItems={NAV} title="Курьерская служба" showCityFilter />}>
        <Route index element={<Navigate to="orders" replace />} />
        <Route path="orders" element={<ServiceOrdersPage />} />
        <Route path="orders/new" element={<NewOrderPage />} />
        <Route path="orders/:orderId" element={<ServiceOrderDetailPage />} />
        <Route path="orders/:orderId/edit" element={<ServiceOrderDetailPage editMode />} />
        <Route path="restaurants" element={<ObjectsPage />} />
        <Route path="restaurants/new" element={<AddObjectPage />} />
        <Route path="restaurants/:restaurantId" element={<ObjectDetailPage />} />
        <Route path="restaurants/:restaurantId/staff" element={<ObjectStaffPage />} />
        <Route path="restaurants/:restaurantId/channels" element={<ObjectChannelsPage />} />
        <Route path="restaurants/:restaurantId/pickup" element={<PickupPointsPage />} />
        <Route path="couriers" element={<ServiceCouriersPage />} />
        <Route path="couriers/:courierId" element={<ServiceCourierDetailPage />} />
        <Route path="registration-requests" element={<Navigate to="/service/restaurants" replace />} />
        <Route path="channels" element={<ChannelsPage />} />
        <Route path="profile" element={<ServiceProfilePage />} />
        <Route
          path="partner-program"
          element={(
            <RequireServiceStaff>
              <ServicePartnerProgramRulesPage />
            </RequireServiceStaff>
          )}
        />
        <Route
          path="financial-settings"
          element={(
            <RequireServiceStaff>
              <ServiceFinancialSettingsPage />
            </RequireServiceStaff>
          )}
        />
        {DEV_AUTH_ENABLED && <Route path="admin" element={<ServiceAdminPage />} />}
        </Route>
      </Routes>
    </ServiceCityProvider>
  )
}
