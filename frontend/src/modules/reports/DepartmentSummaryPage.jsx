import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { referenceApi } from '../reference/referenceApi'
import { Button, Card, Field, Notice, PageHeader } from '../../shared/ui'
import { formatMoney } from '../../shared/format'
import { reportsApi } from './reportsApi'
import './reports.css'

const longDate = (d) =>
  d ? new Date(`${d}T00:00:00`).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }) : ''

/**
 * UI-304 View Department Inventory Report (SDD 5.1 DepartmentSummaryPage): choose a department,
 * see its totals, and print its current items grouped by room in the OPC's report layout.
 * Department Heads see only their own department (REQ-406, also enforced on the server).
 */
export default function DepartmentSummaryPage() {
  const { user } = useAuth()
  const isHead = user.role === 'DEPT_HEAD'
  const [params, setParams] = useSearchParams()
  const deptId = isHead ? String(user.departmentId ?? '') : params.get('dept') ?? ''

  const [departments, setDepartments] = useState([])
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [signs, setSigns] = useState(null)

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    setReport(null)
    setError('')
    if (!deptId) return
    setLoading(true)
    reportsApi
      .departmentSummary(deptId)
      .then((r) => {
        setReport(r)
        setSigns(defaultSigns(r, user))
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [deptId, user])

  const choices = isHead ? departments.filter((d) => String(d.id) === deptId) : departments
  const setSign = (k, f) => (e) => setSigns((s) => ({ ...s, [k]: { ...s[k], [f]: e.target.value } }))

  return (
    <>
      <div className="no-print">
        <PageHeader
          title="Department Inventory Report"
          subtitle="Current items of a department, grouped by room, as of today"
          actions={report && <Button onClick={() => printReport(report)}>Print report</Button>}
        />

        <Card>
          <div className="report-controls">
            <Field label="Department">
              {(id) => (
                <select id={id} className="field-input" value={deptId} disabled={isHead}
                        onChange={(e) => setParams(e.target.value ? { dept: e.target.value } : {})}>
                  {!isHead && <option value="">Choose a department</option>}
                  {choices.map((d) => <option key={d.id} value={d.id}>{d.code} · {d.name}</option>)}
                </select>
              )}
            </Field>
          </div>
          {!deptId && !isHead && (
            <p className="muted" style={{ margin: '4px 0 0' }}>Choose a department to see its inventory report.</p>
          )}
          {isHead && !deptId && (
            <Notice tone="error">Your account is not linked to a department. Ask an Administrator.</Notice>
          )}
          {error && <Notice tone="error" style={{ marginTop: 12 }}>{error}</Notice>}
          {loading && <p className="muted" style={{ margin: '12px 0 0' }}>Loading the report…</p>}
        </Card>

        {report && (
          <>
            <div className="report-totals">
              <Total label="Items" value={report.itemCount} />
              <Total label="Total quantity" value={report.totalQuantity} />
              <Total label="Total cost" value={`₱ ${formatMoney(report.totalCost)}`} />
              <Total label="Rooms" value={report.rooms.length} />
            </div>
            {report.itemsWithoutCost > 0 && (
              <Notice style={{ marginBottom: 18 }}>
                {report.itemsWithoutCost} item{report.itemsWithoutCost === 1 ? ' has' : 's have'} no acquisition cost
                recorded, so the total cost leaves {report.itemsWithoutCost === 1 ? 'it' : 'them'} out.
              </Notice>
            )}
            <Card title="Signatories" subtitle="Printed under the report. Change them before printing if needed.">
              <div className="report-signs-form">
                {SIGN_KEYS.map(([k, label]) => (
                  <div key={k} className="report-sign-field">
                    <div className="field-label">{label}</div>
                    <input className="field-input" placeholder="Name" value={signs[k].name} onChange={setSign(k, 'name')}
                           aria-label={`${label} name`} />
                    <input className="field-input" placeholder="Position" value={signs[k].title}
                           onChange={setSign(k, 'title')} aria-label={`${label} position`} />
                  </div>
                ))}
              </div>
            </Card>
          </>
        )}
      </div>

      {report && <ReportSheet report={report} signs={signs} />}
    </>
  )
}

/**
 * The browser prints the page title in its header and uses it as the PDF file name, so give it a
 * meaningful one while printing, e.g. "Department Inventory Report - CNAHS - 2026-11-22".
 */
function printReport(report) {
  const previous = document.title
  document.title = `Department Inventory Report - ${report.departmentCode} - ${report.asOf}`
  const restore = () => {
    document.title = previous
    window.removeEventListener('afterprint', restore)
  }
  window.addEventListener('afterprint', restore)
  window.print()
}

const SIGN_KEYS = [
  ['conducted', 'Inventory conducted by'],
  ['checked', 'Checked by'],
  ['verified', 'Verified by'],
  ['received', 'Received by'],
  ['noted', 'Noted by'],
]

function defaultSigns(r, user) {
  const office = user.role === 'ADMIN' || user.role === 'STAFF'
  return {
    conducted: { name: office ? user.fullName : '', title: office ? user.roleLabel : '' },
    checked: { name: '', title: '' },
    verified: { name: '', title: 'Property Custodian' },
    received: {
      name: r.headName ?? '',
      title: r.headDesignation ? `${r.headDesignation}, ${r.departmentCode}` : r.departmentCode,
    },
    noted: { name: '', title: 'Dean' },
  }
}

function Total({ label, value }) {
  return (
    <div className="report-total">
      <div className="report-total-label">{label}</div>
      <div className="report-total-value num">{value}</div>
    </div>
  )
}

/** The report as printed: OPC department inventory report layout, grouped by room. */
function ReportSheet({ report, signs }) {
  return (
    <div className="dept-report">
      <div className="dr-center dr-inst">{report.institution}</div>
      <div className="dr-center">OFFICE OF THE PROPERTY CUSTODIAN</div>
      <div className="dr-center dr-title">DEPARTMENT INVENTORY REPORT</div>
      <div className="dr-head">
        <div>
          Department / Office: <strong>{report.departmentCode}</strong> · {report.departmentName}
        </div>
        <div>As of {longDate(report.asOf)}</div>
      </div>

      <table>
        <thead>
          <tr>
            <th className="r" style={{ width: 52 }}>QTY</th>
            <th style={{ width: 58 }}>UNIT</th>
            <th>DESCRIPTION</th>
            <th style={{ width: 52 }}>YEAR</th>
            <th className="r" style={{ width: 100 }}>UNIT COST</th>
            <th className="r" style={{ width: 110 }}>TOTAL COST</th>
            <th style={{ width: 130 }}>REMARKS</th>
          </tr>
        </thead>
        {report.rooms.length === 0 ? (
          <tbody>
            <tr><td colSpan={7} className="dr-empty">This department has no assets.</td></tr>
          </tbody>
        ) : (
          report.rooms.map((g) => (
            <tbody key={g.room}>
              <tr className="dr-room"><td colSpan={7}>{g.room}</td></tr>
              {g.lines.map((l) => (
                <tr key={l.assetId}>
                  <td className="r num">{l.quantity}</td>
                  <td>{l.unit}</td>
                  <td>
                    <Link className="dr-link" to={`/assets/${l.assetId}`}>{l.description}</Link>
                    {detailsOf(l) && <div className="dr-sub">{detailsOf(l)}</div>}
                  </td>
                  <td className="num">{l.year ?? ''}</td>
                  <td className="r num">{l.unitCost == null ? '' : formatMoney(l.unitCost)}</td>
                  <td className="r num">{l.totalCost == null ? '' : formatMoney(l.totalCost)}</td>
                  <td>{l.remarks}</td>
                </tr>
              ))}
              <tr className="dr-subtotal">
                <td className="r num">{g.totalQuantity}</td>
                <td colSpan={4}>Subtotal · {g.room}</td>
                <td className="r num">{formatMoney(g.totalCost)}</td>
                <td />
              </tr>
            </tbody>
          ))
        )}
        <tfoot>
          <tr className="dr-grand">
            <td className="r num">{report.totalQuantity}</td>
            <td colSpan={4}>GRAND TOTAL · {report.itemCount} item{report.itemCount === 1 ? '' : 's'}</td>
            <td className="r num">{formatMoney(report.totalCost)}</td>
            <td />
          </tr>
        </tfoot>
      </table>

      <div className="dr-signs">
        {SIGN_KEYS.map(([k, label]) => (
          <div key={k} className="dr-sign">
            <div className="dr-sign-label">{label}:</div>
            <div className="dr-sign-name">{signs?.[k]?.name || ' '}</div>
            <div className="dr-sign-title">{signs?.[k]?.title || ' '}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

/** Tag, brand/model and serial under the description, as on the Property Inventory Slip. */
function detailsOf(l) {
  const brandModel = [l.brand, l.modelNo].filter(Boolean).join(' ')
  return [l.propertyTagNo, brandModel, l.serialNo && `S/N ${l.serialNo}`].filter(Boolean).join(' · ')
}
