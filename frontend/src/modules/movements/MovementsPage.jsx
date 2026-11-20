import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Notice, PageHeader, Pager } from '../../shared/ui'
import { formatDateTime } from '../../shared/format'
import { movementsApi, MOVEMENT_TYPES } from './movementsApi'
import './movements.css'

/**
 * Movements home: every recorded issue, transfer, condition change and turnover, newest first.
 * Transfers and turnovers start from an asset (UC-301/302 step 1: "the user opens an asset item").
 */
export default function MovementsPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const type = params.get('type') ?? ''
  const dept = params.get('dept') ?? ''
  const page = Number(params.get('page') ?? 0)
  const [departments, setDepartments] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch(() => {})
  }, [])

  useEffect(() => {
    movementsApi
      .list({ type: type || undefined, departmentId: dept || undefined, page, size: 25 })
      .then(setResult)
      .catch((e) => setError(e.message))
  }, [type, dept, page])

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v == null ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  return (
    <>
      <PageHeader
        title="Movements"
        subtitle="Every issue, transfer, condition change and turnover, each saved as one transaction"
        actions={<Button onClick={() => navigate('/movements/resignation')}>Resignation Turnover</Button>}
      />

      <Notice style={{ marginBottom: 22 }}>
        To transfer an item or record its condition or turnover, open it from the{' '}
        <Link to="/assets">Asset Register</Link> and use the buttons on its record.
      </Notice>

      <Card>
        <div className="search-filters" style={{ marginTop: 0, marginBottom: 16 }}>
          <select className="field-input" style={{ width: 'auto' }} value={type}
                  onChange={(e) => update({ type: e.target.value })} aria-label="Movement type">
            <option value="">All movements</option>
            {MOVEMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
          <select className="field-input" style={{ width: 'auto' }} value={dept}
                  onChange={(e) => update({ dept: e.target.value })} aria-label="Department">
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
          </select>
        </div>
        {error && <Notice tone="error" style={{ marginBottom: 14 }}>{error}</Notice>}
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Movement</th>
              <th>Item</th>
              <th>Qty</th>
              <th>Details</th>
              <th>Recorded by</th>
            </tr>
          </thead>
          <tbody>
            {result && result.items.length === 0 && (
              <tr><td colSpan={6} className="empty">No movements recorded yet.</td></tr>
            )}
            {result?.items.map((m) => (
              <tr key={m.id} className="clickable" onClick={() => navigate(`/assets/${m.assetId}`)}>
                <td className="num" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(m.at)}</td>
                <td className="move-type">{m.typeLabel}</td>
                <td>
                  {m.propertyTagNo && <span className="num">{m.propertyTagNo} · </span>}
                  {m.description}
                </td>
                <td className="num">{m.quantity} {m.unit}</td>
                <td>{m.summary}</td>
                <td>{m.recordedBy ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {result && (
          <Pager page={result.page} totalPages={result.totalPages} total={result.total} size={result.size}
                 onPage={(p) => update({ page: p })} />
        )}
      </Card>
    </>
  )
}
