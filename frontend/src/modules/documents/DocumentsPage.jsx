import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge, Button, Card, Field, Notice, PageHeader, Pager, SelectField } from '../../shared/ui'
import { dash, formatDate } from '../../shared/format'
import { DOC_TYPES, STATUS_LABELS, documentsApi } from './documentsApi'
import './documents.css'

/** Module 1 home: uploaded purchasing documents, newest first, with what still needs review. */
export default function DocumentsPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const filter = {
    status: params.get('status') ?? '', type: params.get('type') ?? '', q: params.get('q') ?? '',
    page: Number(params.get('page') ?? 0),
  }
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const t = setTimeout(() => {
      documentsApi.list({
        status: filter.status || undefined, type: filter.type || undefined, q: filter.q || undefined,
        page: filter.page, size: 25,
      }).then(setResult).catch((e) => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [filter.status, filter.type, filter.q, filter.page]) // eslint-disable-line react-hooks/exhaustive-deps

  function update(k, v) {
    const next = new URLSearchParams(params)
    if (v === '' || v == null) next.delete(k)
    else next.set(k, v)
    if (k !== 'page') next.delete('page')
    setParams(next)
  }

  return (
    <>
      <PageHeader title="Document Capture" subtitle="Sales invoices, purchase orders and purchase requests read by OCR"
                  actions={<Button onClick={() => navigate('/documents/upload')}>+ Upload Document</Button>} />
      <Card style={{ marginBottom: 22 }}>
        <div className="doc-filters">
          <Field label="Search" value={filter.q} placeholder="Number, PO, PR or supplier"
                 onChange={(e) => update('q', e.target.value)} />
          <SelectField label="Type" value={filter.type} onChange={(e) => update('type', e.target.value)} placeholder="All"
                       options={DOC_TYPES.map((t) => ({ value: t.value, label: t.label }))} />
          <SelectField label="Status" value={filter.status} onChange={(e) => update('status', e.target.value)} placeholder="All"
                       options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))} />
        </div>
      </Card>
      {error && <Notice tone="error" style={{ marginBottom: 18 }}>{error}</Notice>}
      <Card>
        <table className="table">
          <thead>
            <tr><th>Uploaded</th><th>Type</th><th>Number</th><th>PO · PR</th><th>Supplier</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {result && result.items.length === 0 && (
              <tr><td colSpan={7} className="empty">No documents yet. Upload a scanned sales invoice, PO or PR.</td></tr>
            )}
            {result?.items.map((d) => (
              <tr key={d.id} className="clickable" onClick={() => navigate(`/documents/${d.id}`)}>
                <td className="num">{formatDate(d.createdAt)}</td>
                <td>{d.typeLabel}</td>
                <td className="num"><strong>{d.docNo}</strong></td>
                <td className="num">{[d.poNo, d.prNo].filter(Boolean).join(' · ') || '—'}</td>
                <td>{dash(d.supplier)}</td>
                <td><StatusBadge status={d.status} /></td>
                <td><button className="link-btn">{d.status === 'READ' || d.status === 'FAILED' ? 'Review' : 'Open'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {result && (
          <Pager page={result.page} totalPages={result.totalPages} total={result.total} size={result.size}
                 onPage={(p) => update('page', String(p))} />
        )}
      </Card>
    </>
  )
}

export function StatusBadge({ status }) {
  const label = STATUS_LABELS[status] ?? status
  if (status === 'ENCODED') return <Badge>{label}</Badge>
  if (status === 'FAILED') return <Badge tone="bad">{label}</Badge>
  if (status === 'READ') return <Badge tone="warn">{label}</Badge>
  return <Badge muted>{label}</Badge>
}
