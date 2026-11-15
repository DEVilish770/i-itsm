import { createContext, useContext, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { dialogOpen, useDialog } from '../lib/dialog.js'
import { subscribe } from '../lib/ws.js'
import { prefersReducedMotion, useTheme } from '../lib/theme.jsx'
import { HEALTH, PRIORITY, STATUS, sentence, timeLeft } from '../lib/words.js'
import DotField from './DotField.jsx'
import { Icon } from './icons.jsx'

// ------------------------------------------------------------------ hooks

/** Generic loader hook: re-runs when deps change; exposes reload(). */
export function useLoad(fn, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let alive = true
    setLoading(true)
    fn().then((d) => { if (alive) { setData(d); setError(null) } }).catch((e) => alive && setError(e.message)).finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [...deps, tick]) // eslint-disable-line
  return { data, error, loading, reload: () => setTick((t) => t + 1), setData }
}

/** Re-renders every `ms` so "time left" stays true. */
export function useNow(ms = 30000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const id = window.setInterval(() => setNow(Date.now()), ms); return () => window.clearInterval(id) }, [ms])
  return now
}

export function useReducedMotion() {
  const [reduced, setReduced] = useState(prefersReducedMotion)
  useEffect(() => {
    let mq
    try { mq = window.matchMedia('(prefers-reduced-motion: reduce)') } catch { return undefined }
    const on = () => setReduced(mq.matches)
    mq.addEventListener?.('change', on)
    return () => mq.removeEventListener?.('change', on)
  }, [])
  return reduced
}

/** Closes a popover on a click outside it or on Escape (while a dialog is open, Escape belongs to the dialog). */
export function useDismiss(ref, open, onClose) {
  const close = useRef(onClose)
  close.current = onClose
  useEffect(() => {
    if (!open) return undefined
    const down = (e) => { if (ref.current && !ref.current.contains(e.target)) close.current() }
    const key = (e) => { if (e.key === 'Escape' && !dialogOpen()) close.current() }
    document.addEventListener('pointerdown', down)
    document.addEventListener('keydown', key)
    return () => { document.removeEventListener('pointerdown', down); document.removeEventListener('keydown', key) }
  }, [open, ref])
}

/** Pages set the serif title in the top bar (the shell falls back to a title per route). */
export const TitleCtx = createContext(() => {})
export function usePageTitle(text) {
  const set = useContext(TitleCtx)
  const { pathname } = useLocation()
  useEffect(() => { if (text) set({ path: pathname, text }) }, [text, pathname, set])
}

// ------------------------------------------------------------------ words with a tone

/** A technical value in small quiet text (kept for the detail view and admin tables). */
export const Badge = ({ v, cls = '' }) => (v ? <span className={`tech ${cls}`}>{String(v).replace(/_/g, ' ')}</span> : null)

export function PriorityWord({ p, code = true }) {
  const m = PRIORITY[p]
  if (!m) return <span className="prio">—</span>
  return <span className={`prio tone-${m.tone}`} title={`Priority ${m.tech}`}>{m.word}{code && <small>{p}</small>}</span>
}

export function StatusWord({ s }) {
  if (!s) return null
  return <span className="status-word" title={`Lifecycle state: ${s}`}>{STATUS[s] || sentence(s)}</span>
}

export function HealthWord({ s }) {
  const h = HEALTH[s]
  if (!h) return null
  return <span className={`health h-${h.tone}`} title={`SLA status: ${s}`}>{h.word}</span>
}

/** Tiny "AI" / "Rules" tag next to anything an AI or the built-in rules wrote. */
export function Tag({ source, title }) {
  if (!source) return null
  const ai = source === 'AI'
  return <span className={`tag${ai ? ' ai' : ''}`} title={title || (ai ? 'Written by AI' : 'From the built-in rules')}>{ai ? 'AI' : 'Rules'}</span>
}

export function TypingDots({ label = 'Working' }) {
  return <span className="typing" role="status" aria-label={label}><i /><i /><i /></span>
}

// ------------------------------------------------------------------ notices, numbers, meters

