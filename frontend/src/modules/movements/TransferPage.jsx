import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { assetsApi } from '../assets/assetsApi'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { movementsApi } from './movementsApi'
import './movements.css'

/** UI-301 Transfer Asset: source on the left, destination on the right, one transaction. */
export default function TransferPage() {
  const { assetId } = useParams()
  const navigate = useNavigate()
  const [asset, setAsset] = useState(null)
  const [departments, setDepartments] = useState([])
  const [people, setPeople] = useState([])
  const [form, setForm] = useState({ toDepartmentId: '', toRoom: '', toEmployeeId: '', quantity: '', reason: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([assetsApi.get(assetId), referenceApi.departments()])
      .then(([a, d]) => {
        setAsset(a)
        setDepartments(d)
        setForm((f) => ({ ...f, toDepartmentId: a.departmentId ? String(a.departmentId) : '', quantity: String(a.quantity) }))
      })
      .catch((e) => setError(e.message))
  }, [assetId])

  useEffect(() => {
    if (!form.toDepartmentId) return setPeople([])
    referenceApi.employees({ departmentId: form.toDepartmentId }).then(setPeople).catch(() => setPeople([]))
  }, [form.toDepartmentId])

  const set = (k) => (e) => {
    const v = e.target.value
    setForm((f) => ({ ...f, [k]: v, ...(k === 'toDepartmentId' ? { toEmployeeId: '' } : {}) }))
    setErrors((x) => ({ ...x, [k]: undefined }))
  }

  if (error && !asset) return <Notice tone="error">{error}</Notice>
  if (!asset) return <p className="muted">Loading…</p>

  if (asset.status !== 'ISSUED') {
    return (
      <>
        <PageHeader title="Transfer Asset" subtitle={`${asset.propertyTagNo ?? 'No tag'} · ${asset.description}`} />
        <Notice>
          Only items assigned to a department can be transferred. This item is {asset.statusLabel.toLowerCase()}.
          {asset.status === 'IN_STORAGE' && ' Items in OPC storage are released with an issue order.'}
        </Notice>
        <div style={{ marginTop: 16 }}><Button variant="secondary" onClick={() => navigate(-1)}>Back</Button></div>
      </>
    )
  }

  const qty = Number(form.quantity)
  const split = qty >= 1 && qty < asset.quantity
  const destName = departments.find((d) => String(d.id) === form.toDepartmentId)?.code

  async function submit(e) {
    e.preventDefault()
    const errs = {}
    if (!form.toDepartmentId) errs.toDepartmentId = 'Choose the department.'
    if (!form.toRoom.trim()) errs.toRoom = 'Enter the room.'
    if (!form.toEmployeeId) errs.toEmployeeId = 'Choose the accountable person.'
    if (!(qty >= 1 && qty <= asset.quantity)) errs.quantity = `From 1 to ${asset.quantity}.`
    if (!form.reason.trim()) errs.reason = 'Enter the reason.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      const r = await movementsApi.transfer({
        assetId: asset.id, toDepartmentId: Number(form.toDepartmentId), toRoom: form.toRoom,
        toEmployeeId: Number(form.toEmployeeId), quantity: qty, reason: form.reason,
      })
      navigate(`/assets/${r.assetId}`, {
        state: { flash: r.split
          ? `${qty} ${asset.unit} split off and transferred in one transaction. This is the new item; ${asset.quantity - qty} ${asset.unit} stay with the original.`
          : 'Transfer saved.' },
      })
    } catch (err) {
      setErrors(err.fields || {})
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Transfer Asset"
                  subtitle={`${asset.propertyTagNo ?? 'No tag'} · ${asset.description} · ${asset.quantity} ${asset.unit}`} />
      <form onSubmit={submit} noValidate>
        <div className="transfer-sides">
          <Card title="From (source)">
            <div className="stack" style={{ gap: 14, marginTop: 12 }}>
              <Field label="Department" value={asset.departmentCode ?? ''} readOnly />
              <Field label="Room" value={asset.room ?? '—'} readOnly />
              <Field label="Accountable person" value={asset.accountable ?? '—'} readOnly />
            </div>
          </Card>
          <span className="transfer-arrow" aria-hidden="true">→</span>
          <Card title="To (destination)">
            <div className="stack" style={{ gap: 14, marginTop: 12 }}>
              <SelectField label="Department" required value={form.toDepartmentId} onChange={set('toDepartmentId')}
                           error={errors.toDepartmentId} placeholder="Choose"
                           options={departments.filter((d) => d.code !== 'OPC' || d.id === asset.departmentId)
                             .map((d) => ({ value: String(d.id), label: d.code }))} />
              <Field label="Room" required value={form.toRoom} onChange={set('toRoom')} error={errors.toRoom}
                     placeholder="e.g. Storage Room" />
              <SelectField label="Accountable person" required value={form.toEmployeeId} onChange={set('toEmployeeId')}
                           error={errors.toEmployeeId} disabled={!form.toDepartmentId}
                           placeholder={people.length ? 'Choose' : 'No persons in this department'}
                           options={people.map((p) => ({ value: String(p.id), label: p.fullName }))} />
            </div>
          </Card>
        </div>

        <Card>
          <div className="grid-2">
            <Field label="Quantity to move" required type="number" min="1" max={asset.quantity} value={form.quantity}
                   onChange={set('quantity')} error={errors.quantity} className="field-input num"
                   hint={`of ${asset.quantity} ${asset.unit}`} />
            <Field label="Reason" required value={form.reason} onChange={set('reason')} error={errors.reason}
                   placeholder="e.g. Moved for storage-room work station" />
          </div>
          {split && (
            <Notice style={{ marginTop: 18 }}>
              {qty} {asset.unit} are split off as a new item and moved in one transaction; {asset.quantity - qty}{' '}
              {asset.unit} stay in {asset.room ?? asset.departmentCode}.
            </Notice>
          )}
          {!split && qty === asset.quantity && destName && (
            <p className="muted" style={{ margin: '16px 0 0' }}>The whole item moves to {destName}.</p>
          )}
          {error && <Notice tone="error" style={{ marginTop: 16 }}>{error}</Notice>}
          <div className="row" style={{ marginTop: 18 }}>
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Confirm Transfer'}</Button>
            <Button variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
          </div>
        </Card>
      </form>
    </>
  )
}
