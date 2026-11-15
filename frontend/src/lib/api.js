// Thin fetch wrapper: attaches the JWT, parses JSON, throws a typed error on non-2xx.
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `HTTP ${status}`)
    this.status = status
    this.code = body?.code
    this.body = body
  }
}

export async function api(path, { method = 'GET', body, token } = {}) {
  const t = token ?? sessionStorage.getItem('itsm.token')
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  if (res.status === 401 && t) {
    // The token was rejected (expired, or the backend restarted with a new random JWT key): end this tab's login.
    sessionStorage.removeItem('itsm.token'); sessionStorage.removeItem('itsm.user')
    location.assign('/login')
  }
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

export const fmt = {
  dt: (s) => (s ? new Date(s).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—'),
  ago: (s) => {
    if (!s) return '—'
    const m = Math.round((Date.now() - new Date(s)) / 60000)
    if (m < 1) return 'just now'
    if (m < 60) return `${m} min ago`
    if (m < 1440) return `${Math.round(m / 60)} h ago`
    return `${Math.round(m / 1440)} d ago`
  },
  mins: (m) => (m >= 1440 ? `${(m / 1440).toFixed(1)} d` : m >= 60 ? `${(m / 60).toFixed(1)} h` : `${m} min`)
}
