import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader, SelectField } from '../../shared/ui'
import { formatMoney, parseMoney } from '../../shared/format'
import { assetsApi, CONDITIONS } from './assetsApi'
import { AssetPhoto } from './parts'
import './assets.css'

const EMPTY = {
  description: '', categoryId: '', propertyTagNo: '', brand: '', modelNo: '', serialNo: '', color: '', dimension: '',
  quantity: '1', unit: 'unit', acquisitionDate: '', unitCost: '', supplier: '', invoiceNo: '',
  departmentId: '', room: '', employeeId: '', condition: 'GOOD',
}

/**
 * UI-104 Add Asset and UI-106 Edit Asset (same form). Fields follow the Property Inventory Slip.
 * In edit mode the department, room and accountable person are read-only: they change by transfer.
 */
export default function AssetFormPage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const isNas = user.role === 'NAS'

  const [form, setForm] = useState(EMPTY)
  const [original, setOriginal] = useState(null)
  const [lists, setLists] = useState({ categories: [], units: [], departments: [] })
  const [people, setPeople] = useState([])
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [duplicate, setDuplicate] = useState(null) // { message, matches, canOverride }
  const [busy, setBusy] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [photo, setPhoto] = useState(null) // { file, url }
  const tagTouched = useRef(false)
  const fileInput = useRef(null)

  const custodyLocked = editing && original && original.status !== 'DRAFT'

  // Lists for the dropdowns, and the asset itself when editing.
  useEffect(() => {
    Promise.all([referenceApi.categories(), referenceApi.units(), referenceApi.departments(),
      editing ? assetsApi.get(id) : Promise.resolve(null)])
      .then(([categories, units, departments, asset]) => {
        setLists({ categories, units, departments })
        if (asset) {
          setOriginal(asset)
          tagTouched.current = true
          setForm(Object.fromEntries(Object.keys(EMPTY).map((k) => {
            const v = asset[k]
            if (k === 'unitCost') return [k, v == null ? '' : formatMoney(v)]
            return [k, v == null ? '' : String(v)]
          })))
        } else if (categories[0]) {
          setForm((f) => ({ ...f, categoryId: String(categories[0].id) }))
        }
      })
      .catch((e) => setLoadError(e.message))
  }, [editing, id])

  // Accountable persons of the chosen department.
  useEffect(() => {
    if (!form.departmentId) {
      setPeople([])
      return
    }
    referenceApi.employees({ departmentId: form.departmentId }).then(setPeople).catch(() => setPeople([]))
  }, [form.departmentId])

  // UC-104 step 3: suggest a property tag until the user types their own.
  useEffect(() => {
    if (editing || tagTouched.current || !form.description.trim()) return
    const year = form.acquisitionDate ? Number(form.acquisitionDate.slice(0, 4)) : undefined
    const t = setTimeout(() => {
      assetsApi.suggestTag({ year, departmentId: form.departmentId || undefined, description: form.description })
        .then((s) => !tagTouched.current && setForm((f) => ({ ...f, propertyTagNo: s.tag })))
        .catch(() => {})
    }, 450)
    return () => clearTimeout(t)
  }, [editing, form.description, form.departmentId, form.acquisitionDate])

  useEffect(() => () => photo?.url && URL.revokeObjectURL(photo.url), [photo])

  const set = (k) => (e) => {
    const v = e.target.value
    setForm((f) => ({ ...f, [k]: v, ...(k === 'departmentId' ? { employeeId: '' } : {}) }))
    setErrors((er) => ({ ...er, [k]: undefined }))
    if (duplicate) setDuplicate(null)
  }

  function validate() {
    const e = {}
    if (!form.description.trim()) e.description = 'Enter the description.'
    if (!form.categoryId) e.categoryId = 'Choose the category.'
    if (!(Number(form.quantity) >= 1)) e.quantity = 'At least 1.'
    if (!form.unit) e.unit = 'Choose the unit.'
    if (!form.acquisitionDate) e.acquisitionDate = 'Enter the acquisition date.'
    if (parseMoney(form.unitCost) === '') e.unitCost = 'Enter the unit cost.'
    if (form.employeeId && !form.departmentId) e.departmentId = 'Choose the department.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function save(confirmDuplicate = false) {
    if (!validate()) return
    setBusy(true)
    setError('')
    const body = {
      ...form,
      categoryId: Number(form.categoryId),
      quantity: Number(form.quantity),
      unitCost: parseMoney(form.unitCost),
      departmentId: form.departmentId ? Number(form.departmentId) : null,
      employeeId: form.employeeId ? Number(form.employeeId) : null,
      confirmDuplicate,
    }
    try {
      const saved = editing ? await assetsApi.update(id, body) : await assetsApi.create(body)
      if (photo?.file) {
        try {
          await assetsApi.uploadPhoto(saved.id, photo.file)
        } catch (err) {
          navigate(`/assets/${saved.id}`, { state: { flash: `Saved, but the photo was not uploaded: ${err.message}` } })
          return
        }
      }
      const flash = saved.status === 'DRAFT'
        ? 'Saved as a draft. Property Staff will confirm it before it joins the register.'
        : editing ? 'Changes saved.' : 'Asset added to the register.'
      navigate(`/assets/${saved.id}`, { replace: true, state: { flash } })
    } catch (err) {
      if (err.status === 409 && err.details?.duplicates) {
        setDuplicate({ message: err.message, matches: err.details.duplicates, canOverride: err.details.canOverride })
        setErrors(err.fields || {})
      } else {
        setErrors(err.fields || {})
        setError(err.message)
      }
    } finally {
      setBusy(false)
    }
  }

  function pickPhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setError('The photo must be a JPG, PNG or WebP image.')
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('The photo must be 5 MB or smaller.')
      return
    }
    setPhoto({ file, url: URL.createObjectURL(file) })
  }

  if (loadError) return <Notice tone="error">{loadError}</Notice>
  if (editing && !original) return <p className="muted">Loading…</p>

  const deptCode = lists.departments.find((d) => String(d.id) === form.departmentId)?.code
  const personName = people.find((p) => String(p.id) === form.employeeId)?.fullName ?? original?.accountable

  return (
    <>
      <PageHeader
        title={editing ? 'Edit Asset' : 'Add Asset'}
        subtitle={editing
          ? `${original.propertyTagNo ?? 'No tag'} · ${original.description}`
          : 'Fields follow the Property Inventory Slip. Fields marked * are required.'}
      />

      <form className="asset-form-layout" onSubmit={(e) => { e.preventDefault(); save(false) }} noValidate>
        <Card>
          <div className="asset-form-grid">
            <div className="span-4">
              <Field label="Description" required value={form.description} onChange={set('description')}
                     error={errors.description} placeholder="e.g. PRINTER L360, EPSON, All in One Printer" />
            </div>
            <div className="span-2">
              <SelectField label="Category" required value={form.categoryId} onChange={set('categoryId')}
                           error={errors.categoryId}
                           options={lists.categories.map((c) => ({ value: String(c.id), label: c.name }))} />
            </div>
            <div className="span-2">
              <Field label={editing ? 'Property tag no.' : 'Property tag no. (suggested)'} value={form.propertyTagNo}
                     error={errors.propertyTagNo} className="field-input num"
                     onChange={(e) => { tagTouched.current = true; set('propertyTagNo')(e) }}
                     hint={!editing && !tagTouched.current ? 'Year-office-item type-number. You can change it.' : undefined} />
            </div>
            <div className="span-2"><Field label="Brand" value={form.brand} onChange={set('brand')} /></div>
            <div className="span-2"><Field label="Model no." value={form.modelNo} onChange={set('modelNo')} /></div>
            <div className="span-2"><Field label="Color" value={form.color} onChange={set('color')} /></div>
            <div className="span-2"><Field label="Dimension" value={form.dimension} onChange={set('dimension')} /></div>
            <div className="span-2">
              <Field label="Serial no." value={form.serialNo} onChange={set('serialNo')} error={errors.serialNo}
                     className="field-input num" />
            </div>
            <div className="span-1">
              <Field label="Qty" required type="number" min="1" value={form.quantity} onChange={set('quantity')}
                     error={errors.quantity} className="field-input num" />
            </div>
            <div className="span-1">
              <SelectField label="Unit" required value={form.unit} onChange={set('unit')} error={errors.unit}
                           options={lists.units.map((u) => ({ value: u, label: u }))} />
            </div>
            <div className="span-2">
              <Field label="Acquisition date" required type="date" value={form.acquisitionDate}
                     onChange={set('acquisitionDate')} error={errors.acquisitionDate}
                     max={new Date().toISOString().slice(0, 10)} />
            </div>
            <div className="span-2">
              <Field label="Acquisition unit cost (₱)" required inputMode="decimal" value={form.unitCost}
                     onChange={set('unitCost')} error={errors.unitCost} className="field-input num"
                     onBlur={() => { const p = parseMoney(form.unitCost); if (p !== '') setForm((f) => ({ ...f, unitCost: formatMoney(p) })) }} />
            </div>
            <div className="span-2"><Field label="Supplier" value={form.supplier} onChange={set('supplier')} /></div>
            <div className="span-2">
              <Field label="Invoice no." value={form.invoiceNo} onChange={set('invoiceNo')} className="field-input num" />
            </div>

            {custodyLocked ? (
              <>
                <div className="span-1"><Field label="Department" value={original.departmentCode ?? 'OPC storage'} readOnly /></div>
                <div className="span-1"><Field label="Room / location" value={original.room ?? '—'} readOnly /></div>
                <div className="span-1"><Field label="Accountable person" value={original.accountable ?? '—'} readOnly /></div>
              </>
            ) : (
              <>
                <div className="span-1">
                  <SelectField label="Department" value={form.departmentId} onChange={set('departmentId')}
                               error={errors.departmentId} placeholder="OPC storage"
                               options={lists.departments.map((d) => ({ value: String(d.id), label: d.code }))} />
                </div>
                <div className="span-1">
                  <Field label="Room / location" value={form.room} onChange={set('room')} placeholder="e.g. Property office" />
                </div>
                <div className="span-1">
                  <SelectField label="Accountable person" value={form.employeeId} onChange={set('employeeId')}
                               error={errors.employeeId} disabled={!form.departmentId}
                               placeholder={form.departmentId ? (people.length ? 'Choose' : 'No persons yet') : '—'}
                               options={people.map((p) => ({ value: String(p.id), label: p.fullName }))} />
                </div>
              </>
            )}
            <div className="span-1">
              <SelectField label="Condition" required value={form.condition} onChange={set('condition')}
                           options={CONDITIONS} />
            </div>
            {custodyLocked && (
              <p className="locked-note">
                Department, room and accountable person change only by recording a transfer, so the history stays complete.
              </p>
            )}
            {!custodyLocked && form.departmentId && !people.length && (
              <p className="locked-note">
                No accountable persons for {deptCode} yet. Add them under <Link to="/assets/people">Accountable Persons</Link>.
              </p>
            )}
          </div>
        </Card>

        <Card className="side-panel">
          <div className="stack">
            <div className="field">
              <span className="field-label">Photo (optional, for unfamiliar items)</span>
              <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={pickPhoto} />
              {photo ? (
                <img className="photo-img" src={photo.url} alt="New item photo" />
              ) : editing && original.hasPhoto ? (
                <AssetPhoto assetId={original.id} alt={original.description} />
              ) : (
                <button type="button" className="photo-box photo-drop" onClick={() => fileInput.current?.click()}>
                  Item photo<br /><span style={{ fontSize: 13 }}>JPG or PNG, up to 5 MB</span>
                </button>
              )}
              {(photo || (editing && original.hasPhoto)) && (
                <button type="button" className="link-btn" style={{ alignSelf: 'flex-start' }}
                        onClick={() => fileInput.current?.click()}>
                  Replace photo
                </button>
              )}
            </div>

            {isNas && !editing && (
              <Notice>Your entry is saved as a draft until Property Staff confirm it.</Notice>
            )}

            {duplicate && (
              <Notice tone={duplicate.canOverride ? 'warn' : 'error'}>
                {duplicate.message}
                <ul className="dup-list">
                  {duplicate.matches.map((m) => (
                    <li key={m.id}>
                      <Link to={`/assets/${m.id}`} target="_blank">{m.propertyTagNo ?? 'No tag'}</Link> · {m.description}
                      {m.serialNo && <> · <span className="num">{m.serialNo}</span></>}
                    </li>
                  ))}
                </ul>
                {duplicate.canOverride && (
                  <div className="row" style={{ marginTop: 10 }}>
                    <Button disabled={busy} onClick={() => save(true)}>It's a different item, save</Button>
                  </div>
                )}
              </Notice>
            )}

            {error && <Notice tone="error">{error}</Notice>}
            {Object.values(errors).some(Boolean) && !error && !duplicate && (
              <Notice tone="error">Please correct the highlighted fields.</Notice>
            )}

            <div className="row">
              <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</Button>
              <Button variant="secondary" onClick={() => navigate(editing ? `/assets/${id}` : '/assets')}>Cancel</Button>
            </div>
            {!custodyLocked && form.departmentId === '' && (
              <span className="field-hint">With no department, the item is recorded as in OPC storage.</span>
            )}
            {!custodyLocked && personName && deptCode && (
              <span className="field-hint">Accountable: {personName} ({deptCode}).</span>
            )}
          </div>
        </Card>
      </form>
    </>
  )
}
