import { api } from '../../shared/api/client'

/** SDD 6.2: /api/users (Administrator only). */
export const usersApi = {
  list: () => api.get('/users').then((r) => r.data),
  create: (body) => api.post('/users', body).then((r) => r.data),
  update: (id, body) => api.put(`/users/${id}`, body).then((r) => r.data),
  deactivate: (id) => api.post(`/users/${id}/deactivate`).then((r) => r.data),
  activate: (id) => api.post(`/users/${id}/activate`).then((r) => r.data),
  resetPassword: (id, temporaryPassword) =>
    api.post(`/users/${id}/reset-password`, { temporaryPassword }).then((r) => r.data),
}

export const ROLE_OPTIONS = [
  { value: 'ADMIN', label: 'Administrator' },
  { value: 'STAFF', label: 'Property Staff' },
  { value: 'NAS', label: 'NAS Assistant' },
  { value: 'DEPT_HEAD', label: 'Department Head' },
]

/** A readable temporary password, e.g. "maroon-4827-desk", that is easy to read out or write down. */
export function suggestTempPassword() {
  const words = ['desk', 'lamp', 'chair', 'stool', 'board', 'shelf', 'table', 'cabinet', 'printer', 'monitor']
  const pick = (arr) => arr[crypto.getRandomValues(new Uint32Array(1))[0] % arr.length]
  const n = 1000 + (crypto.getRandomValues(new Uint32Array(1))[0] % 9000)
  return `${pick(words)}-${n}-${pick(words)}`
}
