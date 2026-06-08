import AppHeader from '../components/AppHeader.jsx'

export default function StandaloneLayout({ children, roleLabel }) {
  return (
    <div className="app-shell app-shell--standalone">
      <AppHeader roleLabel={roleLabel} />
      <main className="app-main app-main--standalone">{children}</main>
    </div>
  )
}
