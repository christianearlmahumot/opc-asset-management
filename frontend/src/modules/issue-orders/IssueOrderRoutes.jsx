import { Route, Routes } from 'react-router-dom'
import IssueOrderDetailPage from './IssueOrderDetailPage'
import IssueOrderHistoryPage from './IssueOrderHistoryPage'
import PrepareIssueOrderPage from './PrepareIssueOrderPage'
import RequestFormPage from './RequestFormPage'
import RequestsPage from './RequestsPage'

/** Module 3 routes, mounted at /issue-orders (Administrator and Property Staff). */
export default function IssueOrderRoutes() {
  return (
    <Routes>
      <Route index element={<RequestsPage />} />
      <Route path="requests/new" element={<RequestFormPage />} />
      <Route path="prepare" element={<PrepareIssueOrderPage />} />
      <Route path="prepare/:requestId" element={<PrepareIssueOrderPage />} />
      <Route path="history" element={<IssueOrderHistoryPage />} />
      <Route path=":id" element={<IssueOrderDetailPage />} />
    </Routes>
  )
}
