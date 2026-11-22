import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { DOC_TYPES, documentsApi } from './documentsApi'
import './documents.css'

const ACCEPTED = ['application/pdf', 'image/jpeg', 'image/png']

/**
 * UI-101 Upload Purchasing Document (SDD 1.1 UploadDocumentPage): the document type, its number and
 * the scanned file. The server reads it in the background and the review screen opens.
 */
export default function UploadDocumentPage() {
  const navigate = useNavigate()
  const [type, setType] = useState('SALES_INVOICE')
  const [docNo, setDocNo] = useState('')
  const [file, setFile] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [duplicate, setDuplicate] = useState(null)
  const [busy, setBusy] = useState(false)
  const t = DOC_TYPES.find((x) => x.value === type)

  async function upload(confirmDuplicate = false) {
    const errs = {}
    if (!docNo.trim()) errs.docNo = 'Enter the document number.'
    if (!file) errs.file = 'Choose the scanned file.'
    setErrors(errs)
    setError('')
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      const d = await documentsApi.upload({ file, type, docNo: docNo.trim(), confirmDuplicate })
      navigate(`/documents/${d.id}`)
    } catch (e) {
      if (e.status === 409 && e.details?.duplicates) setDuplicate({ message: e.message, docs: e.details.duplicates })
      else {
        setError(e.message)
        setErrors(e.fields ?? {})
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Upload Document" subtitle="A scanned sales invoice, purchase order or purchase request" />
      <Card className="upload-card">
        <div className="upload-grid">
          <SelectField label="Document type" required value={type} onChange={(e) => { setType(e.target.value); setDuplicate(null) }}
                       options={DOC_TYPES.map((x) => ({ value: x.value, label: x.label }))} />
          <Field label={t.numberLabel} required value={docNo} placeholder={t.example} error={errors.docNo}
                 onChange={(e) => { setDocNo(e.target.value); setDuplicate(null) }} maxLength={40} />
        </div>
        <FileDropzone file={file} onFile={(f) => { setFile(f); setErrors((x) => ({ ...x, file: undefined })) }} error={errors.file} />
        <p className="muted upload-hint">
          Upload each document on its own: one invoice, one PO or one PR. Up to 10 pages are read. Printed documents read
          best; check handwritten marks yourself on the next screen.
        </p>

        {duplicate && (
          <Notice style={{ marginTop: 16 }}>
            {duplicate.message}
            <ul className="dup-list">
              {duplicate.docs.map((d) => (
                <li key={d.id}><a href={`/documents/${d.id}`} onClick={(e) => { e.preventDefault(); navigate(`/documents/${d.id}`) }}>
                  {d.typeLabel} {d.docNo}</a> · {d.status.toLowerCase()}</li>
              ))}
            </ul>
            <Button variant="secondary" onClick={() => upload(true)} disabled={busy}>Upload anyway</Button>
          </Notice>
        )}
        {error && <Notice tone="error" style={{ marginTop: 16 }}>{error}</Notice>}

        <div className="row" style={{ marginTop: 20, gap: 10 }}>
          <Button onClick={() => upload(false)} disabled={busy}>{busy ? 'Uploading…' : 'Upload and Read'}</Button>
          <Button variant="secondary" onClick={() => navigate('/documents')}>Cancel</Button>
        </div>
      </Card>
    </>
  )
}

/** SDD 1.1 FileDropzone: drag and drop or browse; checks the file type before upload. */
function FileDropzone({ file, onFile, error }) {
  const input = useRef(null)
  const [over, setOver] = useState(false)
  const [typeError, setTypeError] = useState('')

  function pick(f) {
    if (!f) return
    if (!ACCEPTED.includes(f.type)) {
      setTypeError('Choose a JPG, PNG or PDF file.')
      return
    }
    if (f.size > 15 * 1024 * 1024) {
      setTypeError('The file is larger than 15 MB. Scan at 300 DPI or less.')
      return
    }
    setTypeError('')
    onFile(f)
  }

  return (
    <div className="field" style={{ marginTop: 16 }}>
      <span className="field-label">Scanned file *</span>
      <div className={`dropzone${over ? ' over' : ''}${error || typeError ? ' has-error' : ''}`}
           role="button" tabIndex={0}
           onClick={() => input.current?.click()}
           onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') input.current?.click() }}
           onDragOver={(e) => { e.preventDefault(); setOver(true) }}
           onDragLeave={() => setOver(false)}
           onDrop={(e) => { e.preventDefault(); setOver(false); pick(e.dataTransfer.files?.[0]) }}>
        {file ? (
          <>
            <strong>{file.name}</strong>
            <span className="muted">{(file.size / 1024 / 1024).toFixed(1)} MB · click to choose another</span>
          </>
        ) : (
          <>
            <strong>Drop the scan here, or click to browse</strong>
            <span className="muted">JPG, PNG or PDF</span>
          </>
        )}
      </div>
      <input ref={input} type="file" hidden accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
             onChange={(e) => pick(e.target.files?.[0])} />
      {(typeError || error) && <span className="field-error">{typeError || error}</span>}
    </div>
  )
}
