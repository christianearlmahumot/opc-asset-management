import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CONDITIONS } from '../assets/assetsApi'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader } from '../../shared/ui'
import { dash } from '../../shared/format'
import { movementsApi } from './movementsApi'
import './movements.css'

const STORAGE = 'storage'

/**
 * UI-303 Resignation Turnover. Started from HR's email notice, which is never stored in the system.
 * Each item is saved in its own transaction; a failed item stays listed as pending.
 */
export default function ResignationPage() {
  const navigate = useNavigate()
  const [people, setPeople] = useState([])
  const [departments, setDepartments] = useState([])
  const [personId, setPersonId] = useState('')
  const [items, setItems] = useState(null)
  const [rows, setRows] = useState({}) // assetId -> { to, condition }
  const [colleagues, setColleagues] = useState({}) // departmentId -> people
  const [markResigned, setMarkResigned] = useState(true)
  const [outcome, setOutcome] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([referenceApi.employees({}), referenceApi.departments()])
      .then(([p, d]) => { setPeople(p); setDepartments(d) })
      .catch((e) => setError(e.message))
  }, [])

  const deptCode = useMemo(() => Object.fromEntries(departments.map((d) => [d.id, d.code])), [departments])

  function loadItems(id) {
    setItems(null)
    setOutcome(null)
    setError('')
    if (!id) return
    movementsApi.heldBy(id)
      .then(async (list) => {
        setItems(list)
        setRows(Object.fromEntries(list.map((a) => [a.id, { to: '', condition: a.condition }])))
        const deptIds = [...new Set(list.map((a) => a.departmentId).filter(Boolean))]
        const entries = await Promise.all(deptIds.map((d) => referenceApi.employees({ departmentId: d }).then((p) => [d, p])))
        setColleagues(Object.fromEntries(entries))
      })
      .catch((e) => setError(e.message))
  }

  const setRow = (id, k) => (e) => setRows((r) => ({ ...r, [id]: { ...r[id], [k]: e.target.value } }))
  const allChosen = items?.length > 0 && items.every((a) => rows[a.id]?.to)

  async function complete() {
    setBusy(true)
    setError('')
    try {
      const res = await movementsApi.resignation({
        employeeId: Number(personId),
        markResigned,
        items: items.map((a) => ({
          assetId: a.id,
          toEmployeeId: rows[a.id].to === STORAGE ? null : Number(rows[a.id].to),
          condition: rows[a.id].condition,
        })),
      })
      setOutcome(res)
      const pendingIds = new Set(res.pending.map((p) => p.assetId))
      setItems((list) => list.filter((a) => pendingIds.has(a.id)))
      if (res.markedResigned) setPeople((list) => list.filter((p) => String(p.id) !== personId))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const person = people.find((p) => String(p.id) === personId)
  const pendingById = Object.fromEntries((outcome?.pending ?? []).map((p) => [p.assetId, p.message]))

  return (
    <>
      <PageHeader title="Resignation Turnover"
                  subtitle="Started from HR's email notice of resignation (the notice is not stored in the system)" />

      <Card style={{ marginBottom: 22 }}>
        <div className="grid-2">
          <Field label="Resigning accountable person">
            {(id) => (
              <select id={id} className="field-input" value={personId}
                      onChange={(e) => { setPersonId(e.target.value); loadItems(e.target.value) }}>
                <option value="">Choose a person</option>
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}{p.departmentId ? `, ${deptCode[p.departmentId] ?? ''}` : ''}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Status" readOnly
                 value={!personId ? '' : items === null ? 'Loading…'
                   : `${items.length} asset item${items.length === 1 ? '' : 's'} assigned`} />
        </div>
      </Card>

      {outcome && (
        <Notice tone={outcome.pending.length ? 'warn' : 'ok'} style={{ marginBottom: 22 }}>
          {outcome.completed} item{outcome.completed === 1 ? '' : 's'} turned over.
          {outcome.pending.length > 0 && ` ${outcome.pending.length} could not be saved and are still listed below.`}
          {outcome.stillHeld === 0 && ` ${person?.fullName ?? 'The person'} now holds no assets.`}
          {outcome.markedResigned && ' They are marked as Resigned.'}
        </Notice>
      )}
      {error && <Notice tone="error" style={{ marginBottom: 22 }}>{error}</Notice>}

      {personId && items && items.length === 0 && !outcome && (
        <Notice>{person?.fullName} holds no assets. Nothing needs to be turned over.</Notice>
      )}

      {items && items.length > 0 && (
        <Card>
          <table className="table">
            <thead>
              <tr>
                <th>Property tag</th>
                <th>Description</th>
                <th>Department · room</th>
                <th style={{ width: 230 }}>Reassign to</th>
                <th style={{ width: 190 }}>Condition</th>
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.id} className={pendingById[a.id] ? 'row-error' : undefined}>
                  <td className="num">{dash(a.propertyTagNo)}</td>
                  <td>
                    {a.description}{a.quantity > 1 && <span className="muted"> · {a.quantity} {a.unit}</span>}
                    {pendingById[a.id] && <div className="field-error">{pendingById[a.id]}</div>}
                  </td>
                  <td>{a.departmentCode}{a.room ? ` · ${a.room}` : ''}</td>
                  <td>
                    <select className="field-input" value={rows[a.id]?.to ?? ''} onChange={setRow(a.id, 'to')}
                            aria-label={`Reassign ${a.description}`}>
                      <option value="">Choose</option>
                      {(colleagues[a.departmentId] ?? []).filter((p) => String(p.id) !== personId).map((p) => (
                        <option key={p.id} value={p.id}>{p.fullName}</option>
                      ))}
                      <option value={STORAGE}>OPC storage</option>
                    </select>
                  </td>
                  <td>
                    <select className="field-input" value={rows[a.id]?.condition} onChange={setRow(a.id, 'condition')}
                            aria-label={`Condition of ${a.description}`}>
                      {CONDITIONS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <label className="row" style={{ gap: 8, marginTop: 16 }}>
            <input type="checkbox" checked={markResigned} onChange={(e) => setMarkResigned(e.target.checked)} />
            Mark {person?.fullName ?? 'the person'} as Resigned once they hold no assets
          </label>
          <div className="row" style={{ marginTop: 18 }}>
            <Button disabled={busy || !allChosen} onClick={complete}>{busy ? 'Saving…' : 'Complete Turnover'}</Button>
            <Button variant="secondary" onClick={() => navigate('/movements')}>Cancel</Button>
            {!allChosen && <span className="muted">Choose where each item goes.</span>}
          </div>
        </Card>
      )}
    </>
  )
}
