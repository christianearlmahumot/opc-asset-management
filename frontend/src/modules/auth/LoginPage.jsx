import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button, Field, Notice } from '../../shared/ui'
import { useAuth } from './AuthContext'
import './LoginPage.css'

/** UI-401 Log In. */
export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(e) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await login(username, password)
      navigate(location.state?.from || '/', { replace: true })
    } catch (err) {
      setError(err.message)
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={onSubmit} noValidate>
        <div className="login-mark" aria-hidden="true">
          OPC
        </div>
        <h1 className="login-title">Fixed-Asset Management</h1>
        <p className="login-sub">Office of the Property Custodian · Inventory Section</p>

        <div className="stack" style={{ gap: 14 }}>
          <Field
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
          />
          <Field
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
          {error && <Notice>{error}</Notice>}
          <Button type="submit" block disabled={busy} style={{ marginTop: 6 }}>
            {busy ? 'Logging in…' : 'Log In'}
          </Button>
        </div>
      </form>
    </main>
  )
}
