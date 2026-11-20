import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../modules/auth/AuthContext'

/**
 * Sends signed-out users to the login page, users with a temporary password to the change-password
 * page, and blocks pages their role cannot use.
 */
export default function RequireAuth({ roles, children }) {
  const { user, ready, can } = useAuth()
  const location = useLocation()

  if (!ready) return null
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  if (!can(roles)) return <Navigate to="/" replace />
  return children
}
