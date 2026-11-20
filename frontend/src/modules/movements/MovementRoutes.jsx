import { Route, Routes } from 'react-router-dom'
import ConditionPage from './ConditionPage'
import MovementsPage from './MovementsPage'
import ResignationPage from './ResignationPage'
import TransferPage from './TransferPage'

/** Module 4 routes, mounted at /movements (Administrator and Property Staff only). */
export default function MovementRoutes() {
  return (
    <Routes>
      <Route index element={<MovementsPage />} />
      <Route path="transfer/:assetId" element={<TransferPage />} />
      <Route path="condition/:assetId" element={<ConditionPage />} />
      <Route path="resignation" element={<ResignationPage />} />
    </Routes>
  )
}
