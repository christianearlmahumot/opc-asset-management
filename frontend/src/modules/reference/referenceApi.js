import { api } from '../../shared/api/client'

/** Reference data: departments, accountable persons (employees), categories and units. */
export const referenceApi = {
  departments: () => api.get('/departments').then((r) => r.data),
  departmentDetails: () => api.get('/departments/details').then((r) => r.data),
  createDepartment: (body) => api.post('/departments', body).then((r) => r.data),
  updateDepartment: (id, body) => api.put(`/departments/${id}`, body).then((r) => r.data),

  categories: () => api.get('/categories').then((r) => r.data),
  createCategory: (name) => api.post('/categories', { name }).then((r) => r.data),
  renameCategory: (id, name) => api.put(`/categories/${id}`, { name }).then((r) => r.data),

  units: () => api.get('/units').then((r) => r.data),
  addUnit: (unit) => api.post('/units', { unit }).then((r) => r.data),

  employees: (params) => api.get('/employees', { params }).then((r) => r.data),
  createEmployee: (body) => api.post('/employees', body).then((r) => r.data),
  updateEmployee: (id, body) => api.put(`/employees/${id}`, body).then((r) => r.data),
}
