import { Outlet } from 'react-router-dom'
import AppHeader from '../components/AppHeader.jsx'
import BottomNav from '../components/BottomNav.jsx'

export default function RoleShell({ navItems, title }) {
  return (
    <div className="app-shell">
      <AppHeader roleLabel={title} />
      <main className="app-main">
        <Outlet />
      </main>
      <BottomNav items={navItems} />
    </div>
  )
}
