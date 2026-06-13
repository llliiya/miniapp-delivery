import { useLocation, useNavigate } from 'react-router-dom'
import DobrovozLogo from './DobrovozLogo.jsx'
import BackIcon from './BackIcon.jsx'
import { useMobileViewport } from '../hooks/useMobileViewport.js'
import { canNavigateBack, navigateBack } from '../utils/navigationBack.js'

export default function AppHeader({ roleLabel }) {
  const navigate = useNavigate()
  const location = useLocation()
  const isMobile = useMobileViewport()
  const showBack = isMobile && canNavigateBack(location.pathname)

  return (
    <header className="app-header">
      <div className="app-header__start">
        {showBack ? (
          <button
            type="button"
            className="app-header__back"
            onClick={() => navigateBack(navigate)}
            aria-label="Назад"
            title="Назад"
          >
            <BackIcon />
          </button>
        ) : null}
        <div className="app-header__brand">
          <DobrovozLogo size={32} />
          <span className="app-header__title">Добровоз</span>
        </div>
      </div>
      {roleLabel && <span className="app-header__role">{roleLabel}</span>}
    </header>
  )
}
