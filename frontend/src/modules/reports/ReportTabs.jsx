import { useAuth } from '../auth/AuthContext'
import { ROLES } from '../../app/navigation'
import { SubNav } from '../../shared/ui'

/** Exports are for Administrators and Property Staff (UC-305), so Department Heads see no tabs. */
export default function ReportTabs() {
  const { can } = useAuth()
  if (!can([ROLES.ADMIN, ROLES.STAFF])) return null
  return (
    <SubNav items={[
      { to: '/reports', label: 'Department Inventory', end: true },
      { to: '/reports/export', label: 'Export Reports' },
    ]} />
  )
}
