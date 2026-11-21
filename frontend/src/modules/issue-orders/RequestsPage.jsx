import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button, Card, Notice, PageHeader } from '../../shared/ui'
import { dash, formatDate } from '../../shared/format'
import IssueOrderTabs from './IssueOrderTabs'
import { issueOrdersApi } from './issueOrdersApi'
import './issueOrders.css'

/** Issue Orders home: department requests waiting for an issue order. */
export default function RequestsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [flash, setFlash] = useState(location.state?.flash ?? '')

  useEffect(() => {
    issueOrdersApi.requests('PENDING').then(setRows).catch((e) => setError(e.message))
  }, [])

  async function cancel(r) {
    if (!window.confirm(`Cancel the ${r.departmentCode} request${r.prNo ? ` ${r.prNo}` : ''}?`)) return
    try {
      await issueOrdersApi.cancelRequest(r.id)
      setRows((list) => list.filter((x) => x.id !== r.id))
      setFlash('Request cancelled.')
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <PageHeader title="Issue Orders" subtitle="Department requests and the issue orders that fulfil them"
                  actions={
                    <>
                      <Button variant="secondary" onClick={() => navigate('/issue-orders/prepare')}>Issue without request</Button>
                      <Button onClick={() => navigate('/issue-orders/requests/new')}>+ New Request</Button>
                    </>
                  } />
      <IssueOrderTabs />
      {flash && <Notice tone="ok" style={{ marginBottom: 18 }}>{flash}</Notice>}
      {error && <Notice tone="error" style={{ marginBottom: 18 }}>{error}</Notice>}
      <Card>
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Department</th>
              <th>PR number</th>
              <th>Purpose</th>
              <th>Items needed</th>
              <th style={{ width: 250 }}></th>
            </tr>
          </thead>
          <tbody>
            {rows === null ? (
              <tr><td colSpan={6} className="empty">Loading…</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={6} className="empty">No pending requests.</td></tr>
            ) : rows.map((r) => (
              <tr key={r.id}>
                <td className="num">{formatDate(r.createdAt)}</td>
                <td><strong>{r.departmentCode}</strong></td>
                <td className="num">{dash(r.prNo)}</td>
                <td>{dash(r.purpose)}</td>
                <td>
                  <ul className="requested">
                    {r.items.map((i, k) => <li key={k}>{i.quantity} × {i.description}</li>)}
                  </ul>
                </td>
                <td>
                  <div className="row" style={{ gap: 10, justifyContent: 'flex-end' }}>
                    <Button onClick={() => navigate(`/issue-orders/prepare/${r.id}`)}>Prepare Issue Order</Button>
                    <button className="link-btn" onClick={() => cancel(r)}>Cancel</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  )
}
