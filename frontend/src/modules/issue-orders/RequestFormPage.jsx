import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { issueOrdersApi } from './issueOrdersApi'
import './issueOrders.css'

/** UI-201 New Department Request. */
export default function RequestFormPage() {
  const navigate = useNavigate()
  const [departments, setDepartments] = useState([])
  const [form, setForm] = useState({ departmentId: '', prNo: '', purpose: '', requestedBy: '' })
  const [items, setItems] = useState([{ description: '', quantity: '1' }])
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch((e) => setError(e.message))
  }, [])

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))
  const setItem = (i, k) => (e) => setItems((list) => list.map((it, j) => (j === i ? { ...it, [k]: e.target.value } : it)))

  async function submit(e) {
    e.preventDefault()
    const filled = items.filter((i) => i.description.trim())
    const errs = {}
    if (!form.departmentId) errs.departmentId = 'Choose the department.'
    if (!filled.length) errs.items = 'Add at least one item.'
    else if (filled.some((i) => !(Number(i.quantity) >= 1))) errs.items = 'Each quantity must be at least 1.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      await issueOrdersApi.createRequest({
        ...form, departmentId: Number(form.departmentId),
        items: filled.map((i) => ({ description: i.description, quantity: Number(i.quantity) })),
      })
      navigate('/issue-orders', { state: { flash: 'Request saved as Pending.' } })
    } catch (err) {
      setErrors(err.fields || {})
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="New Department Request" subtitle="Record what a department needs; link its purchase request" />
      <Card style={{ maxWidth: 1060 }}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="grid-2">
            <SelectField label="Department" required value={form.departmentId} onChange={set('departmentId')}
                         error={errors.departmentId} placeholder="Choose"
                         options={departments.map((d) => ({ value: String(d.id), label: `${d.code} · ${d.name}` }))} />
            <Field label="PR number" value={form.prNo} onChange={set('prNo')} placeholder="e.g. PR-1004MEDTEC-26-009330"
                   className="field-input num" />
          </div>
          <div className="grid-2">
            <Field label="Purpose" value={form.purpose} onChange={set('purpose')}
                   placeholder="e.g. To be installed in the BSMT laboratory" />
            <Field label="Requested by" value={form.requestedBy} onChange={set('requestedBy')} placeholder="Optional" />
          </div>

          <div className="field">
            <span className="field-label">Items needed *</span>
            <table className="table">
              <thead>
                <tr><th>Item</th><th style={{ width: 140 }}>Quantity</th><th style={{ width: 80 }}></th></tr>
              </thead>
              <tbody>
                {items.map((it, i) => (
                  <tr key={i}>
                    <td><input className="field-input" value={it.description} onChange={setItem(i, 'description')}
                               placeholder="e.g. Laboratory stool" aria-label={`Item ${i + 1}`} /></td>
                    <td><input className="field-input num" type="number" min="1" value={it.quantity}
                               onChange={setItem(i, 'quantity')} aria-label={`Quantity ${i + 1}`} /></td>
                    <td>
                      {items.length > 1 && (
                        <button type="button" className="link-btn"
                                onClick={() => setItems((l) => l.filter((_, j) => j !== i))}>Remove</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {errors.items && <span className="field-error">{errors.items}</span>}
            <div style={{ marginTop: 12 }}>
              <Button variant="secondary" onClick={() => setItems((l) => [...l, { description: '', quantity: '1' }])}>
                + Add item
              </Button>
            </div>
          </div>

          {error && <Notice tone="error">{error}</Notice>}
          <div className="row">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save Request'}</Button>
            <Button variant="secondary" onClick={() => navigate('/issue-orders')}>Cancel</Button>
          </div>
        </form>
      </Card>
    </>
  )
}
