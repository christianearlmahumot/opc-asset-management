import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, Card, Field, Notice, PageHeader } from '../../shared/ui'
import { dash, formatDate, formatDateTime, formatMoney } from '../../shared/format'
import { movementsApi, today } from '../movements/movementsApi'
import { assetsApi } from './assetsApi'
import { AssetPhoto, ConditionBadge, HistoryList, StatusBadge } from './parts'
import './assets.css'

/** SDD 2.4 AssetDetailPage: asset details, photo and movement history timeline. */
export default function AssetDetailPage() {
  const { id } = useParams()
  const { user, can } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [asset, setAsset] = useState(null)
  const [history, setHistory] = useState({ loading: true, entries: [] })
  const [error, setError] = useState('')
  const [flash, setFlash] = useState(location.state?.flash ?? '')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setError('')
    setFlash(location.state?.flash ?? '')
    assetsApi.get(id).then(setAsset).catch((e) => setError(e.message))
    assetsApi.history(id)
      .then((entries) => setHistory({ loading: false, entries }))
      .catch(() => setHistory({ loading: false, entries: [] }))
  }, [id])

  async function confirmDraft() {
    setBusy(true)
    try {
      setAsset(await assetsApi.confirm(id))
      setHistory({ loading: false, entries: await assetsApi.history(id) })
      setFlash('Draft confirmed. The asset is now in the register.')
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  if (error && !asset) return <Notice tone="error">{error}</Notice>
  if (!asset) return <p className="muted">Loading…</p>

  const isDraft = asset.status === 'DRAFT'
  const canMove = can(['ADMIN', 'STAFF']) && !isDraft && asset.status !== 'DISPOSED'
  const canDispose = can(['ADMIN', 'STAFF']) && (asset.status === 'TURNED_OVER' || asset.status === 'IN_STORAGE')
  const canEdit = asset.status !== 'DISPOSED' && (can(['ADMIN', 'STAFF']) || (user.role === 'NAS' && isDraft))
  const where = asset.departmentCode
    ? `${asset.departmentCode}${asset.departmentName ? ` · ${asset.departmentName}` : ''}`
    : 'OPC storage'

  return (
    <>
      <PageHeader
        title={asset.propertyTagNo ?? 'Asset without tag'}
        subtitle={`${asset.description} · ${asset.quantity} ${asset.unit}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate(-1)}>Back</Button>
            {isDraft && can(['ADMIN', 'STAFF']) && (
              <Button disabled={busy} onClick={confirmDraft}>Confirm draft</Button>
            )}
            {canMove && asset.status === 'ISSUED' && (
              <Button variant="secondary" onClick={() => navigate(`/movements/transfer/${asset.id}`)}>Transfer</Button>
            )}
            {canMove && (
              <Button variant="secondary" onClick={() => navigate(`/movements/condition/${asset.id}`)}>
                {asset.status === 'ISSUED' ? 'Condition / Turnover' : 'Record condition'}
              </Button>
            )}
            {canEdit && <Button onClick={() => navigate(`/assets/${asset.id}/edit`)}>Edit</Button>}
          </>
        }
      />

      {flash && <Notice tone="ok" style={{ marginBottom: 18 }}>{flash}</Notice>}
      {error && <Notice tone="error" style={{ marginBottom: 18 }}>{error}</Notice>}
      {isDraft && (
        <Notice style={{ marginBottom: 18 }}>
          This is a draft encoded by a NAS Assistant. It is not in the register until Property Staff confirm it.
        </Notice>
      )}

      <div className="asset-detail-layout">
        <div className="stack" style={{ gap: 22 }}>
          <Card title="Property Inventory Slip">
            <dl className="dl" style={{ marginTop: 14 }}>
              <Item label="Property tag no." value={dash(asset.propertyTagNo)} num />
              <Item label="Serial no." value={dash(asset.serialNo)} num />
              <Item label="Description" value={asset.description} wide />
              <Item label="Category" value={dash(asset.categoryName)} />
              <Item label="Brand · model" value={[asset.brand, asset.modelNo].filter(Boolean).join(' · ') || '—'} />
              <Item label="Color" value={dash(asset.color)} />
              <Item label="Dimension" value={dash(asset.dimension)} />
              <Item label="Quantity" value={`${asset.quantity} ${asset.unit}`} num />
              <Item label="Date acquired" value={formatDate(asset.acquisitionDate)} num />
              <Item label="Acquisition unit cost" value={`₱ ${formatMoney(asset.unitCost)}`} num />
              <Item label="Total cost" value={`₱ ${formatMoney(asset.totalCost)}`} num />
              <Item label="Supplier" value={dash(asset.supplier)} />
              <Item label="Invoice no." value={dash(asset.invoiceNo)} num />
              <Item label="Issue order no." num value={asset.issueOrderNo
                ? (can(['ADMIN', 'STAFF']) ? <Link to={`/issue-orders/${asset.issueOrderId}`}>{asset.issueOrderNo}</Link> : asset.issueOrderNo)
                : '—'} />
            </dl>
          </Card>

          <Card title="Custody and condition">
            <dl className="dl" style={{ marginTop: 14 }}>
              <Item label="Department" value={where} />
              <Item label="Room / location" value={dash(asset.room)} />
              <Item label="Accountable person" value={dash(asset.accountable)} />
              <Item label="Condition" value={<ConditionBadge condition={asset.condition} label={asset.conditionLabel} />} />
              <Item label="Status" value={<StatusBadge status={asset.status} label={asset.statusLabel} />} />
              {asset.parentAssetId && (
                <Item label="Split from" value={<Link to={`/assets/${asset.parentAssetId}`}>Original item</Link>} />
              )}
            </dl>
          </Card>

          {canDispose && (
            <DisposalCard asset={asset} onDone={(updated, entries) => {
              setAsset(updated)
              setHistory({ loading: false, entries })
              setFlash('Disposal recorded.')
            }} />
          )}
        </div>

        <div className="stack side-panel" style={{ gap: 22 }}>
          {asset.hasPhoto && (
            <Card>
              <AssetPhoto assetId={asset.id} version={asset.updatedAt} alt={asset.description} />
            </Card>
          )}
          <Card title="History" subtitle="Newest first">
            <HistoryList loading={history.loading} entries={history.entries} />
            <p className="muted" style={{ fontSize: 13, margin: '12px 0 0' }}>
              Encoded by {dash(asset.createdByName)} on {formatDateTime(asset.createdAt)}
            </p>
          </Card>
        </div>
      </div>
    </>
  )
}

/** UC-302 step 6: when OPC disposes of a turned-over item, the disposal date is recorded. */
function DisposalCard({ asset, onDone }) {
  const [open, setOpen] = useState(false)
  const [date, setDate] = useState(today())
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (!date) return setError('Enter the disposal date.')
    setBusy(true)
    setError('')
    try {
      await movementsApi.disposal({ assetId: asset.id, disposalDate: date, note })
      const [updated, entries] = await Promise.all([assetsApi.get(asset.id), assetsApi.history(asset.id)])
      onDone(updated, entries)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Disposal" subtitle="Record the date when OPC disposes of this item. It then leaves the active register.">
      {!open ? (
        <Button variant="secondary" onClick={() => setOpen(true)}>Record disposal date</Button>
      ) : (
        <form className="stack" style={{ gap: 14 }} onSubmit={submit} noValidate>
          <div className="grid-2">
            <Field label="Disposal date" required type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
            <Field label="Note" value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Sold as scrap" />
          </div>
          {error && <Notice tone="error">{error}</Notice>}
          <div className="row">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Confirm disposal'}</Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          </div>
        </form>
      )}
    </Card>
  )
}

function Item({ label, value, wide, num }) {
  return (
    <div style={wide ? { gridColumn: 'span 2' } : undefined}>
      <dt>{label}</dt>
      <dd className={num ? 'num' : undefined}>{value}</dd>
    </div>
  )
}
