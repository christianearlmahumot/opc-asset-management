/*
 * The sidebar of the wireframes, in their order, with the roles allowed to see each item
 * (SRS 2.2 permissions table). `ready: false` items show a "not built yet" page until their build step lands.
 */
export const ROLES = {
  ADMIN: 'ADMIN',
  STAFF: 'STAFF',
  NAS: 'NAS',
  DEPT_HEAD: 'DEPT_HEAD',
}

const { ADMIN, STAFF, NAS, DEPT_HEAD } = ROLES

export const NAV = [
  { to: '/', label: 'Dashboard', roles: null, ready: true, end: true },
  { to: '/documents', label: 'Document Capture', roles: [ADMIN, STAFF, NAS], ready: true },
  { to: '/assets', label: 'Asset Register', roles: [ADMIN, STAFF, NAS, DEPT_HEAD], ready: true },
  { to: '/issue-orders', label: 'Issue Orders', roles: [ADMIN, STAFF], ready: true },
  { to: '/movements', label: 'Movements', roles: [ADMIN, STAFF], ready: true },
  { to: '/reports', label: 'Summaries & Reports', roles: [ADMIN, STAFF, DEPT_HEAD], ready: true },
  { to: '/users', label: 'Users', roles: [ADMIN], ready: true },
  { to: '/import', label: 'Masterlist Import', roles: [ADMIN], ready: true },
  { to: '/audit-log', label: 'Audit Log', roles: [ADMIN], ready: true },
]
