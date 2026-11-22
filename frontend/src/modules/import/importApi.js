import { api } from '../../shared/api/client'

/** SDD 2.3: /api/import (Administrator only). The file is sent again to start, so it is never stored. */
export const importApi = {
  preview: (file, sheet) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post('/import/preview', form, { params: { sheet: sheet || undefined }, timeout: 180000 })
      .then((r) => r.data)
  },
  start: (file, request) => {
    const form = new FormData()
    form.append('file', file)
    form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    return api.post('/import', form, { timeout: 180000 }).then((r) => r.data)
  },
  batches: () => api.get('/import/batches').then((r) => r.data),
  batch: (id) => api.get(`/import/batches/${id}`).then((r) => r.data),
  rows: (id, params) => api.get(`/import/batches/${id}/rows`, { params }).then((r) => r.data),
  rowsXlsx: (id) =>
    api.get(`/import/batches/${id}/rows.xlsx`, { responseType: 'blob', timeout: 120000 }).then((r) => r.data),
}
