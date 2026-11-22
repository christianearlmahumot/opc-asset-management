import { useEffect, useState } from 'react'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import ReportTabs from './ReportTabs'
import { reportsApi } from './reportsApi'
import './reports.css'

const REPORTS = [
  {
    value: 'MASTERLIST',
    label: 'Masterlist of Issue Orders',
    about: 'One row per issue order line, with the 14 columns of the OPC masterlist. The dates filter by date issued.',
    dates: 'Date issued',
  },
  {
    value: 'DEPARTMENT',
    label: 'Department Inventory Report',
    about: 'The current items of one department, grouped by room, with subtotals and a grand total, as of today.',
  },
  {
    value: 'TURNED_OVER',
    label: 'Inventory of Turned-over Items',
    about: 'Every item returned to OPC storage (turnovers and resignation turnovers), with the reason and the disposal date once disposed of. The dates filter by date returned; the department is the one it came from.',
    dates: 'Date returned',
  },
]

const FORMATS = [
  { value: 'XLSX', label: 'Excel (.xlsx)' },
  { value: 'PDF', label: 'PDF' },
]

/**
 * UI-305 Export Reports (SDD 5.2 ReportsPage): choose the report, its filters and the format, then
 * download it. Administrators and Property Staff only (also enforced on the server).
 */
export default function ExportReportsPage() {
  const [departments, setDepartments] = useState([])
  const [form, setForm] = useState({ type: 'MASTERLIST', format: 'XLSX', departmentId: '', from: '', to: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const [done, setDone] = useState(null)

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch((e) => setError(e.message))
  }, [])

  const report = REPORTS.find((r) => r.value === form.type)
  const set = (k) => (e) => {
    setForm((f) => ({ ...f, [k]: e.target.value }))
    setFields((x) => ({ ...x, [k]: undefined }))
    setDone(null)
  }

  async function exportFile() {
    setError('')
    setFields({})
    setDone(null)
    if (form.type === 'DEPARTMENT' && !form.departmentId) {
      setFields({ departmentId: 'Choose the department.' })
      return
    }
    setBusy(true)
    try {
      const params = {
        type: form.type,
        format: form.format,
        departmentId: form.departmentId || undefined,
        from: report.dates && form.from ? form.from : undefined,
        to: report.dates && form.to ? form.to : undefined,
      }
      const file = await reportsApi.exportFile(params)
      saveFile(file.blob, file.fileName)
      setDone(file)
    } catch (e) {
      setError(e.message)
      setFields(e.fields ?? {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Export Reports" subtitle="Download the OPC's reports as Excel or PDF" />
      <ReportTabs />

      <Card>
        <div className="export-grid">
          <SelectField label="Report" value={form.type} onChange={set('type')} options={REPORTS} />
          <SelectField label="Format" value={form.format} onChange={set('format')} options={FORMATS} />
        </div>
        <p className="muted export-about">{report.about}</p>

        <div className="export-grid">
          <SelectField
            label="Department"
            required={form.type === 'DEPARTMENT'}
            value={form.departmentId}
            onChange={set('departmentId')}
            placeholder={form.type === 'DEPARTMENT' ? 'Choose a department' : 'All departments'}
            options={departments.map((d) => ({ value: String(d.id), label: `${d.code} · ${d.name}` }))}
            error={fields.departmentId}
          />
          {report.dates && (
            <div className="export-dates">
              <Field label={`${report.dates} from`} type="date" value={form.from} onChange={set('from')} error={fields.from} />
              <Field label="to" type="date" value={form.to} onChange={set('to')} />
            </div>
          )}
        </div>

        {error && <Notice tone="error" style={{ marginTop: 16 }}>{error}</Notice>}
        {done && done.rows === 0 && (
          <Notice style={{ marginTop: 16 }}>
            No records match these filters. {done.fileName} was downloaded with the column headings only.
          </Notice>
        )}
        {done && done.rows > 0 && (
          <Notice tone="ok" style={{ marginTop: 16 }}>
            Downloaded {done.fileName} · {done.rows.toLocaleString()} row{done.rows === 1 ? '' : 's'}.
          </Notice>
        )}

        <div className="row" style={{ marginTop: 18 }}>
          <Button onClick={exportFile} disabled={busy}>{busy ? 'Preparing the file…' : 'Export'}</Button>
          {busy && <span className="muted">A large masterlist can take up to a minute.</span>}
        </div>
      </Card>
    </>
  )
}

function saveFile(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
