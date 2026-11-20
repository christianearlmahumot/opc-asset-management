import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Notice, PageHeader, Pager } from '../../shared/ui'
import { dash } from '../../shared/format'
import AssetTabs from './AssetTabs'
import { assetsApi, CONDITIONS, STATUSES } from './assetsApi'
import { ConditionBadge, HistoryList, StatusBadge } from './parts'
import './assets.css'

const PAGE_SIZE = 25

/** UI-205 Search Asset and View History. Filters live in the URL so Back returns to the same results. */
export default function AssetSearchPage() {
  const { user, can } = useAuth()
  const navigate = useNavigate()
  const isHead = user.role === 'DEPT_HEAD'
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const departmentId = params.get('dept') ?? ''
  const condition = params.get('condition') ?? ''
  const status = params.get('status') ?? ''
  const page = Number(params.get('page') ?? 0)

  const [text, setText] = useState(q)
  const [departments, setDepartments] = useState([])
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState({ loading: false, entries: [] })

  useEffect(() => {
    if (!isHead) referenceApi.departments().then(setDepartments).catch(() => {})
  }, [isHead])

  useEffect(() => {
    setLoading(true)
    setError('')
    assetsApi
      .search({ q: q || undefined, departmentId: departmentId || undefined, condition: condition || undefined,
        status: status || undefined, includeDrafts: !isHead, page, size: PAGE_SIZE })
      .then(setResult)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [q, departmentId, condition, status, page, isHead])

  const update = useCallback((changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null || v === undefined ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
    setSelected(null)
  }, [params, setParams])

  function select(row) {
    setSelected(row)
    setHistory({ loading: true, entries: [] })
    assetsApi.history(row.id)
      .then((entries) => setHistory({ loading: false, entries }))
      .catch(() => setHistory({ loading: false, entries: [] }))
  }

  const items = result?.items ?? []

  return (
    <>
      <PageHeader title="Asset Search" subtitle="Search by keyword, property tag, serial number or accountable person"
                  actions={can(['ADMIN', 'STAFF', 'NAS']) && <Button onClick={() => navigate('/assets/new')}>+ Add Asset</Button>} />
      <AssetTabs />

      <Card style={{ marginBottom: 22 }}>
        <form className="search-bar" onSubmit={(e) => { e.preventDefault(); update({ q: text.trim() }) }}>
          <input className="field-input" value={text} onChange={(e) => setText(e.target.value)}
                 placeholder="e.g. printer, 2018-PCO-PRNT-01, VGFK140014" aria-label="Search assets" autoFocus />
          <Button type="submit">Search</Button>
        </form>
        <div className="search-filters">
          {!isHead && (
            <select className="field-input" value={departmentId} onChange={(e) => update({ dept: e.target.value })}
                    aria-label="Department">
              <option value="">All departments</option>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
            </select>
          )}
          <select className="field-input" value={condition} onChange={(e) => update({ condition: e.target.value })}
                  aria-label="Condition">
            <option value="">Any condition</option>
            {CONDITIONS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
          <select className="field-input" value={status} onChange={(e) => update({ status: e.target.value })}
                  aria-label="Status">
            <option value="">Any status</option>
            {STATUSES.filter((s) => !isHead || s.value !== 'DRAFT').map((s) => (
              <option key={s.value} value={s.value}>{s.label === 'Draft' ? 'Drafts awaiting confirmation' : s.label}</option>
            ))}
          </select>
          {(q || departmentId || condition || status) && (
            <button className="link-btn" onClick={() => { setText(''); setParams(new URLSearchParams()) }}>Clear</button>
          )}
        </div>
      </Card>

      <div className={`split${selected ? ' with-panel' : ''}`}>
        <Card>
          {error && <Notice tone="error" style={{ marginBottom: 14 }}>{error}</Notice>}
          <table className="table asset-table">
            <thead>
              <tr>
                <th>Property tag</th>
                <th>Description</th>
                <th>Serial no.</th>
                <th>Qty</th>
                <th>Department · room</th>
                <th>Accountable</th>
                <th>Condition</th>
              </tr>
            </thead>
            <tbody>
              {loading && !items.length ? (
                <tr><td colSpan={7} className="empty">Searching…</td></tr>
              ) : !items.length ? (
                <tr><td colSpan={7} className="empty">
                  No assets found.{q && ' Check the tag or serial number.'}
                </td></tr>
              ) : items.map((a) => (
                <tr key={a.id} className={`clickable${selected?.id === a.id ? ' selected' : ''}`}
                    onClick={() => select(a)} onDoubleClick={() => navigate(`/assets/${a.id}`)}>
                  <td className="tag">{dash(a.propertyTagNo)}</td>
                  <td>
                    {a.description}
                    {a.status === 'DRAFT' && <> <StatusBadge status="DRAFT" /></>}
                  </td>
                  <td className="num">{dash(a.serialNo)}</td>
                  <td className="num">{a.quantity} {a.unit}</td>
                  <td>{a.departmentCode ? `${a.departmentCode}${a.room ? ` · ${a.room}` : ''}` : (a.room ? `OPC storage · ${a.room}` : 'OPC storage')}</td>
                  <td>{dash(a.accountable)}</td>
                  <td><ConditionBadge condition={a.condition} label={a.conditionLabel} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {result && (
            <Pager page={result.page} totalPages={result.totalPages} total={result.total} size={result.size}
                   onPage={(p) => update({ page: p })} />
          )}
        </Card>

        {selected && (
          <Card className="side-panel">
            <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h2 className="card-title">{selected.propertyTagNo || selected.description} history</h2>
                <p className="card-sub">Newest first</p>
              </div>
              <button className="link-btn" onClick={() => setSelected(null)}>Close</button>
            </div>
            <HistoryList loading={history.loading} entries={history.entries} />
            <Link to={`/assets/${selected.id}`} style={{ fontWeight: 600 }}>Open full record →</Link>
          </Card>
        )}
      </div>
    </>
  )
}
