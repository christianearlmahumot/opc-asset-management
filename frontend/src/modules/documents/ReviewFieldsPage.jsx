import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader } from '../../shared/ui'
import { formatMoney, parseMoney } from '../../shared/format'
import { StatusBadge } from './DocumentsPage'
import { DOC_TYPES, LOW_CONFIDENCE, documentsApi } from './documentsApi'
import './documents.css'

let keySeq = 1
const newKey = () => `k${keySeq++}`
const low = (conf, key) => conf && conf[key] != null && conf[key] < LOW_CONFIDENCE

/**
 * UI-102 Review and Confirm Extracted Fields (SDD 1.2 ReviewFieldsPage): the scan on the left, the
 * pre-filled header and item lines on the right. Fields the OCR was unsure of are highlighted; the
 * user checks every field, adds serial numbers for items tracked one by one, and confirms.
 */
export default function ReviewFieldsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [doc, setDoc] = useState(null)
  const [header, setHeader] = useState(null)
  const [lines, setLines] = useState([])
  const [lists, setLists] = useState({ categories: [], units: [] })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [duplicates, setDuplicates] = useState(null)
  const [busy, setBusy] = useState(false)
  const loadedFor = useRef('')

  const load = useCallback(() => documentsApi.get(id).then(setDoc).catch((e) => setError(e.message)), [id])

  useEffect(() => {
    load()
    Promise.all([referenceApi.categories(), referenceApi.units()])
      .then(([categories, units]) => setLists({ categories, units }))
      .catch((e) => setError(e.message))
  }, [load])

  // While the server is reading the document, check again every 2 seconds.
  useEffect(() => {
    if (!doc || (doc.status !== 'QUEUED' && doc.status !== 'PROCESSING')) return undefined
    const t = setTimeout(load, 2000)
    return () => clearTimeout(t)
  }, [doc, load])

  // Fill the form once per reading (and again after "Read again").
  useEffect(() => {
    if (!doc || (doc.status !== 'READ' && doc.status !== 'FAILED')) return
    const stamp = `${doc.id}:${doc.status}:${doc.lines.map((l) => l.id).join(',')}`
    if (loadedFor.current === stamp || !lists.units.length) return
    loadedFor.current = stamp
    setHeader({
      docNo: doc.docNo ?? '', supplier: doc.supplier ?? '', docDate: doc.docDate ?? '', poNo: doc.poNo ?? '',
      prNo: doc.prNo ?? '', departmentCode: doc.departmentCode ?? '',
    })
    const fromOcr = doc.lines.filter((l) => !l.confirmed).map((l) => toForm(l, lists))
    setLines(fromOcr.length ? fromOcr : [blankLine()])   // UC-102 1a: empty lines for manual encoding
    setErrors({})
    setDuplicates(null)
  }, [doc, lists])

  const setH = (k) => (e) => setHeader((h) => ({ ...h, [k]: e.target.value }))
  const setL = (key, k, v) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, [k]: v } : l)))

  function split(key) {
    setLines((ls) => ls.flatMap((l) => {
      if (l.key !== key) return [l]
      const n = Math.min(Number(l.quantity) || 1, 200)
      return Array.from({ length: n }, () => ({ ...l, key: newKey(), lineId: null, quantity: '1', more: true }))
    }))
  }

  async function readAgain() {
    if (!window.confirm('Read the document again? Lines you have not saved are replaced by a new reading.')) return
    setBusy(true)
    try {
      setDoc(await documentsApi.readAgain(id))
      loadedFor.current = ''
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function confirm(confirmDuplicates = false) {
    setBusy(true)
    setError('')
    setErrors({})
    try {
      const body = {
        header: { ...header, docDate: header.docDate || null },
        confirmDuplicates,
        lines: lines.map((l) => ({
          lineId: l.lineId, quantity: l.quantity === '' ? null : Number(l.quantity), unit: l.unit || null,
          description: l.description, unitCost: parseMoney(l.unitCost) || null,
          categoryId: l.categoryId ? Number(l.categoryId) : null, propertyTagNo: l.propertyTagNo, brand: l.brand,
          modelNo: l.modelNo, serialNo: l.serialNo, color: l.color, dimension: l.dimension,
        })),
      }
      await documentsApi.confirm(id, body)
      setDuplicates(null)
      await load()
      window.scrollTo(0, 0)
    } catch (e) {
      if (e.status === 409 && e.details?.duplicates) setDuplicates({ message: e.message, list: e.details.duplicates })
      else {
        setError(e.message)
        setErrors(e.fields ?? {})
      }
    } finally {
      setBusy(false)
    }
  }

  const total = useMemo(() => lines.reduce((s, l) => s + (Number(l.quantity) || 0) * (parseMoney(l.unitCost) || 0), 0), [lines])
  const typeLabel = DOC_TYPES.find((t) => t.value === doc?.type)?.label ?? ''

  if (!doc) return <Card>{error ? <Notice tone="error">{error}</Notice> : <p className="muted">Loading…</p>}</Card>

  return (
    <>
      <PageHeader title={`${typeLabel} ${doc.docNo}`}
                  subtitle={doc.status === 'ENCODED' ? 'Encoded' : 'Check every field against the scan, then confirm'}
                  actions={<Button variant="secondary" onClick={() => navigate('/documents')}>Back to documents</Button>} />
      <div className="review-layout">
        <DocumentViewer id={doc.id} contentType={doc.contentType} name={doc.originalName} />

        <div className="review-side">
          <Card>
            <div className="row" style={{ justifyContent: 'space-between', gap: 10 }}>
              <StatusBadge status={doc.status} />
              <span className="muted">
                {doc.pageCount ? `${doc.pageCount} page${doc.pageCount === 1 ? '' : 's'}` : ''}
                {doc.ocrSeconds != null ? ` · read in ${doc.ocrSeconds} s` : ''}
              </span>
            </div>
            {(doc.status === 'QUEUED' || doc.status === 'PROCESSING') && (
              <p className="muted" style={{ margin: '12px 0 0' }}>
                The server is reading the document. A page takes up to a minute on the free server; this screen updates by itself.
              </p>
            )}
            {doc.status === 'FAILED' && (
              <Notice tone="error" style={{ marginTop: 12 }}>
                The document could not be read{doc.ocrError ? `: ${doc.ocrError}` : '.'} Encode the items by hand below, or read it again.
              </Notice>
            )}
            {!doc.ocrAvailable && doc.status !== 'ENCODED' && (
              <Notice style={{ marginTop: 12 }}>OCR is not installed on this server, so the fields are not pre-filled.</Notice>
            )}
            {doc.linked.length > 0 && (
              <div className="linked">
                <span className="field-label">Same purchase</span>
                {doc.linked.map((l) => (
                  <Link key={l.id} to={`/documents/${l.id}`} className="linked-chip">{l.typeLabel} {l.docNo}</Link>
                ))}
              </div>
            )}
          </Card>

          {doc.status === 'ENCODED' && <Encoded doc={doc} />}

          {header && (doc.status === 'READ' || doc.status === 'FAILED') && (
            <>
              <Card title="Document">
                <div className="review-header">
                  <Field label="Document no." required value={header.docNo} onChange={setH('docNo')} error={errors.docNo} />
                  <Field label="Date" required type="date" value={header.docDate} onChange={setH('docDate')}
                         error={errors.docDate} className={`field-input${low(doc.headerConfidence, 'docDate') ? ' low' : ''}`}
                         hint="Saved as the items' acquisition date" />
                  <Field label="Supplier" value={header.supplier} onChange={setH('supplier')} error={errors.supplier}
                         className={`field-input${low(doc.headerConfidence, 'supplier') ? ' low' : ''}`} />
                  <Field label="PO no." value={header.poNo} onChange={setH('poNo')} error={errors.poNo}
                         className={`field-input num${low(doc.headerConfidence, 'poNo') ? ' low' : ''}`} />
                  <Field label="PR no." value={header.prNo} onChange={setH('prNo')} error={errors.prNo}
                         className={`field-input num${low(doc.headerConfidence, 'prNo') ? ' low' : ''}`} />
                  <Field label="Department code" value={header.departmentCode} onChange={setH('departmentCode')}
                         error={errors.departmentCode}
                         className={`field-input${low(doc.headerConfidence, 'departmentCode') ? ' low' : ''}`} />
                </div>
                <p className="muted review-note"><span className="low-swatch" /> Highlighted: the OCR was unsure. Check these first.</p>
              </Card>

              <Card title={`Items (${lines.length})`}
                    subtitle="Each line becomes an item in OPC storage. For items tracked one by one, split the line and add serial numbers.">
                <div className="lines">
                  {lines.map((l, i) => (
                    <LineEditor key={l.key} line={l} index={i} lists={lists} errors={errors}
                                onChange={(k, v) => setL(l.key, k, v)} onSplit={() => split(l.key)}
                                onRemove={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} />
                  ))}
                </div>
                <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
                  <Button variant="secondary" onClick={() => setLines((ls) => [...ls, blankLine()])}>+ Add line</Button>
                  <span>Total <strong className="num">₱ {formatMoney(total)}</strong></span>
                </div>
              </Card>

              <Card>
                {duplicates && (
                  <Notice style={{ marginBottom: 14 }}>
                    {duplicates.message}
                    <ul className="dup-list">
                      {duplicates.list.map((d) => (
                        <li key={d.line}>Line {d.line + 1}: {d.matches.map((m) => (
                          <span key={m.id}><Link to={`/assets/${m.id}`}>{m.propertyTagNo ?? `#${m.id}`}</Link> ({m.reason}) </span>
                        ))}</li>
                      ))}
                    </ul>
                    Remove those lines, or save them anyway if they are new items.
                    <div style={{ marginTop: 10 }}><Button variant="secondary" onClick={() => confirm(true)} disabled={busy}>Save anyway</Button></div>
                  </Notice>
                )}
                {error && <Notice tone="error" style={{ marginBottom: 14 }}>{error}</Notice>}
                {user.role === 'NAS' && (
                  <p className="muted" style={{ marginTop: 0 }}>Your items are saved as drafts for Property Staff to confirm.</p>
                )}
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <Button variant="secondary" onClick={readAgain} disabled={busy || !doc.ocrAvailable}>Read again</Button>
                  <Button onClick={() => confirm(false)} disabled={busy || lines.length === 0}>
                    {busy ? 'Saving…' : `Confirm and Save ${lines.length} item${lines.length === 1 ? '' : 's'}`}
                  </Button>
                </div>
              </Card>
            </>
          )}
        </div>
      </div>
    </>
  )
}

function LineEditor({ line: l, index: i, lists, errors, onChange, onSplit, onRemove }) {
  const err = (k) => errors[`lines.${i}.${k}`]
  const cls = (k, extra = '') => `field-input${extra}${l.conf && low(l.conf, k) ? ' low' : ''}${err(k) ? ' invalid' : ''}`
  const lineTotal = (Number(l.quantity) || 0) * (parseMoney(l.unitCost) || 0)
  const ocrTotal = l.ocrTotal != null && Math.abs(Number(l.ocrTotal) - lineTotal) > 0.5 ? l.ocrTotal : null
  const anyError = Object.keys(errors).some((k) => k.startsWith(`lines.${i}.`))

  return (
    <div className={`line${anyError ? ' line-error' : ''}`}>
      <div className="line-head">
        <strong>Line {i + 1}</strong>
        <span className="row" style={{ gap: 12 }}>
          {Number(l.quantity) > 1 && <button className="link-btn" onClick={onSplit}>Split into {Math.min(Number(l.quantity), 200)}</button>}
          <button className="link-btn" onClick={() => onChange('more', !l.more)}>{l.more ? 'Fewer fields' : 'Serial, brand, model…'}</button>
          <button className="link-btn" onClick={onRemove}>Remove</button>
        </span>
      </div>
      <div className="line-grid">
        <label className="lf"><span>Qty *</span>
          <input className={cls('quantity', ' num')} value={l.quantity} inputMode="numeric"
                 onChange={(e) => onChange('quantity', e.target.value.replace(/\D/g, '').slice(0, 6))} aria-label={`Line ${i + 1} quantity`} />
        </label>
        <label className="lf"><span>Unit *</span>
          <select className={cls('unit')} value={l.unit} onChange={(e) => onChange('unit', e.target.value)} aria-label={`Line ${i + 1} unit`}>
            <option value="">Choose</option>
            {lists.units.map((u) => <option key={u} value={u}>{u}</option>)}
          </select>
        </label>
        <label className="lf"><span>Unit cost *</span>
          <input className={cls('unitCost', ' num')} value={l.unitCost} inputMode="decimal"
                 onChange={(e) => onChange('unitCost', e.target.value)} aria-label={`Line ${i + 1} unit cost`} />
        </label>
        <label className="lf"><span>Category *</span>
          <select className={cls('categoryId')} value={l.categoryId} onChange={(e) => onChange('categoryId', e.target.value)}
                  aria-label={`Line ${i + 1} category`}>
            <option value="">Choose</option>
            {lists.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <label className="lf lf-wide"><span>Description *</span>
          <textarea className={cls('description')} rows={2} value={l.description}
                    onChange={(e) => onChange('description', e.target.value)} aria-label={`Line ${i + 1} description`} />
        </label>
      </div>
      <div className="line-foot muted">
        Total ₱ {formatMoney(lineTotal)}
        {ocrTotal != null && <span className="warn-text"> · the document says ₱ {formatMoney(ocrTotal)}</span>}
        {l.ocrUnit && !l.unit && <span> · read as “{l.ocrUnit}”</span>}
        {l.ocrCategory && <span> · PR category: {l.ocrCategory}</span>}
      </div>
      {l.more && (
        <div className="line-grid more">
          <label className="lf"><span>Serial no.</span>
            <input className={cls('serialNo')} value={l.serialNo} onChange={(e) => onChange('serialNo', e.target.value)} maxLength={60} />
          </label>
          <label className="lf"><span>Brand</span>
            <input className={cls('brand')} value={l.brand} onChange={(e) => onChange('brand', e.target.value)} maxLength={60} />
          </label>
          <label className="lf"><span>Model no.</span>
            <input className={cls('modelNo')} value={l.modelNo} onChange={(e) => onChange('modelNo', e.target.value)} maxLength={60} />
          </label>
          <label className="lf"><span>Property tag no.</span>
            <input className={cls('propertyTagNo')} value={l.propertyTagNo} onChange={(e) => onChange('propertyTagNo', e.target.value)} maxLength={40} />
          </label>
          <label className="lf"><span>Color</span>
            <input className={cls('color')} value={l.color} onChange={(e) => onChange('color', e.target.value)} maxLength={40} />
          </label>
          <label className="lf"><span>Dimension</span>
            <input className={cls('dimension')} value={l.dimension} onChange={(e) => onChange('dimension', e.target.value)} maxLength={60} />
          </label>
        </div>
      )}
      {Object.entries(errors).filter(([k]) => k.startsWith(`lines.${i}.`)).map(([k, v]) => (
        <div key={k} className="field-error">{v}</div>
      ))}
    </div>
  )
}

function Encoded({ doc }) {
  const mins = doc.encodingSeconds != null ? Math.floor(doc.encodingSeconds / 60) : null
  return (
    <Card title={`${doc.assets.length} item${doc.assets.length === 1 ? '' : 's'} saved`}
          subtitle={mins != null ? `Encoded ${mins > 0 ? `${mins} min ` : ''}${doc.encodingSeconds % 60} s after upload` : ''}>
      <table className="table">
        <thead><tr><th>Item</th><th className="r">Qty</th><th>Status</th></tr></thead>
        <tbody>
          {doc.assets.map((a) => (
            <tr key={a.id}>
              <td><Link to={`/assets/${a.id}`}>{a.description}</Link></td>
              <td className="r num">{a.quantity}</td>
              <td>{a.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

/** SDD 1.2 DocumentViewer: the scan with zoom (images) or the browser's PDF viewer. */
function DocumentViewer({ id, contentType, name }) {
  const [url, setUrl] = useState(null)
  const [zoom, setZoom] = useState(1)
  const [error, setError] = useState('')

  useEffect(() => {
    let objectUrl
    documentsApi.fileBlob(id).then((blob) => {
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch((e) => setError(e.message))
    return () => objectUrl && URL.revokeObjectURL(objectUrl)
  }, [id])

  return (
    <div className="viewer">
      <div className="viewer-bar">
        <span className="muted viewer-name">{name}</span>
        {contentType !== 'application/pdf' && (
          <span className="row" style={{ gap: 6 }}>
            <Button variant="secondary" onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))} aria-label="Zoom out">−</Button>
            <span className="num">{Math.round(zoom * 100)}%</span>
            <Button variant="secondary" onClick={() => setZoom((z) => Math.min(4, z + 0.25))} aria-label="Zoom in">+</Button>
          </span>
        )}
      </div>
      <div className="viewer-body">
        {error && <Notice tone="error">{error}</Notice>}
        {url && contentType === 'application/pdf' && <iframe src={url} title="Scanned document" />}
        {url && contentType !== 'application/pdf' && (
          <img src={url} alt="Scanned document" style={{ width: `${zoom * 100}%` }} />
        )}
      </div>
    </div>
  )
}

function blankLine() {
  return { key: newKey(), lineId: null, quantity: '', unit: '', unitCost: '', description: '', categoryId: '',
    serialNo: '', brand: '', modelNo: '', propertyTagNo: '', color: '', dimension: '', more: false, conf: null }
}

/** An OCR line as an editable row: the unit only when it is one of the OPC's units, a category guessed from the PR. */
function toForm(l, lists) {
  return {
    ...blankLine(),
    lineId: l.id,
    quantity: l.quantity == null ? '' : String(l.quantity),
    unit: l.unit && lists.units.includes(l.unit) ? l.unit : '',
    ocrUnit: l.unit,
    unitCost: l.unitCost == null ? '' : formatMoney(l.unitCost),
    ocrTotal: l.totalCost,
    description: l.description ?? '',
    categoryId: guessCategory(l.category, lists.categories),
    ocrCategory: l.category,
    conf: l.confidence ?? {},
  }
}

function guessCategory(text, categories) {
  if (!text) return ''
  const words = text.toLowerCase().split(/[^a-z]+/).filter((w) => w.length > 3)
  let best = ''
  let bestScore = 0
  for (const c of categories) {
    const name = c.name.toLowerCase()
    const score = words.filter((w) => name.includes(w)).length
    if (score > bestScore) {
      bestScore = score
      best = String(c.id)
    }
  }
  return best
}
