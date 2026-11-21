import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { assetsApi } from '../assets/assetsApi'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { formatMoney } from '../../shared/format'
import IoSheet from './IoSheet'
import { downloadPdf, issueOrdersApi, openPdf } from './issueOrdersApi'
import './issueOrders.css'

const todayIso = () => {
  const d = new Date()
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/**
 * UI-202 Select Assets and Generate Issue Order. Left: items in OPC storage to pick from, the
 * accountable person and signatories. Right: the issue order as it will print; after Confirm it shows
 * the saved order with Print and Download PDF.
 */
export default function PrepareIssueOrderPage() {
  const { requestId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [request, setRequest] = useState(null)
  const [departments, setDepartments] = useState([])
  const [deptId, setDeptId] = useState('')
  const [people, setPeople] = useState([])
  const [stock, setStock] = useState([])
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState({}) // assetId -> qty (string)
  const [form, setForm] = useState({ employeeId: '', room: '', notedByName: '', notedByTitle: 'Dean', receivedByTitle: '', remarks: '' })
  const [nextNo, setNextNo] = useState(null)
  const [saved, setSaved] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([referenceApi.departmentDetails(), issueOrdersApi.nextNumber(),
      requestId ? issueOrdersApi.request(requestId) : Promise.resolve(null)])
      .then(([d, no, r]) => {
        setDepartments(d)
        setNextNo(no)
        if (r) {
          setRequest(r)
          setDeptId(String(r.departmentId))
          if (r.status !== 'PENDING') setError(`This request is already ${r.status.toLowerCase()}.`)
        }
      })
      .catch((e) => setError(e.message))
  }, [requestId])

  const dept = departments.find((d) => String(d.id) === deptId)

  // Accountable persons of the department; the department head is the default (UC-202 step 3).
  useEffect(() => {
    if (!deptId) return setPeople([])
    referenceApi.employees({ departmentId: deptId }).then((list) => {
      setPeople(list)
      const head = dept?.headName && list.find((p) => p.fullName.toLowerCase() === dept.headName.toLowerCase())
      setForm((f) => ({
        ...f,
        employeeId: head ? String(head.id) : list.length === 1 ? String(list[0].id) : '',
        receivedByTitle: dept?.headDesignation ? `${dept.headDesignation}, ${dept.code}` : dept?.code ?? '',
      }))
    }).catch(() => setPeople([]))
  }, [deptId]) // eslint-disable-line react-hooks/exhaustive-deps

  // Items in OPC storage, searchable.
  useEffect(() => {
    const t = setTimeout(() => {
      assetsApi.search({ status: 'IN_STORAGE', q: q || undefined, size: 100 })
        .then((r) => setStock(r.items)).catch((e) => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  const pickedRows = useMemo(() => {
    const byId = Object.fromEntries(stock.map((a) => [a.id, a]))
    return Object.entries(picked).map(([id, qty]) => ({ asset: byId[id] ?? pickedCache[id], qty: Number(qty) })).filter((p) => p.asset)
  }, [picked, stock])

  function toggle(a) {
    pickedCache[a.id] = a
    setPicked((p) => {
      const next = { ...p }
      if (next[a.id] !== undefined) delete next[a.id]
      else next[a.id] = String(a.quantity)
      return next
    })
  }

  const person = people.find((p) => String(p.id) === form.employeeId)
  const previewLines = pickedRows.map(({ asset, qty }) => ({
    id: asset.id, quantity: qty || 0, unit: asset.unit, description: asset.description,
    unitCost: asset.unitCost, totalCost: asset.unitCost == null ? null : Number(asset.unitCost) * (qty || 0),
  }))
  const distinct = (vals) => [...new Set(vals.filter(Boolean))].join(' / ') || null
  const previewIo = {
    ioNo: nextNo, issuedOn: todayIso(), departmentCode: dept?.code ?? '—', room: form.room || null,
    issuedByName: user.fullName, purchasedFrom: distinct(pickedRows.map((p) => p.asset.supplier)),
    invoiceNo: distinct(pickedRows.map((p) => p.asset.invoiceNo)),
    invoiceDate: pickedRows.map((p) => p.asset.acquisitionDate).filter(Boolean).sort()[0],
    remarks: form.remarks || null, notedByName: form.notedByName || null, notedByTitle: form.notedByTitle || null,
    receivedByName: person?.fullName, receivedByTitle: form.receivedByTitle || null,
  }

  async function confirm() {
    const errs = {}
    if (!deptId) errs.departmentId = 'Choose the department.'
    if (!form.employeeId) errs.employeeId = 'Choose the accountable person.'
    if (!pickedRows.length) errs.lines = 'Select at least one item.'
    pickedRows.forEach(({ asset, qty }) => {
      if (!(qty >= 1 && qty <= asset.quantity)) errs.lines = `Quantity for ${asset.description} must be 1 to ${asset.quantity}.`
    })
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      const io = await issueOrdersApi.prepare({
        requestId: request?.id ?? null, departmentId: Number(deptId), employeeId: Number(form.employeeId),
        room: form.room, remarks: form.remarks, notedByName: form.notedByName, notedByTitle: form.notedByTitle,
        receivedByTitle: form.receivedByTitle,
        lines: pickedRows.map(({ asset, qty }) => ({ assetId: asset.id, quantity: qty })),
      })
      setSaved(io)
    } catch (err) {
      if (err.status === 409 && err.details?.unavailable) {
        // UC-202 5a: remove items issued to someone else in the meantime, and tell the user.
        const gone = new Set(err.details.unavailable.map((u) => String(u.assetId)))
        setPicked((p) => Object.fromEntries(Object.entries(p).filter(([id]) => !gone.has(id))))
        setStock((s) => s.filter((a) => !gone.has(String(a.id))))
        setError(`${err.message} ${err.details.unavailable.map((u) => `${u.description} (${u.reason})`).join('; ')}`)
      } else {
        setErrors(err.fields || {})
        setError(err.message)
      }
    } finally {
      setBusy(false)
    }
  }

  const subtitle = request
    ? `${request.departmentCode}${request.prNo ? ` · ${request.prNo}` : ''} · items in OPC storage`
    : 'Issue items in OPC storage directly to a department'

  if (saved) {
    return (
      <>
        <PageHeader title={`Issue order ${saved.ioNo}`} subtitle={`${saved.departmentCode} · ${saved.accountable} · saved`} />
        <div className="prepare-layout">
          <Card>
            <Notice tone="ok">
              Issue order {saved.ioNo} saved. {saved.lines.length} item{saved.lines.length === 1 ? ' was' : 's were'} assigned
              to {saved.accountable} and recorded as issued, in one transaction.
            </Notice>
            <p style={{ margin: '16px 0 10px' }}>Print it for signing by the Property Custodian, the dean and the department head.</p>
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {saved.lines.map((l) => (
                <li key={l.id}>{l.assetId ? <Link to={`/assets/${l.assetId}`}>{l.description}</Link> : l.description} · {l.quantity} {l.unit}</li>
              ))}
            </ul>
            <div className="row" style={{ marginTop: 20 }}>
              <Button variant="secondary" onClick={() => navigate('/issue-orders')}>Back to requests</Button>
              <Button variant="secondary" onClick={() => navigate(`/issue-orders/${saved.id}`)}>Open issue order</Button>
            </div>
          </Card>
          <Card title={`Issue order ${saved.ioNo} · ready to print`}>
            <div style={{ marginTop: 12 }}><IoSheet io={saved} lines={saved.lines} /></div>
            <div className="row" style={{ marginTop: 16 }}>
              <Button onClick={() => openPdf(saved.id).catch((e) => setError(e.message))}>Print</Button>
              <Button variant="secondary" onClick={() => downloadPdf(saved.id, saved.ioNo).catch((e) => setError(e.message))}>
                Download PDF
              </Button>
            </div>
            {error && <Notice tone="error" style={{ marginTop: 12 }}>{error}</Notice>}
          </Card>
        </div>
      </>
    )
  }

  return (
    <>
      <PageHeader title="Prepare Issue Order" subtitle={subtitle} />
      <div className="prepare-layout">
        <Card>
          {request && (
            <div style={{ marginBottom: 16 }}>
              <span className="field-label">Requested</span>
              <ul className="requested">
                {request.items.map((i, k) => <li key={k}>{i.quantity} × {i.description}</li>)}
              </ul>
            </div>
          )}
          {!request && (
            <div style={{ marginBottom: 16 }}>
              <SelectField label="Department" required value={deptId} onChange={(e) => setDeptId(e.target.value)}
                           error={errors.departmentId} placeholder="Choose"
                           options={departments.filter((d) => d.code !== 'OPC').map((d) => ({ value: String(d.id), label: `${d.code} · ${d.name}` }))} />
            </div>
          )}

          <input className="field-input" placeholder="Search items in OPC storage" value={q}
                 onChange={(e) => setQ(e.target.value)} aria-label="Search items in storage" />
          <div className="pick-scroll" style={{ marginTop: 10 }}>
            <table className="table pick-table">
              <thead>
                <tr><th></th><th>Description</th><th style={{ width: 120 }}>Qty</th><th className="r">Unit cost</th></tr>
              </thead>
              <tbody>
                {stock.length === 0 && (
                  <tr><td colSpan={4} className="empty">No items in OPC storage{q ? ' match' : ''}.</td></tr>
                )}
                {stock.map((a) => {
                  const on = picked[a.id] !== undefined
                  return (
                    <tr key={a.id} className={on ? 'selected' : undefined}>
                      <td><input type="checkbox" checked={on} onChange={() => toggle(a)} aria-label={`Select ${a.description}`} /></td>
                      <td>
                        {a.description}
                        <div className="muted" style={{ fontSize: 13 }}>
                          {[a.propertyTagNo, a.supplier, a.invoiceNo && `Inv. ${a.invoiceNo}`, a.hasPhoto && 'photo'].filter(Boolean).join(' · ')}
                        </div>
                      </td>
                      <td>
                        {on && a.quantity > 1 ? (
                          <span className="row" style={{ gap: 6 }}>
                            <input className="field-input qty-input num" type="number" min="1" max={a.quantity}
                                   value={picked[a.id]} onChange={(e) => setPicked((p) => ({ ...p, [a.id]: e.target.value }))}
                                   aria-label={`Quantity of ${a.description}`} />
                            <span className="muted">/ {a.quantity}</span>
                          </span>
                        ) : (
                          <span className="num">{a.quantity} {a.unit}</span>
                        )}
                      </td>
                      <td className="num" style={{ textAlign: 'right' }}>{formatMoney(a.unitCost)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {errors.lines && <span className="field-error">{errors.lines}</span>}

          <div className="stack" style={{ gap: 14, marginTop: 18 }}>
            <SelectField label="Accountable person (department head)" required value={form.employeeId}
                         onChange={(e) => setForm((f) => ({ ...f, employeeId: e.target.value }))} error={errors.employeeId}
                         disabled={!deptId} placeholder={people.length ? 'Choose' : 'No persons in this department'}
                         options={people.map((p) => ({ value: String(p.id), label: p.fullName }))} />
            <Field label="Room / location" value={form.room} onChange={(e) => setForm((f) => ({ ...f, room: e.target.value }))}
                   placeholder="e.g. Phlebotomy Laboratory" />
            <div className="grid-2">
              <Field label="Noted by (dean)" value={form.notedByName}
                     onChange={(e) => setForm((f) => ({ ...f, notedByName: e.target.value }))} />
              <Field label="Title" value={form.notedByTitle}
                     onChange={(e) => setForm((f) => ({ ...f, notedByTitle: e.target.value }))} placeholder="e.g. Dean, CNAHS" />
            </div>
            <div className="grid-2">
              <Field label="Received by title" value={form.receivedByTitle}
                     onChange={(e) => setForm((f) => ({ ...f, receivedByTitle: e.target.value }))} placeholder="e.g. Chair, MedTech" />
              <Field label="Remarks" value={form.remarks} onChange={(e) => setForm((f) => ({ ...f, remarks: e.target.value }))} />
            </div>
            {error && <Notice tone="error">{error}</Notice>}
            <div className="row">
              <Button disabled={busy || (request && request.status !== 'PENDING')} onClick={confirm}>
                {busy ? 'Generating…' : 'Confirm and Generate'}
              </Button>
              <Button variant="secondary" onClick={() => navigate('/issue-orders')}>Cancel</Button>
            </div>
          </div>
        </Card>

        <Card title={`Issue order ${nextNo ?? ''} · preview`} className="side-panel"
              subtitle="The number is confirmed when you save.">
          {previewLines.length ? (
            <IoSheet io={previewIo} lines={previewLines} />
          ) : (
            <div className="io-sheet io-placeholder">Select items on the left to see the issue order.</div>
          )}
        </Card>
      </div>
    </>
  )
}

/** Keeps picked rows visible even when a new search hides them from the stock list. */
const pickedCache = {}
