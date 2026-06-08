import DobrovozLogo from './DobrovozLogo.jsx'

export default function AppHeader({ roleLabel }) {
  return (
    <header className="app-header">
      <div className="app-header__brand">
        <DobrovozLogo size={32} />
        <span className="app-header__title">Добровоз</span>
      </div>
      {roleLabel && <span className="app-header__role">{roleLabel}</span>}
    </header>
  )
}
