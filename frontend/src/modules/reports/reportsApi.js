import { api } from '../../shared/api/client'

/** SDD Module 5 endpoints. */
export const reportsApi = {
  departmentSummary: (departmentId) =>
    api.get(`/reports/departments/${departmentId}/summary`, { timeout: 60000 }).then((r) => r.data),
}
