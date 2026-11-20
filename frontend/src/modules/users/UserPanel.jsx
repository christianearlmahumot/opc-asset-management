import { useState } from 'react'
import { Button, Card, Field, Notice } from '../../shared/ui'
import { ROLE_OPTIONS, suggestTempPassword, usersApi } from './usersApi'

/**
 * The right-hand panel of UI-402. In "add" mode it registers a new account with a temporary password.
 * In "edit" mode it changes the name, role and department, and offers password reset and deactivation.
 */
export default function UserPanel({ mode, user, isSelf, departments, flash, onSaved, onClose }) {
  const adding = mode === 'add'
  const [form, setForm] = useState(() => ({
    fullName: user?.fullName ?? '',
    username: user?.username ?? '',
    temporaryPassword: adding ? suggestTempPassword() : '',
    role: user?.role ?? 'STAFF',
    departmentId: user?.departmentId ?? '',
  }))
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))
  const isHead = form.role === 'DEPT_HEAD'

  function validate() {
    const e = {}
    if (!form.fullName.trim()) e.fullName = 'Enter the full name.'
    if (adding) {
      if (!/^[a-zA-Z0-9._-]{3,50}$/.test(form.username.trim()))
        e.username = 'Use 3–50 letters, numbers, dots, dashes or underscores.'
      if (form.temporaryPassword.length < 8) e.temporaryPassword = 'Use at least 8 characters.'
    }
    if (isHead && !form.departmentId) e.departmentId = 'Choose the department.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  /** Runs a save; on success the page shows `message` (and the temporary password, if one was issued). */
  async function run(action, message, issuedPassword) {
    setBusy(true)
    setError('')
    try {
      const saved = await action()
      onSaved(saved, { message, password: issuedPassword })
      return true
    } catch (err) {
      setErrors(err.fields || {})
      setError(err.message)
      return false
    } finally {
      setBusy(false)
    }
  }

  function onSubmit(e) {
    e.preventDefault()
    if (!validate()) return
    const departmentId = isHead ? Number(form.departmentId) : null
    if (adding) {
      const pw = form.temporaryPassword
      run(
        () =>
          usersApi.create({
            fullName: form.fullName,
            username: form.username,
            temporaryPassword: pw,
            role: form.role,
            departmentId,
          }),
        `Account created for ${form.fullName.trim()}.`,
        pw,
      )
    } else {
      run(
        () => usersApi.update(user.id, { fullName: form.fullName, role: form.role, departmentId }),
        'Changes saved.',
      )
    }
  }

  return (
    <Card className="user-panel">
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
        <h2 className="card-title" style={{ margin: 0 }}>
          {adding ? 'Add User' : 'Edit User'}
        </h2>
        <button type="button" className="link-btn" onClick={onClose} aria-label="Close panel">
          Close
        </button>
      </div>

      {flash?.message && !adding && (
        <Notice tone="ok" style={{ marginBottom: 14 }}>
          {flash.message}
        </Notice>
      )}
      {flash?.password && !adding && <IssuedPassword password={flash.password} />}

      <form className="stack" style={{ gap: 14 }} onSubmit={onSubmit} noValidate>
        <Field label="Full name" required value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        <Field
          label="Username"
          required={adding}
          value={form.username}
          onChange={set('username')}
          error={errors.username}
          readOnly={!adding}
          hint={adding ? 'Used to log in. It cannot be changed later.' : undefined}
          autoComplete="off"
        />
        {adding && (
          <Field label="Temporary password" required error={errors.temporaryPassword}
                 hint="Give this to the user. They must replace it at first login.">
            {(id) => (
              <div className="row" style={{ gap: 8 }}>
                <input id={id} className="field-input num" value={form.temporaryPassword}
                       onChange={set('temporaryPassword')} autoComplete="off" />
                <Button variant="secondary" onClick={() => setForm((f) => ({ ...f, temporaryPassword: suggestTempPassword() }))}>
                  New
                </Button>
              </div>
            )}
          </Field>
        )}
        <Field label="Role" required error={errors.role}
               hint={isSelf ? 'You cannot change your own role.' : undefined}>
          {(id) => (
            <select id={id} className="field-input" value={form.role} onChange={set('role')} disabled={isSelf}>
              {ROLE_OPTIONS.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Department (Department Head only)" required={isHead} error={errors.departmentId}
               hint={isHead ? 'This user will see only this department’s assets and reports.' : undefined}>
          {(id) => (
            <select id={id} className="field-input" value={isHead ? form.departmentId : ''}
                    onChange={set('departmentId')} disabled={!isHead}>
              <option value="">{isHead ? 'Choose a department' : '—'}</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.code} · {d.name}
                </option>
              ))}
            </select>
          )}
        </Field>

        {error && <Notice tone="error">{error}</Notice>}

        <div className="row">
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </Button>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </div>
      </form>

      {!adding && user && (
        <AccountActions user={user} isSelf={isSelf} busy={busy} run={run} />
      )}
    </Card>
  )
}

