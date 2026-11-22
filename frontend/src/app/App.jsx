import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppShell from '../shared/layout/AppShell'
import { AuthProvider } from '../modules/auth/AuthContext'
import LoginPage from '../modules/auth/LoginPage'
import ChangePasswordPage from '../modules/auth/ChangePasswordPage'
import DashboardPage from '../modules/dashboard/DashboardPage'
import UserManagementPage from '../modules/users/UserManagementPage'
import AuditLogPage from '../modules/audit/AuditLogPage'
import ImportPage from '../modules/import/ImportPage'
import DocumentRoutes from '../modules/documents/DocumentRoutes'
import ReportRoutes from '../modules/reports/ReportRoutes'
import AssetRoutes from '../modules/assets/AssetRoutes'
import MovementRoutes from '../modules/movements/MovementRoutes'
import IssueOrderRoutes from '../modules/issue-orders/IssueOrderRoutes'
import RequireAuth from './RequireAuth'
import NotBuiltYet from './NotBuiltYet'
import { NAV, ROLES } from './navigation'

/*
 * Each build step adds its module's routes here, replacing the NotBuiltYet placeholder,
 * for example: <Route path="assets/*" element={<AssetRoutes />} />
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/change-password" element={<ChangePasswordPage />} />
          <Route
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="assets/*" element={<AssetRoutes />} />
            <Route
              path="issue-orders/*"
              element={
                <RequireAuth roles={[ROLES.ADMIN, ROLES.STAFF]}>
                  <IssueOrderRoutes />
                </RequireAuth>
              }
            />
            <Route
              path="movements/*"
              element={
                <RequireAuth roles={[ROLES.ADMIN, ROLES.STAFF]}>
                  <MovementRoutes />
                </RequireAuth>
              }
            />
            <Route
              path="users"
              element={
                <RequireAuth roles={[ROLES.ADMIN]}>
                  <UserManagementPage />
                </RequireAuth>
              }
            />
            <Route
              path="reports/*"
              element={
                <RequireAuth roles={[ROLES.ADMIN, ROLES.STAFF, ROLES.DEPT_HEAD]}>
                  <ReportRoutes />
                </RequireAuth>
              }
            />
            <Route
              path="documents/*"
              element={
                <RequireAuth roles={[ROLES.ADMIN, ROLES.STAFF, ROLES.NAS]}>
                  <DocumentRoutes />
                </RequireAuth>
              }
            />
            <Route
              path="import"
              element={
                <RequireAuth roles={[ROLES.ADMIN]}>
                  <ImportPage />
                </RequireAuth>
              }
            />
            <Route
              path="audit-log"
              element={
                <RequireAuth roles={[ROLES.ADMIN]}>
                  <AuditLogPage />
                </RequireAuth>
              }
            />
            {NAV.filter((n) => !n.ready).map((n) => (
              <Route
                key={n.to}
                path={`${n.to.slice(1)}/*`}
                element={
                  <RequireAuth roles={n.roles}>
                    <NotBuiltYet label={n.label} step={n.step} />
                  </RequireAuth>
                }
              />
            ))}
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
