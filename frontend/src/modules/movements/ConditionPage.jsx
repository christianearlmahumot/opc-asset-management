import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { assetsApi, CONDITIONS } from '../assets/assetsApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { movementsApi, REASONS, today } from './movementsApi'
import './movements.css'

/** UI-302 Record Condition or Turnover. */
export default function ConditionPage() {
  const { assetId } = useParams()
  const navigate = useNavigate()
  const [asset, setAsset] = useState(null)
  const [form, setForm] = useState({
    condition: 'DEFECTIVE', action: 'KEEP', reason: 'DEFECTIVE', note: '', dateReturned: today(),
    turnedOverBy: '', quantity: '',
  })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    assetsApi.get(assetId)
      .then((a) => {
        setAsset(a)
        setForm((f) => ({ ...f, quantity: String(a.quantity), action: a.status === 'ISSUED' ? 'TURN_OVER' : 'KEEP' }))
      })
      .catch((e) => setError(e.message))
  }, [assetId])

  const set = (k) => (e) => {
    const v = e.target.value
    setForm((f) => {
      const next = { ...f, [k]: v }
      // Keep the reason in step with the condition until the user picks a reason themselves.
      if (k === 'condition' && (v === 'DEFECTIVE' || v === 'DAMAGED') && ['DEFECTIVE', 'DAMAGED'].includes(f.reason)) {
        next.reason = v
      }
      return next
    })
    setErrors((x) => ({ ...x, [k]: undefined }))
  }

  if (error && !asset) return <Notice tone="error">{error}</Notice>
  if (!asset) return <p className="muted">Loading…</p>

  const canTurnOver = asset.status === 'ISSUED'
  const turningOver = form.action === 'TURN_OVER'
  const qty = Number(form.quantity)
  const split = qty >= 1 && qty < asset.quantity

  async function submit(e) {
    e.preventDefault()
    const errs = {}
    if (!(qty >= 1 && qty <= asset.quantity)) errs.quantity = `From 1 to ${asset.quantity}.`
    if (turningOver) {
      if (form.reason === 'OTHER' && !form.note.trim()) errs.note = 'Describe the reason.'
      if (!form.dateReturned) errs.dateReturned = 'Enter the date returned.'
      if (!form.turnedOverBy.trim()) errs.turnedOverBy = 'Enter who turned it over.'
    } else if (form.condition === asset.condition && !split) {
      errs.condition = `The condition is already ${asset.conditionLabel}.`
    }
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      const r = await movementsApi.turnover({
        assetId: asset.id, condition: form.condition, action: form.action, quantity: qty,
        reason: turningOver ? form.reason : null, note: form.note,
        dateReturned: turningOver ? form.dateReturned : null, turnedOverBy: turningOver ? form.turnedOverBy : null,
      })
      navigate(`/assets/${r.assetId}`, {
        state: { flash: turningOver
          ? `Turned over to OPC storage${r.split ? ` (${qty} ${asset.unit} split off as this new item)` : ''}. The disposal date can be recorded later.`
          : `Condition recorded${r.split ? ` for ${qty} ${asset.unit}, split off as this new item` : ''}.` },
      })
    } catch (err) {
      setErrors(err.fields || {})
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const where = [asset.departmentCode ?? 'OPC storage', asset.room].filter(Boolean).join(' · ')

  return (
    <>
      <PageHeader title="Record Condition or Turnover"
                  subtitle={`${asset.propertyTagNo ?? 'No tag'} · ${asset.description} · ${where}`} />
      <div className="condition-layout">
        <Card>
          <form className="stack" style={{ gap: 16 }} onSubmit={submit} noValidate>
            <SelectField label="New condition" required value={form.condition} onChange={set('condition')}
                         error={errors.condition} options={CONDITIONS}
                         hint={`Currently ${asset.conditionLabel}.`} />

            <div className="field">
              <span className="field-label">Action *</span>
              <div className="choice-row">
                <label className="choice">
                  <input type="radio" name="action" value="KEEP" checked={form.action === 'KEEP'} onChange={set('action')} />
                  {canTurnOver ? 'Keep in department (still used)' : 'Record condition only'}
                </label>
                {canTurnOver && (
                  <label className="choice">
                    <input type="radio" name="action" value="TURN_OVER" checked={turningOver} onChange={set('action')} />
                    Turn over to OPC storage
                  </label>
                )}
              </div>
            </div>

            {asset.quantity > 1 && (
              <Field label="Quantity" required type="number" min="1" max={asset.quantity} value={form.quantity}
                     onChange={set('quantity')} error={errors.quantity} className="field-input num"
                     hint={`of ${asset.quantity} ${asset.unit}. A smaller number splits those off as a new item.`} />
            )}

            {turningOver && (
              <>
                <div className="grid-2">
                  <SelectField label="Reason" required value={form.reason} onChange={set('reason')} options={REASONS} />
                  <Field label="Date returned" required type="date" value={form.dateReturned} max={today()}
                         onChange={set('dateReturned')} error={errors.dateReturned} />
                </div>
                <Field label="Turned over by" required value={form.turnedOverBy} onChange={set('turnedOverBy')}
                       error={errors.turnedOverBy} placeholder="e.g. Lab technician, ME" />
              </>
            )}
            <Field label={form.reason === 'OTHER' && turningOver ? 'Note *' : 'Note'} value={form.note}
                   onChange={set('note')} error={errors.note} />

            {turningOver && (
              <Notice>
                {split ? `${qty} ${asset.unit} move` : 'The item moves'} to OPC storage. Its disposal date can be recorded later.
              </Notice>
            )}
            {error && <Notice tone="error">{error}</Notice>}
            <div className="row">
              <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Confirm'}</Button>
              <Button variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
            </div>
          </form>
        </Card>
        <Card title="Reasons" subtitle="Standard list replacing free-text remarks">
          <ul className="reason-list">
            {REASONS.map((r) => <li key={r.value}>{r.label}</li>)}
          </ul>
        </Card>
      </div>
    </>
  )
}
