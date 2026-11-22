import axios from 'axios'

/*
 * One Axios instance for the whole app. It attaches the JWT to every call (SRS 3.1.3) and turns
 * server errors into a plain { message, fields } object that forms can show directly.
 */

const TOKEN_KEY = 'fams.token'

export const tokenStore = {
  get() {
    try {
      return sessionStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token) {
    try {
      sessionStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* storage blocked: the session lasts only until reload */
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 30000,
})

api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized = () => {}
/** AuthContext registers this so an expired token sends the user back to the login page. */
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const status = err.response?.status
    let body = err.response?.data
    // File downloads ask for a blob, so a JSON error message arrives as a blob too: read it.
    if (body instanceof Blob && body.type.includes('json')) {
      try {
        body = JSON.parse(await body.text())
      } catch {
        body = null
      }
    }
    if (status === 401 && !err.config?.url?.endsWith('/auth/login')) {
      onUnauthorized()
    }
    const message =
      body?.message ||
      (err.code === 'ECONNABORTED'
        ? 'The server took too long to answer. It may be waking up; try again in a minute.'
        : !err.response
          ? 'Cannot reach the server. Check your connection.'
          : 'Something went wrong.')
    return Promise.reject({ status, message, fields: body?.fields || {}, details: body?.details })
  },
)
