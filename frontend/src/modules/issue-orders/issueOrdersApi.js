import { api } from '../../shared/api/client'

/** SDD Module 3 endpoints. */
export const issueOrdersApi = {
  requests: (status) => api.get('/requests', { params: { status } }).then((r) => r.data),
  request: (id) => api.get(`/requests/${id}`).then((r) => r.data),
  createRequest: (body) => api.post('/requests', body).then((r) => r.data),
  cancelRequest: (id) => api.post(`/requests/${id}/cancel`).then((r) => r.data),

  nextNumber: () => api.get('/issue-orders/next-number').then((r) => r.data.ioNo),
  prepare: (body) => api.post('/issue-orders', body).then((r) => r.data),
  list: (params) => api.get('/issue-orders', { params }).then((r) => r.data),
  get: (id) => api.get(`/issue-orders/${id}`).then((r) => r.data),
  pdfBlob: (id) => api.get(`/issue-orders/${id}/pdf`, { responseType: 'blob', timeout: 60000 }).then((r) => r.data),
}

/** Opens the PDF in a new tab, where the browser's viewer prints it (UC-202 step 8). */
export async function openPdf(id) {
  const tab = window.open('', '_blank')
  const blob = await issueOrdersApi.pdfBlob(id)
  const url = URL.createObjectURL(blob)
  if (tab) tab.location.href = url
  else window.location.href = url
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

export async function downloadPdf(id, ioNo) {
  const blob = await issueOrdersApi.pdfBlob(id)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `Issue-Order-${ioNo}.pdf`
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
