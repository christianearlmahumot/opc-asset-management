import { api } from '../../shared/api/client'

export const referenceApi = {
  departments: () => api.get('/departments').then((r) => r.data),
}
