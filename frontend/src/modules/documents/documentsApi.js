import { api } from '../../shared/api/client'

/** SDD Module 1: /api/documents (Administrator, Property Staff, NAS Assistant). */
export const documentsApi = {
  list: (params) => api.get('/documents', { params }).then((r) => r.data),
  get: (id) => api.get(`/documents/${id}`).then((r) => r.data),
  upload: ({ file, type, docNo, confirmDuplicate }) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post('/documents', form, { params: { type, docNo, confirmDuplicate }, timeout: 120000 })
      .then((r) => r.data)
  },
  fileBlob: (id) => api.get(`/documents/${id}/file`, { responseType: 'blob', timeout: 120000 }).then((r) => r.data),
  updateHeader: (id, header) => api.put(`/documents/${id}`, header).then((r) => r.data),
  readAgain: (id) => api.post(`/documents/${id}/read-again`).then((r) => r.data),
  confirm: (id, body) => api.post(`/documents/${id}/confirm`, body, { timeout: 120000 }).then((r) => r.data),
}

export const DOC_TYPES = [
  { value: 'SALES_INVOICE', label: 'Sales invoice', numberLabel: 'Sales invoice no.', example: 'e.g. 3135' },
  { value: 'PURCHASE_ORDER', label: 'Purchase order', numberLabel: 'PO no.', example: 'e.g. 000004960' },
  { value: 'PURCHASE_REQUEST', label: 'Purchase request', numberLabel: 'PR no.', example: 'e.g. PR-1004MEDTEC-26-009330' },
]

export const STATUS_LABELS = {
  QUEUED: 'Waiting to be read',
  PROCESSING: 'Reading…',
  READ: 'Ready for review',
  FAILED: 'Could not be read',
  ENCODED: 'Encoded',
}

/** Fields the OCR was less sure of are highlighted (UC-102 step 2). */
export const LOW_CONFIDENCE = 70
