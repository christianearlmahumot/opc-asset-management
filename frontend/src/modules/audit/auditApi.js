import { api } from '../../shared/api/client'

/** SDD 6.3: /api/audit-logs (Administrator only, read-only). */
export const auditApi = {
  search: (params) => api.get('/audit-logs', { params }).then((r) => r.data),
  get: (id) => api.get(`/audit-logs/${id}`).then((r) => r.data),
  entities: () => api.get('/audit-logs/entities').then((r) => r.data),
}

export const ACTIONS = [
  { value: 'CREATE', label: 'Created' },
  { value: 'UPDATE', label: 'Updated' },
  { value: 'DELETE', label: 'Deleted' },
  { value: 'LOGIN', label: 'Logged in' },
  { value: 'LOGIN_FAILED', label: 'Failed login' },
  { value: 'MOVEMENT', label: 'Movement' },
  { value: 'IMPORT', label: 'Import' },
  { value: 'EXPORT', label: 'Export' },
]

const ENTITY_LABELS = {
  app_user: 'User account',
  asset: 'Asset',
  category: 'Category',
  department: 'Department',
  employee: 'Accountable person',
  value_mapping: 'Unit / value mapping',
  dept_request: 'Department request',
  issue_order: 'Issue order',
  movement: 'Movement',
  import_batch: 'Masterlist import',
  source_document: 'Purchasing document',
  report: 'Report',
}

export const actionLabel = (a) => ACTIONS.find((x) => x.value === a)?.label ?? a
export const entityLabel = (e) => ENTITY_LABELS[e] ?? e

/** "issueOrderLineId" → "Issue order line id" */
export function fieldLabel(key) {
  const words = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function formatValue(v) {
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

/** Screens of the app that show a record, so an entry can link to it. */
export function recordLink(entity, id) {
  if (id == null) return null
  if (entity === 'asset') return `/assets/${id}`
  if (entity === 'issue_order') return `/issue-orders/${id}`
  return null
}

/** One line for the table: what was created, or which fields changed. */
export function summarize(e) {
  const n = e.newValues ?? {}
  if (e.action === 'LOGIN_FAILED') return n.username ? `Username tried: ${n.username}` : 'Unknown username'
  if (e.action === 'LOGIN') return 'Signed in'
  if (e.action === 'IMPORT') {
    return [n.imported != null && `${n.imported} lines imported`, n.issueOrders != null && `${n.issueOrders} issue orders`,
      n.setAside && `${n.setAside} set aside`, n.rejected && `${n.rejected} rejected`].filter(Boolean).join(' · ')
  }
  if (e.action === 'EXPORT') {
    return [n.report, n.format, n.department, n.rows != null && `${n.rows} rows`].filter(Boolean).join(' · ')
  }
  if (e.action === 'UPDATE') {
    const keys = Object.keys(n)
    return keys.length ? `Changed: ${keys.map(fieldLabel).join(', ')}` : 'Updated'
  }
  const name = n.description ?? n.ioNo ?? n.username ?? n.fullName ?? n.name ?? n.code ?? n.value
  return name != null ? String(name) : ''
}
