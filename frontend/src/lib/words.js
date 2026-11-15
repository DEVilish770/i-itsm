// Plain words for the main UI. The technical names (P1, IN_PROGRESS, SLA ...) stay in tooltips and in the detail view.

export const PRIORITY = {
  P1: { word: 'Urgent', tone: 'urgent', tech: 'P1 · Critical' },
  P2: { word: 'High', tone: 'high', tech: 'P2 · High' },
  P3: { word: 'Normal', tone: 'normal', tech: 'P3 · Medium' },
  P4: { word: 'Low', tone: 'low', tech: 'P4 · Low' }
}
export const PRIORITIES = ['P1', 'P2', 'P3', 'P4']

export const STATUS = {
  NEW: 'New',
  CLASSIFIED: 'Sorted',
  ASSIGNED: 'Assigned',
  ACKNOWLEDGED: 'Picked up',
  IN_PROGRESS: 'In progress',
  PENDING_INFO: 'Needs info',
  RESOLVED: 'Resolved',
  VERIFIED: 'Verified',
  CLOSED: 'Closed'
}
export const STATUSES = Object.keys(STATUS)
export const DONE = new Set(['RESOLVED', 'VERIFIED', 'CLOSED'])

/** SLA status in plain words. tone: ok | risk | late | done */
export const HEALTH = {
  ON_TRACK: { word: 'On track', tone: 'ok' },
  AT_RISK: { word: 'At risk', tone: 'risk' },
  VIOLATED: { word: 'Overdue', tone: 'late' },
  MET: { word: 'Met', tone: 'done' },
  NOT_APPLICABLE: { word: 'No deadline', tone: 'done' }
}
export const HEALTHS = Object.keys(HEALTH)

export const LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
export const LEVEL_WORD = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' }

export const EVENT = {
  CREATED: 'Reported',
  CLASSIFIED: 'Sorted',
  PRIORITY_CHANGED: 'Priority changed',
  ASSIGNED: 'Assigned',
  ACKNOWLEDGED: 'Picked up',
  INVESTIGATION_STARTED: 'Work started',
  INFO_REQUESTED: 'Asked for info',
  INFO_PROVIDED: 'Info provided',
  NOTE_ADDED: 'Note added',
  RESOLVED: 'Resolved',
  VERIFIED: 'Fix verified',
  REOPENED: 'Reopened',
  CLOSED: 'Closed',
  SLA_WARNING: 'Deadline at risk',
  SLA_VIOLATED: 'Deadline missed',
  ESCALATED: 'Escalated',
  DUPLICATE_DETECTED: 'Possible duplicate',
  FEEDBACK_GIVEN: 'Feedback given'
}

export const ROLE_WORD = { EMPLOYEE: 'Employee', TECHNICIAN: 'Technician', ADMIN: 'Admin' }

/** "IN_PROGRESS" -> "In progress" */
export const sentence = (s) => (s ? String(s).charAt(0) + String(s).slice(1).toLowerCase().replace(/_/g, ' ') : '')

