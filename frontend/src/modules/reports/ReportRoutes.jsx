import { Route, Routes } from 'react-router-dom'
import DepartmentSummaryPage from './DepartmentSummaryPage'

/** Module 5 routes, mounted at /reports (Administrator, Property Staff, Department Head). */
export default function ReportRoutes() {
  return (
    <Routes>
      <Route index element={<DepartmentSummaryPage />} />
    </Routes>
  )
}
