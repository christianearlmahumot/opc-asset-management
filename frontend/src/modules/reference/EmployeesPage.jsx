import { useCallback, useEffect, useMemo, useState } from 'react'
import AssetTabs from '../assets/AssetTabs'
import { Badge, Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { dash } from '../../shared/format'
import { referenceApi } from './referenceApi'

const STATUS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'RESIGNED', label: 'Resigned' },
  { value: 'INACTIVE', label: 'Inactive' },
]

/**
 * Accountable persons (SRS 2.5: maintained by OPC staff, no HR data imported). They are named on issue
 * orders and Property Inventory Slips, so this is personal data under the Data Privacy Act.
 */
export default function EmployeesPage() {
  const [rows, setRows] = useState([])
  const [departments, setDepartments] = useState([])
  const [filter, setFilter] = useState({ q: '', departmentId: '', includeInactive: false })
  const [error, setError] = useState('')
  const [panel, setPanel] = useState(null)

  const load = useCallback(() => {
    const params = { q: filter.q || undefined, departmentId: filter.departmentId || undefined,
      includeInactive: filter.includeInactive }
    referenceApi.employees(params).then(setRows).catch((e) => setError(e.message))
  }, [filter])

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch((e) => setError(e.message))
  }, [])
  useEffect(() => {
    const t = setTimeout(load, 250)
    return () => clearTimeout(t)
  }, [load])

  const deptCode = useMemo(() => Object.fromEntries(departments.map((d) => [d.id, d.code])), [departments])
  const selected = panel?.id ? rows.find((r) => r.id === panel.id) : null

  function onSaved(saved) {
    setRows((list) => (list.some((r) => r.id === saved.id) ? list.map((r) => (r.id === saved.id ? saved : r)) : [saved, ...list]))
    setPanel({ id: saved.id, saved: true })
  }

  return (
    <>
      <PageHeader title="Accountable Persons" subtitle="Employees who can be named as accountable for assets" />
      <AssetTabs />
      <div className={`split${panel ? ' with-panel' : ''}`}>
        <Card>
          <div className="row" style={{ gap: 12, flexWrap: 'wrap' }}>
            <Button onClick={() => setPanel({})}>+ Add Person</Button>
            <input className="field-input" style={{ maxWidth: 280 }} placeholder="Search name or employee no."
                   value={filter.q} onChange={(e) => setFilter((f) => ({ ...f, q: e.target.value }))} />
            <select className="field-input" style={{ maxWidth: 220 }} value={filter.departmentId}
                    onChange={(e) => setFilter((f) => ({ ...f, departmentId: e.target.value }))}>
              <option value="">All departments</option>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
            </select>
            <label className="row" style={{ gap: 6 }}>
              <input type="checkbox" checked={filter.includeInactive}
                     onChange={(e) => setFilter((f) => ({ ...f, includeInactive: e.target.checked }))} />
              Show resigned / inactive
            </label>
          </div>
          {error && <Notice tone="error" style={{ marginTop: 14 }}>{error}</Notice>}
          <table className="table" style={{ marginTop: 18 }}>
            <thead>
              <tr>
                <th>Name</th>
                <th>Employee no.</th>
                <th>Department</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr><td colSpan={4} className="empty">No accountable persons found.</td></tr>
              ) : rows.map((r) => (
                <tr key={r.id} className={`clickable${selected?.id === r.id ? ' selected' : ''}`}
                    onClick={() => setPanel({ id: r.id })}>
                  <td>{r.fullName}</td>
                  <td className="num">{dash(r.employeeNo)}</td>
                  <td>{r.departmentId ? deptCode[r.departmentId] : '—'}</td>
                  <td>{r.status === 'ACTIVE' ? <Badge>Active</Badge> : <Badge muted>{STATUS.find((s) => s.value === r.status)?.label}</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
        {panel && (
          <EmployeePanel key={panel.id ?? 'new'} person={selected} saved={panel.saved} departments={departments}
                         onSaved={onSaved} onClose={() => setPanel(null)} />
        )}
      </div>
    </>
  )
}

function EmployeePanel({ person, saved, departments, onSaved, onClose }) {
  const [form, setForm] = useState(() => ({
    fullName: person?.fullName ?? '',
    employeeNo: person?.employeeNo ?? '',
    departmentId: person?.departmentId ?? '',
    status: person?.status ?? 'ACTIVE',
  }))
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  async function onSubmit(e) {
    e.preventDefault()
    if (!form.fullName.trim()) {
      setErrors({ fullName: 'Enter the full name.' })
      return
    }
    setBusy(true)
    setError('')
    try {
      const body = { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null }
      onSaved(person ? await referenceApi.updateEmployee(person.id, body) : await referenceApi.createEmployee(body))
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
        <h2 className="card-title" style={{ margin: 0 }}>{person ? 'Edit Person' : 'Add Person'}</h2>
        <button type="button" className="link-btn" onClick={onClose}>Close</button>
      </div>
      {saved && <Notice tone="ok" style={{ marginBottom: 14 }}>Saved.</Notice>}
      <form className="stack" style={{ gap: 14 }} onSubmit={onSubmit} noValidate>
        <Field label="Full name" required value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        <Field label="Employee no." value={form.employeeNo} onChange={set('employeeNo')} error={errors.employeeNo} />
        <SelectField label="Department" value={form.departmentId} onChange={set('departmentId')}
                     error={errors.departmentId} placeholder="—"
                     options={departments.map((d) => ({ value: d.id, label: `${d.code} · ${d.name}` }))} />
        {person && (
          <SelectField label="Status" value={form.status} onChange={set('status')} options={STATUS}
                       hint="Resignation turnover (Step 3) moves their assets before they are marked Resigned." />
        )}
        {error && <Notice tone="error">{error}</Notice>}
        <div className="row">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</Button>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
        </div>
      </form>
    </Card>
  )
}
