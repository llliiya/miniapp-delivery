import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import StandaloneLayout from '../../layouts/StandaloneLayout.jsx'
import PageContainer from '../../components/PageContainer.jsx'
import RestaurantJoinForm from './RestaurantJoinForm.jsx'
import './AuthPage.css'

export default function JoinRestaurantPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const partnerCode = (searchParams.get('partner') || '').trim().toUpperCase() || null
  const [submitted, setSubmitted] = useState(false)

  return (
    <StandaloneLayout>
      <PageContainer>
        <div className="auth-page">
          <h1 className="auth-title">
            {submitted ? 'Спасибо!' : 'Заявка на подключение'}
          </h1>
          {submitted ? (
            <div className="auth-form">
              <p className="auth-hint">
                Мы получили вашу заявку.
                <br />
                Наш менеджер свяжется с вами в ближайшее время.
              </p>
              <Link to="/login" className="auth-submit" style={{ display: 'block', textAlign: 'center', textDecoration: 'none' }}>
                Ко входу
              </Link>
            </div>
          ) : (
            <RestaurantJoinForm
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
