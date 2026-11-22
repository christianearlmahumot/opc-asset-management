import { api } from '../../shared/api/client'

/** SDD Module 5 endpoints. */
export const reportsApi = {
  departmentSummary: (departmentId) =>
    api.get(`/reports/departments/${departmentId}/summary`, { timeout: 60000 }).then((r) => r.data),

  /** UC-305: the file, its name from the server, and how many rows it holds (0 = headers only). */
  exportFile: (params) =>
    api.get('/reports/export', { params, responseType: 'blob', timeout: 180000 }).then((r) => ({
      blob: r.data,
      fileName: fileNameOf(r.headers['content-disposition']) ?? `report.${params.format === 'PDF' ? 'pdf' : 'xlsx'}`,
      rows: Number(r.headers['x-export-rows'] ?? 0),
    })),
}

function fileNameOf(disposition) {
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition ?? '')
  return m ? decodeURIComponent(m[1]) : null
}
