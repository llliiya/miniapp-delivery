import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import StandaloneLayout from '../../layouts/StandaloneLayout.jsx'
import PageContainer from '../../components/PageContainer.jsx'
import RegistrationRequestScreen from './RegistrationRequestScreen.jsx'
import './AuthPage.css'

export default function JoinCourierPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const partnerCode = (searchParams.get('partner') || '').trim().toUpperCase() || null
  const [submitted, setSubmitted] = useState(false)

  return (
    <StandaloneLayout>
      <PageContainer>
        <div className="auth-page">
          <h1 className="auth-title">
            {submitted ? 'Спасибо!' : 'Заявка курьера'}
          </h1>
          {submitted ? (
            <div className="auth-form">
              <p className="auth-hint">
                Мы получили вашу заявку.
                <br />
                После одобрения с вами свяжутся или придёт уведомление в мессенджере.
              </p>
              <Link to="/login" className="auth-submit" style={{ display: 'block', textAlign: 'center', textDecoration: 'none' }}>
                Ко входу
              </Link>
            </div>
          ) : (
            <RegistrationRequestScreen
              partnerCode={partnerCode}
              onBack={() => (window.history.length > 1 ? navigate(-1) : navigate('/login'))}
              onSuccess={() => setSubmitted(true)}
            />
          )}
        </div>
      </PageContainer>
    </StandaloneLayout>
  )
}