/** Backend timestamps are zone-less LocalDateTime with up to 9 fraction digits; trim to milliseconds, read as local time. */
export function parseDate(s) {
  if (!s) return null
  if (s instanceof Date) return s
  const d = new Date(String(s).replace(/(\.\d{3})\d+/, '$1'))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 75 -> "1 h 15 min", 3000 -> "2 d 2 h" */
export function duration(mins) {
  const m = Math.max(0, Math.round(mins))
  if (m < 60) return `${m} min`
  if (m < 1440) {
    const h = Math.floor(m / 60), r = m % 60
    return r && h < 10 ? `${h} h ${r} min` : `${h} h`
  }
  const d = Math.floor(m / 1440), h = Math.floor((m % 1440) / 60)
  return h ? `${d} d ${h} h` : `${d} d`
}

const clamp01 = (v) => Math.max(0, Math.min(1, v))

/**
 * "Time left" for an incident summary or detail.
 * Returns { text, tone (ok|risk|late|done), used (share of the fix deadline used, 0..1, or null), title (technical tooltip) }.
 * The share is measured from createdAt; the detail view passes the backend's exact percentRemaining instead.
 */
export function timeLeft(i, now = Date.now(), percentRemaining) {
  const deadline = parseDate(i?.resolutionDeadline ?? i?.sla?.resolutionDeadline)
  const sla = i?.slaStatus || i?.sla?.status
  const tech = `SLA resolution deadline${deadline ? `: ${deadline.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}` : ' not set'} · SLA status ${sla || 'n/a'}`
  if (!deadline) return { text: 'No deadline', tone: 'done', used: null, title: tech }
  if (DONE.has(i.status) || sla === 'MET') {
    return { text: sla === 'VIOLATED' ? 'Finished late' : 'Finished in time', tone: 'done', used: null, title: tech }
  }
  const mins = (deadline.getTime() - now) / 60000
  let used
  if (typeof percentRemaining === 'number') used = clamp01(1 - percentRemaining / 100)
  else {
    const start = parseDate(i.sla?.startedAt || i.createdAt)
    const span = start ? deadline.getTime() - start.getTime() : 0
    used = span > 0 ? clamp01((now - start.getTime()) / span) : (mins < 0 ? 1 : 0)
  }
  if (mins < 0) return { text: `${duration(-mins)} over`, tone: 'late', used: 1, title: tech }
  const tone = sla === 'VIOLATED' ? 'late' : sla === 'AT_RISK' ? 'risk' : 'ok'
  return { text: `${duration(mins)} left`, tone, used, title: tech }
}

export function greeting(date = new Date()) {
  const h = date.getHours()
  if (h >= 5 && h < 12) return 'Good morning'
  if (h >= 12 && h < 17) return 'Good afternoon'
  return 'Good evening'
}

export const firstName = (u) => (u?.fullName || u?.username || '').trim().split(/\s+/)[0] || 'there'

export const initials = (u) => {
  const parts = (u?.fullName || u?.username || '?').trim().split(/\s+/)
  return ((parts[0]?.[0] || '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

/** "3 incidents", "1 incident" */
export const plural = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`

// ---- incident search filters (the GET /api/incidents query parameters)

export const FILTER_KEYS = ['q', 'status', 'priority', 'categoryId', 'serviceId', 'technicianId', 'slaStatus', 'from', 'to']
export const EMPTY_FILTERS = { q: '', status: '', priority: '', categoryId: '', serviceId: '', technicianId: '', slaStatus: '', from: '', to: '', page: 0 }

/** Same query string the incident list has always sent; from / to are datetime-local values, so ":00" adds the seconds. */
export function filtersToQuery(f) {
  return Object.entries(f)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => `${k}=${encodeURIComponent(k === 'from' || k === 'to' ? v + ':00' : v)}`)
    .join('&')
}

/** "2026-11-09", "2026-11-09T10:00:00Z" ... -> "2026-11-09T00:00" (datetime-local), or '' when unreadable. */
export function toLocalInput(v, endOfDay = false) {
  const m = /^(\d{4}-\d{2}-\d{2})(?:[T ](\d{2}:\d{2}))?/.exec(String(v ?? '').trim())
  if (!m) return ''
  return `${m[1]}T${m[2] || (endOfDay ? '23:59' : '00:00')}`
}

const up = (v) => String(Array.isArray(v) ? v[0] : v ?? '').trim().toUpperCase().replace(/\s+/g, '_')
const toId = (v) => {
  const n = Number(Array.isArray(v) ? v[0] : v)
  return v !== null && v !== '' && Number.isInteger(n) && n > 0 ? n : null
}
const PRIORITY_ALIASES = { URGENT: 'P1', CRITICAL: 'P1', HIGH: 'P2', NORMAL: 'P3', MEDIUM: 'P3', LOW: 'P4' }

/** Keeps only valid values for the existing search parameters, in the shapes the incident list's filter form holds. */
export function cleanFilters(raw) {
  const out = {}
  if (!raw || typeof raw !== 'object') return out
  if (typeof raw.q === 'string' && raw.q.trim()) out.q = raw.q.trim().slice(0, 200)
  const st = up(raw.status); if (STATUSES.includes(st)) out.status = st
  const pr = up(raw.priority); const p = PRIORITIES.includes(pr) ? pr : PRIORITY_ALIASES[pr]; if (p) out.priority = p
  for (const k of ['categoryId', 'serviceId', 'technicianId']) { const id = toId(raw[k]); if (id != null) out[k] = String(id) }
  const sla = up(raw.slaStatus); if (HEALTHS.includes(sla)) out.slaStatus = sla
  const from = toLocalInput(raw.from); if (from) out.from = from
  const to = toLocalInput(raw.to, true); if (to) out.to = to
  return out
}

/**
 * Keeps only valid create-incident fields from an AI draft (field names of IncidentRequests.CreateIncident).
 * Service and category ids must exist and be active when the lists are known. Values come back in the form's shapes.
 */
export function cleanDraft(d, { services, categories } = {}) {
  const out = {}
  if (!d || typeof d !== 'object') return out
  if (typeof d.title === 'string' && d.title.trim()) out.title = d.title.trim().slice(0, 200)
  if (typeof d.description === 'string' && d.description.trim()) out.description = d.description.trim().slice(0, 4000)
  const sid = toId(d.serviceId)
  if (sid != null && (!services || services.some((s) => s.id === sid && s.active))) out.serviceId = String(sid)
  const cid = toId(d.categoryId)
  if (cid != null && (!categories || categories.some((c) => c.id === cid && c.active))) out.categoryId = String(cid)
  const imp = up(d.impact); if (LEVELS.includes(imp)) out.impact = imp
  const urg = up(d.urgency); if (LEVELS.includes(urg)) out.urgency = urg
  if (d.affectedUsers !== null && d.affectedUsers !== undefined && d.affectedUsers !== '') {
    const n = Number(d.affectedUsers)
    if (Number.isFinite(n) && n >= 0 && n <= 1000000) out.affectedUsers = String(Math.round(n))
  }
  return out
}

export const isMac = () => {
  try { return /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent) } catch { return false }
}