export function Alert({ kind = 'info', children, onClose }) {
  if (!children) return null
  return (
    <div className={`notice ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <div className="notice-body">{children}</div>
      {onClose && <button type="button" className="link quiet" onClick={onClose}>Dismiss</button>}
    </div>
  )
}

export function CountUp({ value, decimals = 0, duration = 750 }) {
  const reduced = useReducedMotion()
  const [shown, setShown] = useState(reduced ? value : 0)
  const cur = useRef(reduced ? value : 0)
  useEffect(() => {
    if (!Number.isFinite(value)) return undefined
    if (reduced) { cur.current = value; setShown(value); return undefined }
    const from = cur.current, start = performance.now()
    let raf = 0
    const step = (t) => {
      const p = Math.min(1, (t - start) / duration)
      cur.current = from + (value - from) * (1 - Math.pow(1 - p, 3))
      setShown(p === 1 ? value : cur.current)
      if (p < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [value, reduced, duration])
  return <>{decimals ? Number(shown).toFixed(decimals) : Math.round(shown)}</>
}

function BigNumber({ value }) {
  if (value === null || value === undefined || value === '' || value === '—') return '—'
  if (typeof value === 'number') return <CountUp value={value} decimals={Number.isInteger(value) ? 0 : 1} />
  const m = /^(-?\d+(?:\.\d+)?)(.*)$/.exec(String(value))
  if (!m) return String(value)
  return <><CountUp value={Number(m[1])} decimals={(m[1].split('.')[1] || '').length} />{m[2]}</>
}

const TONE = { bad: 'urgent', warn: 'high', ok: 'normal' }

/** A big serif number with a small label; numbers count up. tone: urgent | high | normal (or bad | warn | ok). */
export function Stat({ label, value, tone = '', title, sub }) {
  const t = TONE[tone] || tone
  return (
    <div className={`figure${t ? ` tone-${t}` : ''}`} title={title}>
      <div className="figure-n"><BigNumber value={value} /></div>
      <div className="figure-l">{label}</div>
      {sub && <div className="figure-sub">{sub}</div>}
    </div>
  )
}

/** Hairline bar chart. rows: [{ key?, label, value, tone?, title? }] */
export function Bars({ rows, empty }) {
  if (!rows?.length) return empty ? <p className="quiet">{empty}</p> : null
  const max = Math.max(1, ...rows.map((r) => r.value || 0))
  return (
    <div className="hbars">
      {rows.map((r, i) => (
        <div className="hbar" key={r.key ?? r.label} title={r.title}>
          <span className="hbar-label">{r.label}</span>
          <span className="hbar-track">
            <span className="hbar-fill" style={{ '--p': (r.value || 0) / (r.max || max), '--fill': `var(--${r.tone || 'accent'})`, animationDelay: `${i * 45}ms` }} />
          </span>
          <span className="hbar-n">{r.value}</span>
        </div>
      ))}
    </div>
  )
}

const FILL = { ok: 'var(--muted)', risk: 'var(--high)', late: 'var(--urgent)', done: 'var(--hairline)' }

/** The fix deadline of one incident (detail view), with the exact share of time used from the backend. */
export function SlaMeter({ incident, now = Date.now() }) {
  const sla = incident?.sla
  if (!sla || !sla.resolutionDeadline) return <p className="quiet">No deadline for this one.</p>
  const t = timeLeft(incident, now, sla.percentRemaining)
  const done = !!sla.resolvedAt
  return (
    <div className="meter-wrap">
      <div className="meter-head">
        <HealthWord s={sla.status} />
        <span className={`tone-${t.tone}`} title={`${Math.round(sla.percentRemaining)}% of the time remaining (SLATracker)`}>
          {done ? `Resolved ${fmt.time(sla.resolvedAt)}${sla.resolutionViolated ? ', late' : ', in time'}` : t.text}
        </span>
      </div>
      <div className="meter" title={t.title}>
        <span style={{ '--p': done ? 1 : (t.used ?? 0), '--fill': done ? FILL.done : FILL[t.tone] }} />
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ incident rows

/** One incident as a hairline row: priority bar, number, title (state and details underneath), priority word, time left, deadline hairline. */
export function IncidentRow({ i, index = 0, now = Date.now(), compact = false, onOpen }) {
  const p = PRIORITY[i.priority]
  const t = timeLeft(i, now)
  const details = [i.service?.name, i.category?.name, i.technician?.name || 'Not assigned yet', i.createdAt && fmt.ago(i.createdAt)].filter(Boolean).join(' · ')
  return (
    <Link to={`/incidents/${i.id}`} onClick={onOpen} className={`irow${compact ? ' compact' : ''}`}
      aria-label={`${i.incidentNumber}: ${i.title}. ${p ? `${p.word} priority, ` : ''}${STATUS[i.status] || sentence(i.status)}, ${t.text}`}
      style={{ '--bar': p ? `var(--${p.tone})` : 'var(--hairline)', animationDelay: `${Math.min(index, 14) * 24}ms` }}>
      <span className="irow-bar" aria-hidden="true" />
      <span className="irow-num">
        {i.incidentNumber}
        {i.escalationLevel > 0 && <span className="irow-esc" title={`Escalated (level ${i.escalationLevel})`}>↑</span>}
      </span>
      <span className="irow-main">
        <span className="irow-title">{i.title}</span>
        <span className="irow-meta">
          {p && <span className="irow-prio-inline"><PriorityWord p={i.priority} code={false} /> · </span>}
          <StatusWord s={i.status} />{!compact && details && <> · {details}</>}
        </span>
      </span>
      <span className="irow-prio"><PriorityWord p={i.priority} /></span>
      <span className={`irow-time tone-${t.tone}`} title={t.title}>{t.text}</span>
      <span className="irow-go" aria-hidden="true">→</span>
      {t.used != null && <span className="irow-used" aria-hidden="true" style={{ '--p': t.used, '--fill': FILL[t.tone] }} />}
    </Link>
  )
}

// ------------------------------------------------------------------ page frame

/** The thin dot band with the page's intro line, then the page body. `hero` replaces the band. */
export function Page({ intro, actions, hero, children, className = '' }) {
  return (
    <>
      {hero || <Band intro={intro} actions={actions} />}
      <div className={`wrap page-body ${className}`}>{children}</div>
    </>
  )
}

function Band({ intro, actions }) {
  const band = useRef(null)
  const right = useRef(null)
  const [clear, setClear] = useState(0)
  const hasActions = !!actions
  // Keep the dots clear of the buttons on the right: measure from the button group to the band's right edge.
  useLayoutEffect(() => {
    if (!hasActions || !right.current || !band.current) { setClear(0); return undefined }
    const measure = () => {
      const b = band.current?.getBoundingClientRect(), a = right.current?.getBoundingClientRect()
      if (b && a) setClear(Math.max(0, Math.round(b.right - a.left + 18)))
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(band.current); ro.observe(right.current)
    return () => ro.disconnect()
  }, [hasActions])
  return (
    <div className="band" ref={band}>
      <DotField variant="band" clearRight={clear} />
      <div className="wrap band-in">
        <div className="band-intro">{intro}</div>
        {actions && <div className="band-actions" ref={right}>{actions}</div>}
      </div>
    </div>
  )
}

/** The tall header band with the serif greeting over the dot field. */
export function Hero({ title, children }) {
  return (
    <div className="hero">
      <DotField variant="hero" />
      <div className="wrap hero-in">
        <h2 className="greeting">{title}</h2>
        {children && <div className="hero-line">{children}</div>}
      </div>
    </div>
  )
}

export function Section({ title, note, right, children, className = '' }) {
  return (
    <section className={`section ${className}`}>
      {(title || right) && (
        <div className="section-head">
          {title && <h2>{title}</h2>}
          {note && <span className="section-note">{note}</span>}
          {right && <div className="section-right">{right}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export function EmptyState({ title = 'All quiet.', children, action, compact = false }) {
  return (
    <div className={`empty${compact ? ' compact' : ''}`}>
      <DotField variant="empty" />
      <div className="empty-in">
        <p className="empty-title">{title}</p>
        {children && <p className="empty-text">{children}</p>}
        {action}
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ controls

/**
 * Segmented choice in text, with an underline under the chosen word. options: [[value, label], ...]
 * A radio group for the keyboard too: one Tab stop (the chosen word), arrow keys move and choose, Home and End jump.
 */
export function Choice({ value, options, onChange, label }) {
  const box = useRef(null)
  const at = Math.max(0, options.findIndex(([v]) => v === value))
  function onKey(e) {
    const n = options.length
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key]
    const to = e.key === 'Home' ? 0 : e.key === 'End' ? n - 1 : step ? (at + step + n) % n : -1
    if (to < 0 || !n) return
    e.preventDefault()
    onChange(options[to][0])
    box.current?.querySelectorAll('[role="radio"]')[to]?.focus()
  }
  return (
    <div className="choice" role="radiogroup" aria-label={label} ref={box} onKeyDown={onKey}>
      {options.map(([v, l], i) => (
        <button type="button" key={v} role="radio" aria-checked={value === v} tabIndex={i === at ? 0 : -1} className={value === v ? 'on' : ''} onClick={() => onChange(v)}>{l}</button>
      ))}
    </div>
  )
}

export function Tabs({ value, options, onChange, label }) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {options.map(([v, l]) => (
        <button type="button" key={v} role="tab" aria-selected={value === v} className={value === v ? 'on' : ''} onClick={() => onChange(v)}>{l}</button>
      ))}
    </div>
  )
}

export function Switch({ on, onChange, label, disabled }) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={label} disabled={disabled}
      className={`switch${on ? ' on' : ''}`} onClick={() => onChange(!on)}><span /></button>
  )
}

/** A form dialog: focus moves to its first field, Tab stays inside, Escape closes it, focus returns on close. */
export function Modal({ title, onClose, children }) {
  const box = useRef(null)
  useDialog(box, 'modal', onClose)
  return createPortal(
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal" ref={box} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="close" size={18} /></button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  )
}

export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const next = theme === 'dark' ? 'light' : 'dark'
  return (
    <button type="button" className="icon-btn theme-btn" onClick={toggle} aria-label={`Switch to the ${next} theme`} title={`Switch to the ${next} theme`}>
      <span className={`theme-icons ${theme}`}><Icon name="moon" className="i-moon" /><Icon name="sun" className="i-sun" /></span>
    </button>
  )
}

// ------------------------------------------------------------------ the bell

export function Bell() {
  const { user } = useAuth()
  const nav = useNavigate()
  const [open, setOpen] = useState(false)
  const { data, reload } = useLoad(() => api('/api/notifications'), [open])
  // The list also holds the EMAIL / SMS copies of each event; the bell shows and counts in-app rows only.
  const inApp = (data || []).filter((n) => n.channel === 'IN_APP')
  const unread = inApp.filter((n) => !n.read).length
  // Live: the backend pushes each in-app notification to /topic/user/{id}; refetch the list when one arrives.
  // The slow poll is only a safety net for pushes missed while the connection was down.
  useEffect(() => {
    if (!user?.id) return undefined
    const unsub = subscribe(`/topic/user/${user.id}`, () => reload())
    const id = setInterval(reload, 60000)
    return () => { unsub(); clearInterval(id) }
  }, [user?.id]) // eslint-disable-line
  const [readErr, setReadErr] = useState(null)
  async function readAll() {
    // Say so when the server can't be reached, instead of failing silently.
    try { setReadErr(null); await api('/api/notifications/read-all', { method: 'POST' }); reload() }
    catch (e) { setReadErr(e.message || 'Could not reach the server') }
  }
  function openOne(n) {
    setOpen(false)
    if (!n.read) api(`/api/notifications/${n.id}/read`, { method: 'POST' }).then(reload).catch(() => {})
    if (n.incidentId) nav(`/incidents/${n.incidentId}`)
  }
  const box = useRef(null)
  useDismiss(box, open, () => setOpen(false))
  return (
    <div className="bell" ref={box}>
      <button type="button" className={`icon-btn${unread ? ' lit' : ''}`} onClick={() => setOpen(!open)} aria-expanded={open}
        aria-label={unread ? `Notifications, ${unread} new` : 'Notifications'} title="Notifications">
        <Icon name="bell" />
        {unread > 0 && <span className="bell-count">{unread}</span>}
      </button>
      {open && (
        <div className="popover bell-pop" role="dialog" aria-label="Notifications">
          <div className="pop-head">
            <span>Notifications{unread > 0 && <span className="muted"> · {unread} new</span>}</span>
            <button type="button" className="link" onClick={readAll} disabled={unread === 0}>
              {unread > 0 ? 'Mark all read' : <><Icon name="check" size={14} /> All read</>}
            </button>
          </div>
          {readErr && <p className="pop-err">{readErr}</p>}
          {inApp.length === 0 && <p className="pop-empty">Nothing new. You're all caught up.</p>}
          <div className="pop-list">
            {inApp.map((n, idx) => (
              // Unread rows carry an accent bar and full-strength text; read rows fade, so "Mark all read" shows at a glance.
              <button type="button" key={n.id} className={`nrow${n.read ? ' read' : ''}`} onClick={() => openOne(n)} style={{ animationDelay: `${Math.min(idx, 10) * 18}ms` }}>
                <span className="nrow-subj">{n.subject}</span>
                <span className="nrow-text">{n.plainText || n.body}</span>
                <span className="nrow-meta">{fmt.ago(n.sentAt)}{n.plainText ? <Tag source={n.textSource} /> : null}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
