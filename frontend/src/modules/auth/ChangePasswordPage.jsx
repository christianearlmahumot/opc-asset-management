import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Button, Field, Notice } from '../../shared/ui'
import { useAuth } from './AuthContext'
import './LoginPage.css'

const MIN = 8

/**
 * Change own password. It is forced after an Administrator gives a temporary password (UC-402),
 * and also reachable from the user menu at any time.
 */
export default function ChangePasswordPage() {
  const { user, ready, changePassword, logout } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!ready) return null
  if (!user) return <Navigate to="/login" replace />

  const forced = user.mustChangePassword
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  async function onSubmit(e) {
    e.preventDefault()
    const errs = {}
    if (!form.current) errs.current = forced ? 'Enter the temporary password you were given.' : 'Enter your current password.'
    if (form.next.length < MIN) errs.next = `Use at least ${MIN} characters.`
    else if (form.next === form.current) errs.next = 'Choose a password different from the current one.'
    if (form.confirm !== form.next) errs.confirm = 'The two passwords do not match.'
    setErrors(errs)
    if (Object.keys(errs).length) return

    setBusy(true)
    setError('')
    try {
      await changePassword(form.current, form.next)
      navigate('/', { replace: true, state: { flash: 'Your password was changed.' } })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={onSubmit} noValidate>
        <h1 className="login-title" style={{ fontSize: 24 }}>
          {forced ? 'Choose your password' : 'Change password'}
        </h1>
        <p className="login-sub">
          {forced
            ? `Welcome, ${user.fullName}. You signed in with a temporary password. Choose your own before continuing.`
            : `Signed in as ${user.username}`}
        </p>

        <div className="stack" style={{ gap: 14 }}>
          <Field
            label={forced ? 'Temporary password' : 'Current password'}
            type="password"
            value={form.current}
            onChange={set('current')}
            error={errors.current}
            autoComplete="current-password"
            autoFocus
          />
          <Field
            label="New password"
            type="password"
            value={form.next}
            onChange={set('next')}
            error={errors.next}
            hint={`At least ${MIN} characters. Do not reuse your username.`}
            autoComplete="new-password"
          />
          <Field
            label="Repeat new password"
            type="password"
            value={form.confirm}
            onChange={set('confirm')}
            error={errors.confirm}
            autoComplete="new-password"
          />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" block disabled={busy} style={{ marginTop: 6 }}>
            {busy ? 'Saving…' : 'Save password'}
          </Button>
          {forced ? (
            <button type="button" className="link-btn" onClick={logout} style={{ alignSelf: 'center' }}>
              Log out instead
            </button>
          ) : (
            <Link to="/" style={{ alignSelf: 'center', fontWeight: 600 }}>
              Cancel
            </Link>
          )}
        </div>
      </form>
    </main>
  )
}
