import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { referenceApi } from '../reference/referenceApi'
import { Badge, Button, Card, Field, Notice, PageHeader, Pager } from '../../shared/ui'
import { importApi } from './importApi'
import '../reports/reports.css'
import './import.css'

const fmt = (n) => (n ?? 0).toLocaleString()
const when = (t) => (t ? new Date(t).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : '')

/**
 * UI-107 Import Excel Masterlist (SDD 2.3 ImportPage): upload the .xlsx, check the columns and the
 * rows that will be left out, map each unmatched department and unit spelling once, then import and
 * review the rows that were not imported. Administrator only (also enforced on the server).
 */
export default function ImportPage() {
  const [file, setFile] = useState(null)
  const [sheet, setSheet] = useState('')
  const [preview, setPreview] = useState(null)
  const [departments, setDepartments] = useState([])
  const [deptChoices, setDeptChoices] = useState({})
  const [unitChoices, setUnitChoices] = useState({})
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [batchId, setBatchId] = useState(null)
  const [recent, setRecent] = useState([])
  const fileInput = useRef(null)

  const loadRecent = useCallback(() => importApi.batches().then(setRecent).catch(() => {}), [])

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch((e) => setError(e.message))
    loadRecent()
  }, [loadRecent])

  async function runPreview(nextSheet = sheet) {
    if (!file) return
    setBusy('preview')
    setError('')
    setPreview(null)
    try {
      const p = await importApi.preview(file, nextSheet)
      setPreview(p)
      setSheet(p.sheet)
      setDeptChoices(Object.fromEntries(p.departments.map((d) => [d.label, { action: '', departmentId: '', code: d.suggestedCode ?? '', name: d.label }])))
      setUnitChoices(Object.fromEntries(p.units.map((u) => [u.label, u.suggestedUnit
        ? { action: u.suggestedIsNew ? 'NEW' : 'MAP', unit: u.suggestedUnit }
        : { action: '', unit: '' }])))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy('')
    }
  }

  const undecided = useMemo(() => {
    if (!preview) return 0
    const d = preview.departments.filter((x) => !valid(deptChoices[x.label], 'dept')).length
    const u = preview.units.filter((x) => !valid(unitChoices[x.label], 'unit')).length
    return d + u
  }, [preview, deptChoices, unitChoices])

  const setAsideByChoice = useMemo(() => {
    if (!preview) return 0
    let n = 0
    for (const d of preview.departments) if (deptChoices[d.label]?.action === 'SET_ASIDE') n += d.rows
    for (const u of preview.units) if (unitChoices[u.label]?.action === 'SET_ASIDE') n += u.rows
    return n
  }, [preview, deptChoices, unitChoices])

  async function startImport() {
    const newDepts = Object.values(deptChoices).filter((c) => c.action === 'NEW').length
    const msg = `Import up to ${fmt(preview.readyRows - setAsideByChoice)} lines from ${preview.fileName}?`
      + (newDepts ? `\n\n${newDepts} new department(s) will be created.` : '')
      + '\n\nThe import runs as one transaction: it is saved completely or not at all.'
    if (!window.confirm(msg)) return
    setBusy('start')
    setError('')
    try {
      const request = {
        sheet,
        departments: Object.entries(deptChoices).map(([label, c]) => ({
          label, action: c.action, departmentId: c.departmentId ? Number(c.departmentId) : null, code: c.code, name: c.name,
        })),
        units: Object.entries(unitChoices).map(([label, c]) => ({ label, action: c.action, unit: c.unit })),
      }
      const b = await importApi.start(file, request)
      setBatchId(b.batch.id)
      setPreview(null)
      setFile(null)
      if (fileInput.current) fileInput.current.value = ''
      loadRecent()
    } catch (e) {
      const d = e.details
      const extra = d && (d.departments?.length || d.units?.length)
        ? ` ${[...(d.departments ?? []), ...(d.units ?? [])].slice(0, 8).join(', ')}` : ''
      setError(e.message + extra)
    } finally {
      setBusy('')
    }
  }

  return (
    <>
      <PageHeader title="Masterlist Import" subtitle="Bring the OPC's Excel Masterlist of Issue Orders into the system" />

      {batchId && <BatchResult id={batchId} onClose={() => { setBatchId(null); loadRecent() }} onFinished={loadRecent} />}

      {!batchId && (
        <Card title="1 · Choose the file" subtitle="The .xlsx file with the Masterlist sheet. Nothing is saved until you click Import.">
          <div className="import-file-row">
            <input ref={fileInput} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                   className="field-input" aria-label="Masterlist file"
                   onChange={(e) => { setFile(e.target.files?.[0] ?? null); setPreview(null); setSheet(''); setError('') }} />
            <Button onClick={() => runPreview('')} disabled={!file || !!busy}>
              {busy === 'preview' ? 'Reading the file…' : 'Preview'}
            </Button>
          </div>
          {busy === 'preview' && <p className="muted" style={{ margin: '10px 0 0' }}>A full masterlist takes about half a minute.</p>}
          {preview && preview.sheets.length > 1 && (
            <div style={{ maxWidth: 320, marginTop: 14 }}>
              <Field label="Sheet">
                {(id) => (
                  <select id={id} className="field-input" value={sheet}
                          onChange={(e) => { setSheet(e.target.value); runPreview(e.target.value) }}>
                    {preview.sheets.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                )}
              </Field>
            </div>
          )}
          {error && <Notice tone="error" style={{ marginTop: 14 }}>{error}</Notice>}
          {preview?.running && <Notice style={{ marginTop: 14 }}>Another import is running. Wait for it to finish before starting one.</Notice>}
        </Card>
      )}

      {preview && !batchId && (
        <>
          <div className="report-totals">
            <Total label="Lines in the sheet" value={fmt(preview.totalRows)} />
            <Total label="Ready to import" value={fmt(preview.readyRows)} />
            <Total label="Issue orders" value={fmt(preview.issueOrders)} />
            <Total label="Already in the system" value={fmt(preview.issueOrdersAlreadyInSystem)} hint="set aside" />
          </div>

          <div className="import-two">
            <Card title="Columns found" subtitle={`Headings on row ${preview.headerRow} of the ${preview.sheet} sheet`}>
              <table className="table import-compact">
                <tbody>
                  {preview.columns.map((c) => (
                    <tr key={c.heading}>
                      <td>{c.heading}</td>
                      <td className="num">{c.found ? `Column ${c.column}` : <Badge tone={c.required ? 'bad' : 'warn'}>Not found</Badge>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Lines that will not be imported" subtitle="Listed with their reason after the import, and downloadable as Excel">
              <table className="table import-compact">
                <thead><tr><th>Reason</th><th>Outcome</th><th className="r">Lines</th></tr></thead>
                <tbody>
                  {preview.problems.length === 0 && <tr><td colSpan={3} className="empty">Every line can be imported.</td></tr>}
                  {preview.problems.map((p) => (
                    <tr key={p.outcome + p.group}>
                      <td>{p.group}</td>
                      <td><Badge tone={p.outcome === 'REJECTED' ? 'bad' : 'warn'}>{p.outcome === 'REJECTED' ? 'Rejected' : 'Set aside'}</Badge></td>
                      <td className="r num">{fmt(p.rows)}</td>
                    </tr>
                  ))}
                  {preview.issueOrdersAlreadyInSystem > 0 && (
                    <tr>
                      <td>Issue order already in the system ({fmt(preview.issueOrdersAlreadyInSystem)} issue orders)</td>
                      <td><Badge tone="warn">Set aside</Badge></td>
                      <td className="r num muted">at import</td>
                    </tr>
                  )}
                </tbody>
              </table>
              <p className="muted import-note">
                Lines of one issue order that name a different department are also set aside at import.
                Issue orders keep their earliest invoice date as the date issued, since the masterlist has no issue date.
              </p>
            </Card>
          </div>

          <DepartmentMapping preview={preview} departments={departments} choices={deptChoices} setChoices={setDeptChoices} />
          <UnitMapping preview={preview} choices={unitChoices} setChoices={setUnitChoices} />

          <Card>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <div>
                <strong>3 · Import</strong>
                <div className="muted">
                  {undecided > 0
                    ? `${undecided} spelling${undecided === 1 ? '' : 's'} still need a choice.`
                    : `About ${fmt(preview.readyRows - setAsideByChoice)} lines will be imported as issue orders and items issued to their departments.`}
                </div>
              </div>
              <Button onClick={startImport} disabled={undecided > 0 || !!busy || preview.running || preview.readyRows === 0}>
                {busy === 'start' ? 'Starting…' : 'Import'}
              </Button>
            </div>
            {error && <Notice tone="error" style={{ marginTop: 14 }}>{error}</Notice>}
          </Card>
        </>
      )}

      <Card title="Past imports" style={{ marginTop: 22 }}>
        <table className="table">
          <thead>
            <tr><th>File</th><th>Started</th><th>Status</th><th className="r">Imported</th><th className="r">Set aside</th><th className="r">Rejected</th><th /></tr>
          </thead>
          <tbody>
            {recent.length === 0 && <tr><td colSpan={7} className="empty">No imports yet.</td></tr>}
            {recent.map((b) => (
              <tr key={b.id}>
                <td>{b.fileName}<div className="muted">{b.sheetName}</div></td>
                <td className="num">{when(b.importedAt)}</td>
                <td><StatusBadge status={b.status} /></td>
                <td className="r num">{fmt(b.importedRows)}</td>
                <td className="r num">{fmt(b.setAsideRows)}</td>
                <td className="r num">{fmt(b.rejectedRows)}</td>
                <td><button className="link-btn" onClick={() => setBatchId(b.id)}>View</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  )
}

function valid(c, kind) {
  if (!c || !c.action) return false
  if (c.action === 'SET_ASIDE') return true
  if (kind === 'dept') return c.action === 'MAP' ? !!c.departmentId : /^[A-Z0-9][A-Z0-9-]{0,19}$/.test((c.code ?? '').trim().toUpperCase())
  return /^[a-z][a-z .-]{0,19}$/.test((c.unit ?? '').trim().toLowerCase())
}

function DepartmentMapping({ preview, departments, choices, setChoices }) {
  const set = (label, patch) => setChoices((all) => ({ ...all, [label]: { ...all[label], ...patch } }))
  const fillRest = (action) => setChoices((all) => Object.fromEntries(Object.entries(all).map(([k, c]) => [k, c.action ? c : { ...c, action }])))

  return (
    <Card title={`2 · Departments to map (${preview.departments.length})`}
          subtitle={`${preview.matchedDepartmentLabels} spelling(s) already match a department. Map each other spelling once; the choice is saved for later imports.`}>
      {preview.departments.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>Every department spelling matches.</p>
      ) : (
        <>
          <div className="row import-bulk">
            <span className="muted">For every spelling without a choice:</span>
            <Button variant="secondary" onClick={() => fillRest('NEW')}>Create as new departments</Button>
            <Button variant="secondary" onClick={() => fillRest('SET_ASIDE')}>Set aside</Button>
          </div>
          <div className="import-scroll">
            <table className="table import-compact">
              <thead><tr><th>Spelling in the file</th><th className="r">Lines</th><th style={{ width: 190 }}>Action</th><th>Department</th></tr></thead>
              <tbody>
                {preview.departments.map((d) => {
                  const c = choices[d.label] ?? {}
                  return (
                    <tr key={d.label} className={valid(c, 'dept') ? '' : 'import-todo'}>
                      <td>{d.label}</td>
                      <td className="r num">{fmt(d.rows)}</td>
                      <td>
                        <select className="field-input" value={c.action ?? ''} aria-label={`Action for ${d.label}`}
                                onChange={(e) => set(d.label, { action: e.target.value })}>
                          <option value="">Choose…</option>
                          <option value="MAP">Same as a department</option>
                          <option value="NEW">New department</option>
                          <option value="SET_ASIDE">Set these lines aside</option>
                        </select>
                      </td>
                      <td>
                        {c.action === 'MAP' && (
                          <select className="field-input" value={c.departmentId ?? ''} aria-label={`Department for ${d.label}`}
                                  onChange={(e) => set(d.label, { departmentId: e.target.value })}>
                            <option value="">Choose a department</option>
                            {departments.map((x) => <option key={x.id} value={x.id}>{x.code} · {x.name}</option>)}
                          </select>
                        )}
                        {c.action === 'NEW' && (
                          <div className="import-new-dept">
                            <input className="field-input num" value={c.code ?? ''} maxLength={20} placeholder="CODE"
                                   aria-label={`New code for ${d.label}`}
                                   onChange={(e) => set(d.label, { code: e.target.value.toUpperCase() })} />
                            <input className="field-input" value={c.name ?? ''} maxLength={120} placeholder="Name"
                                   aria-label={`New name for ${d.label}`} onChange={(e) => set(d.label, { name: e.target.value })} />
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <p className="muted import-note">
            Two spellings can share one new code: the department is created once. Department heads and full names can be
            filled in later on the Departments page.
          </p>
        </>
      )}
    </Card>
  )
}

function UnitMapping({ preview, choices, setChoices }) {
  const set = (label, patch) => setChoices((all) => ({ ...all, [label]: { ...all[label], ...patch } }))
  return (
    <Card title={`Units to map (${preview.units.length})`}
          subtitle={`${preview.matchedUnitLabels} spelling(s) already match a unit. Units are stored in lower case, e.g. pc, copy, set.`}>
      {preview.units.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>Every unit spelling matches.</p>
      ) : (
        <div className="import-scroll">
          <table className="table import-compact">
            <thead><tr><th>Spelling in the file</th><th className="r">Lines</th><th style={{ width: 190 }}>Action</th><th>Unit</th></tr></thead>
            <tbody>
              {preview.units.map((u) => {
                const c = choices[u.label] ?? {}
                return (
                  <tr key={u.label} className={valid(c, 'unit') ? '' : 'import-todo'}>
                    <td>{u.label}</td>
                    <td className="r num">{fmt(u.rows)}</td>
                    <td>
                      <select className="field-input" value={c.action ?? ''} aria-label={`Action for unit ${u.label}`}
                              onChange={(e) => set(u.label, { action: e.target.value })}>
                        <option value="">Choose…</option>
                        <option value="MAP">Same as a unit</option>
                        <option value="NEW">New unit</option>
                        <option value="SET_ASIDE">Set these lines aside</option>
                      </select>
                    </td>
                    <td>
                      {(c.action === 'MAP' || c.action === 'NEW') && (
                        <input className="field-input" value={c.unit ?? ''} maxLength={20} placeholder="e.g. pc"
                               aria-label={`Unit for ${u.label}`}
                               onChange={(e) => set(u.label, { unit: e.target.value.toLowerCase() })} />
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

/** Step 3: the running import, then its result and the lines that were not imported. */
function BatchResult({ id, onClose, onFinished }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')
  const [rows, setRows] = useState(null)
  const [outcome, setOutcome] = useState('')
  const [page, setPage] = useState(0)
  const notified = useRef(false)

  useEffect(() => {
    let stop = false
    let timer
    const tick = () =>
      importApi.batch(id).then((v) => {
        if (stop) return
        setView(v)
        if (v.batch.status === 'RUNNING') timer = setTimeout(tick, 2000)
        else if (!notified.current) {
          notified.current = true
          onFinished()
        }
      }).catch((e) => !stop && setError(e.message))
    tick()
    return () => { stop = true; clearTimeout(timer) }
  }, [id, onFinished])

  const status = view?.batch.status
  useEffect(() => {
    if (status !== 'DONE') return
    importApi.rows(id, { outcome: outcome || undefined, page, size: 50 }).then(setRows).catch((e) => setError(e.message))
  }, [id, status, outcome, page])

  async function download() {
    try {
      const blob = await importApi.rowsXlsx(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `Masterlist-import-${id}-not-imported.xlsx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 10000)
    } catch (e) {
      setError(e.message)
    }
  }

  if (!view) return <Card>{error ? <Notice tone="error">{error}</Notice> : <p className="muted">Loading…</p>}</Card>
  const b = view.batch
  const pct = view.total ? Math.min(100, Math.round((view.done / view.total) * 100)) : null

  return (
    <>
      <Card title={`Import of ${b.fileName}`} subtitle={`Started ${when(b.importedAt)}${b.finishedAt ? ` · finished ${when(b.finishedAt)}` : ''}`}>
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <StatusBadge status={b.status} />
          {b.status !== 'RUNNING' && <Button variant="secondary" onClick={onClose}>Close</Button>}
        </div>
        {b.status === 'RUNNING' && (
          <div style={{ marginTop: 16 }}>
            <div>{view.stage ?? 'Working'}…{pct != null && ` ${pct}%`}</div>
            <div className="import-bar"><div style={{ width: `${pct ?? 5}%` }} /></div>
            <p className="muted import-note">You can leave this page; the import keeps running on the server.</p>
          </div>
        )}
        {b.status === 'FAILED' && <Notice tone="error" style={{ marginTop: 14 }}>{b.error}</Notice>}
        {error && <Notice tone="error" style={{ marginTop: 14 }}>{error}</Notice>}
      </Card>

      {b.status === 'DONE' && (
        <>
          <div className="report-totals">
            <Total label="Lines imported" value={fmt(b.importedRows)} hint={`of ${fmt(b.totalRows)}`} />
            <Total label="Issue orders" value={fmt(b.issueOrders)} />
            <Total label="Accountable persons added" value={fmt(b.createdPersons)} />
            <Total label="Set aside · rejected" value={`${fmt(b.setAsideRows)} · ${fmt(b.rejectedRows)}`} />
          </div>
          <Card title="Lines not imported" subtitle="Review these and encode the real items by hand if needed">
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
              <select className="field-input" style={{ maxWidth: 220 }} value={outcome} aria-label="Outcome"
                      onChange={(e) => { setOutcome(e.target.value); setPage(0) }}>
                <option value="">All</option>
                <option value="SET_ASIDE">Set aside</option>
                <option value="REJECTED">Rejected</option>
              </select>
              <Button variant="secondary" onClick={download} disabled={!b.setAsideRows && !b.rejectedRows}>Download as Excel</Button>
            </div>
            <table className="table import-compact">
              <thead><tr><th>Row</th><th>Outcome</th><th>Reason</th><th>Issue Order #</th><th>Department</th><th>Description</th></tr></thead>
              <tbody>
                {rows && rows.items.length === 0 && <tr><td colSpan={6} className="empty">None.</td></tr>}
                {rows?.items.map((r) => {
                  const c = safeJson(r.cells)
                  return (
                    <tr key={r.rowNo}>
                      <td className="num">{r.rowNo}</td>
                      <td><Badge tone={r.outcome === 'REJECTED' ? 'bad' : 'warn'}>{r.outcome === 'REJECTED' ? 'Rejected' : 'Set aside'}</Badge></td>
                      <td>{r.reason}</td>
                      <td className="num">{c['Issue Order #']}</td>
                      <td>{c.Department}</td>
                      <td className="import-desc">{c['Items/Description']}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {rows && <Pager page={rows.page} totalPages={rows.totalPages} total={rows.total} size={rows.size} onPage={setPage} />}
          </Card>
        </>
      )}
    </>
  )
}

function safeJson(s) {
  try {
    return JSON.parse(s) ?? {}
  } catch {
    return {}
  }
}

function StatusBadge({ status }) {
  if (status === 'DONE') return <Badge>Done</Badge>
  if (status === 'FAILED') return <Badge tone="bad">Failed · nothing saved</Badge>
  return <Badge tone="warn">Running</Badge>
}

function Total({ label, value, hint }) {
  return (
    <div className="report-total">
      <div className="report-total-label">{label}</div>
      <div className="report-total-value">{value}</div>
      {hint && <div className="muted" style={{ fontSize: 13 }}>{hint}</div>}
    </div>
  )
}
