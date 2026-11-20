import { api } from '../../shared/api/client'

/** SDD Module 2 endpoints. */
export const assetsApi = {
  search: (params) => api.get('/assets', { params }).then((r) => r.data),
  get: (id) => api.get(`/assets/${id}`).then((r) => r.data),
  history: (id) => api.get(`/assets/${id}/history`).then((r) => r.data),
  create: (body) => api.post('/assets', body).then((r) => r.data),
  update: (id, body) => api.put(`/assets/${id}`, body).then((r) => r.data),
  confirm: (id) => api.post(`/assets/${id}/confirm`).then((r) => r.data),
  suggestTag: (params) => api.get('/assets/tag-suggestion', { params }).then((r) => r.data),
  uploadPhoto: (id, file) => {
    const data = new FormData()
    data.append('file', file)
    return api.post(`/assets/${id}/photo`, data).then((r) => r.data)
  },
  /** The photo needs the auth header, so it is fetched as a blob and shown through an object URL. */
  photoBlob: (id) => api.get(`/assets/${id}/photo`, { responseType: 'blob' }).then((r) => r.data),
}

export const CONDITIONS = [
  { value: 'GOOD', label: 'Good Condition' },
  { value: 'FUNCTIONAL', label: 'Functional' },
  { value: 'DEFECTIVE', label: 'Defective' },
  { value: 'DAMAGED', label: 'Damaged' },
]

export const STATUSES = [
  { value: 'IN_STORAGE', label: 'In OPC storage' },
  { value: 'ISSUED', label: 'Issued' },
  { value: 'TURNED_OVER', label: 'Turned over' },
  { value: 'DISPOSED', label: 'Disposed' },
  { value: 'DRAFT', label: 'Draft' },
]
