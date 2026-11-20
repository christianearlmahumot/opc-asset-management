import { Route, Routes } from 'react-router-dom'
import RequireAuth from '../../app/RequireAuth'
import CategoriesPage from '../reference/CategoriesPage'
import DepartmentsPage from '../reference/DepartmentsPage'
import EmployeesPage from '../reference/EmployeesPage'
import AssetDetailPage from './AssetDetailPage'
import AssetFormPage from './AssetFormPage'
import AssetSearchPage from './AssetSearchPage'

const ENCODERS = ['ADMIN', 'STAFF', 'NAS']
const OFFICE = ['ADMIN', 'STAFF']

/** Module 2 routes, mounted at /assets. */
export default function AssetRoutes() {
  return (
    <Routes>
      <Route index element={<AssetSearchPage />} />
      <Route path="new" element={<RequireAuth roles={ENCODERS}><AssetFormPage /></RequireAuth>} />
      <Route path="people" element={<RequireAuth roles={OFFICE}><EmployeesPage /></RequireAuth>} />
      <Route path="departments" element={<RequireAuth roles={OFFICE}><DepartmentsPage /></RequireAuth>} />
      <Route path="categories" element={<RequireAuth roles={OFFICE}><CategoriesPage /></RequireAuth>} />
      <Route path=":id" element={<AssetDetailPage />} />
      <Route path=":id/edit" element={<RequireAuth roles={ENCODERS}><AssetFormPage /></RequireAuth>} />
    </Routes>
  )
}
