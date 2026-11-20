import { useAuth } from '../auth/AuthContext'
import { SubNav } from '../../shared/ui'

/** Tabs of the Asset Register module. Reference lists live here so the sidebar stays as in the wireframes. */
export default function AssetTabs() {
  const { can } = useAuth()
  const items = [
    { to: '/assets', label: 'Assets', end: true, roles: null },
    { to: '/assets/people', label: 'Accountable Persons', roles: ['ADMIN', 'STAFF'] },
    { to: '/assets/departments', label: 'Departments', roles: ['ADMIN', 'STAFF'] },
    { to: '/assets/categories', label: 'Categories & Units', roles: ['ADMIN', 'STAFF'] },
  ].filter((i) => can(i.roles))
  return items.length > 1 ? <SubNav items={items} /> : null
}