/** Password reset and deactivate / reactivate, kept apart from the main form. */
function AccountActions({ user, isSelf, busy, run }) {
  const [resetting, setResetting] = useState(false)
  const [temp, setTemp] = useState('')
  const [confirmDeactivate, setConfirmDeactivate] = useState(false)

  return (
    <>
      <hr className="divider" />
      <h3 className="section-title">Account</h3>

      {!resetting ? (
        <p className="account-line">
          <span className="muted">Forgot their password?</span>{' '}
          <button className="link-btn" onClick={() => { setTemp(suggestTempPassword()); setResetting(true) }}>
            Set a temporary password
          </button>
        </p>
      ) : (
        <div className="stack" style={{ gap: 10, marginBottom: 16 }}>
          <Field label="New temporary password" value={temp} onChange={(e) => setTemp(e.target.value)}
                 className="field-input num" autoComplete="off"
                 hint="This logs the user out everywhere. They must choose a new password at next login." />
          <div className="row">
            <Button disabled={busy || temp.length < 8}
                    onClick={async () => {
                      if (await run(() => usersApi.resetPassword(user.id, temp), 'Temporary password set.', temp)) {
                        setResetting(false)
                      }
                    }}>
              Set password
            </Button>
            <Button variant="secondary" onClick={() => setResetting(false)}>Cancel</Button>
          </div>
        </div>
      )}

      {user.active ? (
        isSelf ? (
          <p className="account-line muted">You cannot deactivate your own account.</p>
        ) : !confirmDeactivate ? (
          <p className="account-line">
            <button className="link-btn danger" onClick={() => setConfirmDeactivate(true)}>
              Deactivate account
            </button>
          </p>
        ) : (
          <Notice>
            <p style={{ margin: '0 0 10px' }}>
              {user.fullName} will no longer be able to log in. Their records and history stay in the system.
            </p>
            <div className="row">
              <Button disabled={busy}
                      onClick={() => run(() => usersApi.deactivate(user.id), 'Account deactivated.')}>
                Deactivate
              </Button>
              <Button variant="secondary" onClick={() => setConfirmDeactivate(false)}>Keep active</Button>
            </div>
          </Notice>
        )
      ) : (
        <p className="account-line">
          <span className="muted">This account is deactivated.</span>{' '}
          <button className="link-btn" disabled={busy}
                  onClick={() => run(() => usersApi.activate(user.id), 'Account reactivated.')}>
            Reactivate
          </button>
        </p>
      )}
    </>
  )
}

function IssuedPassword({ password }) {
  return (
    <Notice style={{ marginBottom: 14 }}>
      Temporary password: <strong className="num">{password}</strong>
      <br />
      <span style={{ fontSize: 13 }}>Give it to the user now. It is not shown again.</span>
    </Notice>
  )
}
