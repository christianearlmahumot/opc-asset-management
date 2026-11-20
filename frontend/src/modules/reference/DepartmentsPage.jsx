import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import AssetTabs from '../assets/AssetTabs'
import { Button, Card, Field, Notice, PageHeader } from '../../shared/ui'
import { dash } from '../../shared/format'
import { referenceApi } from './referenceApi'

const EMPTY = { code: '', name: '', headName: '', headDesignation: '' }

/** Department list (codes as used in OPC records and CIT-U purchasing documents). Administrators edit it. */
export default function DepartmentsPage() {
  const { can } = useAuth()
  const canEdit = can(['ADMIN'])
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [panel, setPanel] = useState(null) // null | { id?: number }

  useEffect(() => {
    referenceApi.departmentDetails().then(setRows).catch((e) => setError(e.message))
  }, [])

  const selected = panel?.id ? rows.find((r) => r.id === panel.id) : null

  function onSaved(saved) {
    setRows((list) => {
      const next = list.some((r) => r.id === saved.id) ? list.map((r) => (r.id === saved.id ? saved : r)) : [...list, saved]
      return next.sort((a, b) => a.code.localeCompare(b.code))
    })
    setPanel({ id: saved.id, saved: true })
  }

  return (
    <>
      <PageHeader title="Departments" subtitle="Department codes used on issue orders, reports and purchasing documents" />
      <AssetTabs />
      <div className={`split${panel ? ' with-panel' : ''}`}>
        <Card>
          {canEdit && <Button onClick={() => setPanel({})}>+ Add Department</Button>}
          {error && <Notice tone="error" style={{ marginTop: 14 }}>{error}</Notice>}
          <table className="table" style={{ marginTop: canEdit ? 18 : 0 }}>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Head</th>
                <th>Designation</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className={`${canEdit ? 'clickable' : ''}${selected?.id === r.id ? ' selected' : ''}`}
                    onClick={() => canEdit && setPanel({ id: r.id })}>
                  <td><strong>{r.code}</strong></td>
                  <td>{r.name}</td>
                  <td>{dash(r.headName)}</td>
                  <td>{dash(r.headDesignation)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
        {panel && canEdit && (
          <DepartmentPanel key={panel.id ?? 'new'} dept={selected} saved={panel.saved}
                           onSaved={onSaved} onClose={() => setPanel(null)} />
        )}
      </div>
    </>
  )
}

function DepartmentPanel({ dept, saved, onSaved, onClose }) {
  const [form, setForm] = useState(() => ({ ...EMPTY, ...(dept ?? {}) }))
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  async function onSubmit(e) {
    e.preventDefault()
    const errs = {}
    if (!form.code.trim()) errs.code = 'Enter the code.'
    if (!form.name.trim()) errs.name = 'Enter the name.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      const body = { code: form.code, name: form.name, headName: form.headName, headDesignation: form.headDesignation }
      onSaved(dept ? await referenceApi.updateDepartment(dept.id, body) : await referenceApi.createDepartment(body))
    } catch (err) {
      setErrors(err.fields || {})
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="side-panel">
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
        <h2 className="card-title" style={{ margin: 0 }}>{dept ? 'Edit Department' : 'Add Department'}</h2>
        <button type="button" className="link-btn" onClick={onClose}>Close</button>
      </div>
      {saved && <Notice tone="ok" style={{ marginBottom: 14 }}>Saved.</Notice>}
      <form className="stack" style={{ gap: 14 }} onSubmit={onSubmit} noValidate>
        <Field label="Code" required value={form.code} error={errors.code}
               onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
               hint="As written on OPC records, e.g. MEDTECH, ME, CCS." />
        <Field label="Name" required value={form.name} onChange={set('name')} error={errors.name} />
        <Field label="Head" value={form.headName ?? ''} onChange={set('headName')} error={errors.headName}
               hint="Signs the issue order as Received by." />
        <Field label="Designation" value={form.headDesignation ?? ''} onChange={set('headDesignation')}
               placeholder="e.g. Chair, Dean" />
        {error && <Notice tone="error">{error}</Notice>}
        <div className="row">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</Button>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
        </div>
      </form>
    </Card>
  )
}
