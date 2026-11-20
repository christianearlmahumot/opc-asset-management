import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { referenceApi } from '../reference/referenceApi'
import { Badge, Button, Card, Notice, PageHeader } from '../../shared/ui'
import { usersApi } from './usersApi'
import UserPanel from './UserPanel'
import './users.css'

/** UI-402 Manage User Accounts and Roles: table on the left, Add/Edit panel on the right. */
export default function UserManagementPage() {
  const { user: me } = useAuth()
  const [users, setUsers] = useState([])
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [panel, setPanel] = useState(null) // null | { mode: 'add' } | { mode: 'edit', id }
  const [flash, setFlash] = useState(null) // { message, password? } shown in the panel after a save

  const load = useCallback(() => {
    setLoading(true)
    return Promise.all([usersApi.list(), referenceApi.departments()])
      .then(([u, d]) => {
        setUsers(u)
        setDepartments(d)
        setLoadError('')
      })
      .catch((err) => setLoadError(err.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const deptCode = useMemo(() => Object.fromEntries(departments.map((d) => [d.id, d.code])), [departments])
  const selected = panel?.mode === 'edit' ? users.find((u) => u.id === panel.id) : null

  function onSaved(saved, notice) {
    setUsers((list) => {
      const exists = list.some((u) => u.id === saved.id)
      return exists ? list.map((u) => (u.id === saved.id ? saved : u)) : [...list, saved]
    })
    setFlash(notice)
    setPanel({ mode: 'edit', id: saved.id })
  }

  return (
    <>
      <PageHeader title="User Management" subtitle="Create accounts and assign one role to each user" />

      <div className={`users-layout${panel ? ' with-panel' : ''}`}>
        <Card>
          <Button
            onClick={() => {
              setFlash('')
              setPanel({ mode: 'add' })
            }}
          >
            + Add User
          </Button>

          {loadError && (
            <Notice tone="error" style={{ marginTop: 16 }}>
              {loadError}
            </Notice>
          )}

          <table className="table" style={{ marginTop: 18 }}>
            <thead>
              <tr>
                <th>Name</th>
                <th>Username</th>
                <th>Role</th>
                <th>Department</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {loading && users.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    Loading users…
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    No users yet.
                  </td>
                </tr>
              ) : (
                users.map((u) => (
                  <tr
                    key={u.id}
                    className={`clickable${selected?.id === u.id ? ' selected' : ''}`}
                    onClick={() => {
                      setFlash('')
                      setPanel({ mode: 'edit', id: u.id })
                    }}
                  >
                    <td>
                      {u.fullName}
                      {u.id === me.id && <span className="muted"> (you)</span>}
                    </td>
                    <td>{u.username}</td>
                    <td>{u.roleLabel}</td>
                    <td>{u.departmentId ? deptCode[u.departmentId] ?? '—' : '—'}</td>
                    <td>
                      {!u.active ? (
                        <Badge muted>Deactivated</Badge>
                      ) : u.mustChangePassword ? (
                        <Badge muted>Temporary password</Badge>
                      ) : (
                        <Badge>Active</Badge>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </Card>

        {panel && (
          <UserPanel
            key={panel.mode === 'add' ? 'add' : panel.id}
            mode={panel.mode}
            user={selected}
            isSelf={selected?.id === me.id}
            departments={departments}
            flash={flash}
            onSaved={onSaved}
            onClose={() => {
              setPanel(null)
              setFlash('')
            }}
          />
        )}
      </div>
    </>
  )
}
