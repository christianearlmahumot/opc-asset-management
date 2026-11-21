import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, Card, Notice, PageHeader } from '../../shared/ui'
import { dash, formatDateTime } from '../../shared/format'
import IoSheet from './IoSheet'
import { downloadPdf, issueOrdersApi, openPdf } from './issueOrdersApi'
import './issueOrders.css'

/** UC-204: one past issue order, ready to reprint. */
export default function IssueOrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [io, setIo] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    issueOrdersApi.get(id).then(setIo).catch((e) => setError(e.message))
  }, [id])

  if (error && !io) return <Notice tone="error">{error}</Notice>
  if (!io) return <p className="muted">Loading…</p>

  return (
    <>
      <PageHeader
        title={`Issue order ${io.ioNo}`}
        subtitle={`${io.departmentCode} · ${io.accountable ?? '—'} · issued ${io.issuedOn}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate(-1)}>Back</Button>
            <Button variant="secondary" onClick={() => downloadPdf(io.id, io.ioNo).catch((e) => setError(e.message))}>Download PDF</Button>
            <Button onClick={() => openPdf(io.id).catch((e) => setError(e.message))}>Print</Button>
          </>
        }
      />
      {error && <Notice tone="error" style={{ marginBottom: 18 }}>{error}</Notice>}
      <div className="prepare-layout">
        <Card title="Issue order"><div style={{ marginTop: 12 }}><IoSheet io={io} lines={io.lines} /></div></Card>
        <Card title="Items released" subtitle="Open an item to see its record and history">
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {io.lines.map((l) => (
              <li key={l.id} style={{ marginBottom: 8 }}>
                {l.assetId ? <Link to={`/assets/${l.assetId}`}>{l.description}</Link> : l.description}
                <span className="muted"> · {l.quantity} {l.unit}</span>
              </li>
            ))}
          </ul>
          <dl className="dl" style={{ marginTop: 18 }}>
            <div><dt>Room</dt><dd>{dash(io.room)}</dd></div>
            <div><dt>Issued by</dt><dd>{dash(io.issuedByName)}</dd></div>
            <div><dt>Saved</dt><dd>{formatDateTime(io.createdAt)}</dd></div>
            <div><dt>Request</dt><dd>{io.requestId ? `#${io.requestId}` : 'Issued without request'}</dd></div>
          </dl>
        </Card>
      </div>
    </>
  )
}
