import { Route, Routes } from 'react-router-dom'
import RequireAuth from '../../app/RequireAuth'
import { ROLES } from '../../app/navigation'
import DepartmentSummaryPage from './DepartmentSummaryPage'
import ExportReportsPage from './ExportReportsPage'

/** Module 5 routes, mounted at /reports (Administrator, Property Staff, Department Head). */
export default function ReportRoutes() {
  return (
    <Routes>
      <Route index element={<DepartmentSummaryPage />} />
      <Route
        path="export"
        element={
          <RequireAuth roles={[ROLES.ADMIN, ROLES.STAFF]}>
            <ExportReportsPage />
          </RequireAuth>
        }
      />
    </Routes>
  )
}
