import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { referenceApi } from '../reference/referenceApi'
import { Card, Field, Notice, PageHeader, Pager, SelectField } from '../../shared/ui'
import { dash, formatDate } from '../../shared/format'
import IssueOrderTabs from './IssueOrderTabs'
import { issueOrdersApi, openPdf } from './issueOrdersApi'
import './issueOrders.css'

/** UI-204 Issue Order History: find and reprint past issue orders. */
export default function IssueOrderHistoryPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const filter = {
    ioNo: params.get('io') ?? '', departmentId: params.get('dept') ?? '',
    from: params.get('from') ?? '', to: params.get('to') ?? '', page: Number(params.get('page') ?? 0),
  }
  const [departments, setDepartments] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    referenceApi.departments().then(setDepartments).catch(() => {})
  }, [])

  useEffect(() => {
    const t = setTimeout(() => {
      issueOrdersApi.list({
        ioNo: filter.ioNo || undefined, departmentId: filter.departmentId || undefined,
        from: filter.from || undefined, to: filter.to || undefined, page: filter.page, size: 25,
      }).then(setResult).catch((e) => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [filter.ioNo, filter.departmentId, filter.from, filter.to, filter.page]) // eslint-disable-line react-hooks/exhaustive-deps

  function update(k, v) {
    const next = new URLSearchParams(params)
    if (v === '' || v == null) next.delete(k)
    else next.set(k, v)
    if (k !== 'page') next.delete('page')
    setParams(next)
  }

  return (
    <>
      <PageHeader title="Issue Order History" subtitle="Find and reprint past issue orders" />
      <IssueOrderTabs />
      <Card style={{ marginBottom: 22 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 18 }}>
          <Field label="Issue order no." value={filter.ioNo} className="field-input num"
                 onChange={(e) => update('io', e.target.value.replace(/\D/g, '').slice(0, 7))} />
          <SelectField label="Department" value={filter.departmentId} onChange={(e) => update('dept', e.target.value)}
                       placeholder="All" options={departments.map((d) => ({ value: String(d.id), label: d.code }))} />
          <Field label="Date from" type="date" value={filter.from} onChange={(e) => update('from', e.target.value)} />
          <Field label="Date to" type="date" value={filter.to} onChange={(e) => update('to', e.target.value)} />
        </div>
      </Card>
      {error && <Notice tone="error" style={{ marginBottom: 18 }}>{error}</Notice>}
      <Card>
        <table className="table">
          <thead>
            <tr>
              <th>Issue order no.</th><th>Department</th><th>Accountable person</th><th>Lines</th><th>Date issued</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {result && result.items.length === 0 && (
              <tr><td colSpan={6} className="empty">No issue orders match.</td></tr>
            )}
            {result?.items.map((o) => (
              <tr key={o.id}>
                <td className="num"><strong>{o.ioNo}</strong></td>
                <td>{o.departmentCode}</td>
                <td>{dash(o.accountable)}</td>
                <td className="num">{o.lines}</td>
                <td className="num">{formatDate(o.issuedOn)}</td>
                <td>
                  <button className="link-btn" onClick={() => navigate(`/issue-orders/${o.id}`)}>View</button>
                  {' · '}
                  <button className="link-btn" onClick={() => openPdf(o.id).catch((e) => setError(e.message))}>Print</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {result && (
          <Pager page={result.page} totalPages={result.totalPages} total={result.total} size={result.size}
                 onPage={(p) => update('page', p)} />
        )}
      </Card>
    </>
  )
}
