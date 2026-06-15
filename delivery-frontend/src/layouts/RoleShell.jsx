import { Outlet } from 'react-router-dom'
import AppHeader from '../components/AppHeader.jsx'
import BottomNav from '../components/BottomNav.jsx'
import ServiceCitySwitch from '../components/service/ServiceCitySwitch.jsx'

export default function RoleShell({ navItems, title, showCityFilter = false }) {
  return (
    <div className="app-shell">
      <AppHeader
        roleLabel={title}
        trailing={showCityFilter ? <ServiceCitySwitch /> : null}
      />
      <main className="app-main">
        <Outlet />
      </main>
      <BottomNav items={navItems} />
    </div>
  )
}
