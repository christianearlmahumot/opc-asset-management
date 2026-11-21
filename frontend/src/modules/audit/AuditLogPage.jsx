import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { usersApi } from '../users/usersApi'
import { Badge, Button, Card, Field, Notice, PageHeader, Pager } from '../../shared/ui'
import { formatDateTime } from '../../shared/format'
import {
  ACTIONS, actionLabel, auditApi, entityLabel, fieldLabel, formatValue, recordLink, summarize,
} from './auditApi'
import './audit.css'

/**
 * UI-404 View Audit Log (Administrator only). Filter by user, action, record type or date range;
 * entries are newest first, and opening one shows its old and new values. Entries are read-only:
 * the system offers no way to edit or delete them (REQ-404).
 */
export default function AuditLogPage() {
  const [params, setParams] = useSearchParams()
  const filters = {
    userId: params.get('user') ?? '',
    action: params.get('action') ?? '',
    entity: params.get('entity') ?? '',
    entityId: params.get('entityId') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
  }
  const page = Number(params.get('page') ?? 0)
  const selectedId = params.get('entry')

  const [users, setUsers] = useState([])
  const [entities, setEntities] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    usersApi.list().then(setUsers).catch(() => {})
    auditApi.entities().then(setEntities).catch(() => {})
  }, [])

  useEffect(() => {
    setError('')
    const q = Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== ''))
    auditApi
      .search({ ...q, page, size: 25 })
      .then(setResult)
      .catch((e) => {
        setResult(null)
        setError(e.message)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.toString().replace(/(^|&)entry=[^&]*/, '')])

  // The opened entry: taken from the current page, or fetched when opened from a link.
  useEffect(() => {
    if (!selectedId) return setSelected(null)
    const onPage = result?.items.find((e) => String(e.id) === selectedId)
    if (onPage) return setSelected(onPage)
    auditApi.get(selectedId).then(setSelected).catch(() => setSelected(null))
  }, [selectedId, result])

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v == null ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes) && !('entry' in changes)) next.delete('page')
    setParams(next)
  }

  const hasFilters = Object.values(filters).some((v) => v !== '')

  return (
    <>
      <PageHeader title="Audit Log" subtitle="Who changed what and when. Entries cannot be edited or deleted." />

      <div className={`audit-layout${selected ? ' with-panel' : ''}`}>
        <Card>
          <div className="audit-filters">
            <Field label="User">
              {(id) => (
                <select id={id} className="field-input" value={filters.userId}
                        onChange={(e) => update({ user: e.target.value })}>
                  <option value="">All users</option>
                  {users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
                </select>
              )}
            </Field>
            <Field label="Action">
              {(id) => (
                <select id={id} className="field-input" value={filters.action}
                        onChange={(e) => update({ action: e.target.value })}>
                  <option value="">All actions</option>
                  {ACTIONS.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
                </select>
              )}
            </Field>
            <Field label="Record type">
              {(id) => (
                <select id={id} className="field-input" value={filters.entity}
                        onChange={(e) => update({ entity: e.target.value, entityId: '' })}>
                  <option value="">All records</option>
                  {entities.map((e) => <option key={e} value={e}>{entityLabel(e)}</option>)}
                </select>
              )}
            </Field>
            <Field label="From" type="date" value={filters.from} max={filters.to || undefined}
                   onChange={(e) => update({ from: e.target.value })} />
            <Field label="To" type="date" value={filters.to} min={filters.from || undefined}
                   onChange={(e) => update({ to: e.target.value })} />
          </div>
          {(hasFilters || filters.entityId) && (
            <div className="row audit-active">
              {filters.entityId && (
                <Badge muted>{entityLabel(filters.entity)} #{filters.entityId}</Badge>
              )}
              <Button variant="secondary" onClick={() => setParams(new URLSearchParams())}>Clear filters</Button>
            </div>
          )}

          {error && <Notice tone="error" style={{ marginBottom: 14 }}>{error}</Notice>}

          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>User</th>
                <th>Action</th>
                <th>Record</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {result && result.items.length === 0 && (
                <tr><td colSpan={5} className="empty">No audit entries match these filters.</td></tr>
              )}
              {result?.items.map((e) => (
                <tr key={e.id} className={`clickable${String(e.id) === selectedId ? ' selected' : ''}`}
                    onClick={() => update({ entry: e.id })}>
                  <td className="num" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(e.at)}</td>
                  <td>{e.userName ?? <span className="muted">{e.userId ? `User #${e.userId}` : 'System / unknown'}</span>}</td>
                  <td><ActionBadge action={e.action} /></td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {entityLabel(e.entity)}{e.entityId != null && <span className="num muted"> #{e.entityId}</span>}
                  </td>
                  <td className="audit-summary">{summarize(e)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {result && (
            <Pager page={result.page} totalPages={result.totalPages} total={result.total} size={result.size}
                   onPage={(p) => update({ page: p })} />
          )}
        </Card>

        {selected && (
          <EntryPanel entry={selected} onClose={() => update({ entry: '' })}
                      onShowRecord={() => update({ entity: selected.entity, entityId: selected.entityId, entry: '' })} />
        )}
      </div>
    </>
  )
}

function ActionBadge({ action }) {
  const tone = action === 'LOGIN_FAILED' || action === 'DELETE' ? 'bad' : undefined
  return <Badge muted={action === 'LOGIN'} tone={tone}>{actionLabel(action)}</Badge>
}

/** UC-404 step 4: one entry with its old and new values. */
function EntryPanel({ entry, onClose, onShowRecord }) {
  const oldV = entry.oldValues ?? {}
  const newV = entry.newValues ?? {}
  const keys = [...new Set([...Object.keys(oldV), ...Object.keys(newV)])]
  const showOld = entry.action === 'UPDATE' || entry.action === 'DELETE' || Object.keys(oldV).length > 0
  const link = recordLink(entry.entity, entry.entityId)

  return (
    <Card className="audit-panel">
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <h2 className="card-title" style={{ margin: 0 }}>Entry #{entry.id}</h2>
        <button type="button" className="link-btn" onClick={onClose}>Close</button>
      </div>

      <dl className="audit-meta">
        <dt>When</dt><dd className="num">{formatDateTime(entry.at)}</dd>
        <dt>User</dt><dd>{entry.userName ?? (entry.userId ? `User #${entry.userId}` : 'System / unknown')}</dd>
        <dt>Action</dt><dd><ActionBadge action={entry.action} /></dd>
        <dt>Record</dt>
        <dd>
          {entityLabel(entry.entity)}
          {entry.entityId != null && (
            <> · {link ? <Link to={link}>#{entry.entityId}</Link> : <span className="num">#{entry.entityId}</span>}</>
          )}
        </dd>
      </dl>

      {keys.length === 0 ? (
        <p className="muted" style={{ margin: '4px 0 0' }}>No values were recorded for this entry.</p>
      ) : (
        <table className="table audit-values">
          <thead>
            <tr>
              <th>Field</th>
              {showOld && <th>Old value</th>}
              <th>New value</th>
            </tr>
          </thead>
          <tbody>
            {keys.map((k) => (
              <tr key={k}>
                <td>{fieldLabel(k)}</td>
                {showOld && <td className="audit-old">{formatValue(oldV[k])}</td>}
                <td className="audit-new">{formatValue(newV[k])}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {entry.entityId != null && (
        <Button variant="secondary" block style={{ marginTop: 16 }} onClick={onShowRecord}>
          Show all entries for this record
        </Button>
      )}
    </Card>
  )
}
