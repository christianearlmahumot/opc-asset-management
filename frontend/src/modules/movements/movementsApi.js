import { api } from '../../shared/api/client'

/** SDD Module 4 endpoints. */
export const movementsApi = {
  list: (params) => api.get('/movements', { params }).then((r) => r.data),
  transfer: (body) => api.post('/movements/transfer', body).then((r) => r.data),
  turnover: (body) => api.post('/movements/turnover', body).then((r) => r.data),
  disposal: (body) => api.post('/movements/disposal', body).then((r) => r.data),
  resignation: (body) => api.post('/movements/resignation', body).then((r) => r.data),
  heldBy: (employeeId) => api.get(`/employees/${employeeId}/assets`).then((r) => r.data),
}

export const MOVEMENT_TYPES = [
  { value: 'ENCODE', label: 'Encoded' },
  { value: 'ISSUE', label: 'Issued' },
  { value: 'TRANSFER', label: 'Transferred' },
  { value: 'CONDITION', label: 'Condition' },
  { value: 'TURNOVER', label: 'Turned over' },
  { value: 'RESIGNATION', label: 'Resignation turnover' },
  { value: 'DISPOSAL', label: 'Disposed' },
]

/** UC-302: the standard list that replaces free-text remarks. */
export const REASONS = [
  { value: 'DEFECTIVE', label: 'Defective' },
  { value: 'DAMAGED', label: 'Damaged' },
  { value: 'NO_LONGER_USED', label: 'No longer used' },
  { value: 'FOR_UPGRADE', label: 'For upgrade' },
  { value: 'OTHER', label: 'Other (with note)' },
]

export const today = () => {
  const d = new Date()
  const pad = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
