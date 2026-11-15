import { parseDate } from './words.js'

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
  // A proxy or a missing endpoint can answer with HTML or plain text: keep that an ordinary error, not a JSON crash.
  let data = null
  if (text) {
    try { data = JSON.parse(text) } catch { data = null }
  }
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

/** The usual error sentence: the field errors joined, or the message. */
export const errText = (ex) => (ex?.body?.fields
  ? Object.entries(ex.body.fields).map(([k, v]) => `${k}: ${v}`).join('; ')
  : ex?.message || 'Something went wrong')

export const fmt = {
  dt: (s) => { const d = parseDate(s); return d ? d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—' },
  time: (s) => { const d = parseDate(s); return d ? d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '—' },
  day: (s) => { const d = parseDate(s); return d ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : '—' },
  ago: (s) => {
    const d = parseDate(s)
    if (!d) return '—'
    const m = Math.round((Date.now() - d) / 60000)
    if (m < 1) return 'just now'
    if (m < 60) return `${m} min ago`
    if (m < 1440) return `${Math.round(m / 60)} h ago`
    return `${Math.round(m / 1440)} d ago`
  },
  mins: (m) => (m >= 1440 ? `${(m / 1440).toFixed(1)} d` : m >= 60 ? `${(m / 60).toFixed(1)} h` : `${m} min`)
}
