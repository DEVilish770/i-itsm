import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'

export const Badge = ({ v, cls = '' }) => (v ? <span className={`badge ${v} ${cls}`}>{String(v).replace(/_/g, ' ')}</span> : null)

export function Alert({ kind = 'info', children, onClose }) {
  if (!children) return null
  return <div className={`alert ${kind}`}>{children}{onClose && <button className="sm" style={{ float: 'right' }} onClick={onClose}>×</button>}</div>
}

export function Stat({ label, value, tone = '' }) {
  return <div className={`card stat ${tone}`}><div className="label">{label}</div><div className="value">{value ?? '—'}</div></div>
}

export function SlaMeter({ sla }) {
  if (!sla || !sla.resolutionDeadline) return <span className="muted">No SLA</span>
  const pct = Math.max(0, Math.min(100, sla.percentRemaining))
  const tone = sla.status === 'VIOLATED' ? 'bad' : sla.status === 'AT_RISK' ? 'warn' : ''
  const done = sla.resolvedAt
  return (
    <div>
      <div className="row spread small"><span><Badge v={sla.status} /></span>
        <span className="muted">{done ? `resolved ${new Date(sla.resolvedAt).toLocaleTimeString()}` : sla.minutesRemaining >= 0 ? `${sla.minutesRemaining} min left` : `${-sla.minutesRemaining} min overdue`}</span></div>
      {!done && <div className={`meter ${tone}`}><div style={{ width: `${pct}%` }} /></div>}
    </div>
  )
}

/** Generic loader hook: re-runs when deps change; exposes reload(). */
export function useLoad(fn, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let alive = true
    setLoading(true)
    fn().then((d) => alive && setData(d)).catch((e) => alive && setError(e.message)).finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [...deps, tick]) // eslint-disable-line
  return { data, error, loading, reload: () => setTick((t) => t + 1), setData }
}

export function Modal({ title, onClose, children }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.45)', display: 'grid', placeItems: 'center', zIndex: 50 }} onClick={onClose}>
      <div className="card" style={{ width: 520, maxWidth: '95vw' }} onClick={(e) => e.stopPropagation()}>
        <div className="row spread"><h2>{title}</h2><button className="sm" onClick={onClose}>×</button></div>
        {children}
      </div>
    </div>
  )
}

export function Bell() {
  const [open, setOpen] = useState(false)
  const { data, reload } = useLoad(() => api('/api/notifications'), [open])
  const unread = (data || []).filter((n) => !n.read).length
  useEffect(() => { const id = setInterval(reload, 15000); return () => clearInterval(id) }, []) // eslint-disable-line
  async function readAll() { await api('/api/notifications/read-all', { method: 'POST' }); reload() }
  return (
    <div className="bell" style={{ position: 'relative' }}>
      <button onClick={() => setOpen(!open)}>🔔 Notifications{unread > 0 && <span className="count">{unread}</span>}</button>
      {open && (
        <div className="card dropdown">
          <div className="row spread"><b>In-app notifications</b><button className="sm" onClick={readAll}>Mark all read</button></div>
          {(data || []).filter((n) => n.channel === 'IN_APP').length === 0 && <p className="muted">Nothing yet.</p>}
          {(data || []).filter((n) => n.channel === 'IN_APP').map((n) => (
            <div key={n.id} className="feed item" style={{ opacity: n.read ? .6 : 1 }}>
              <div><b>{n.subject}</b></div><div className="small">{n.body}</div><div className="t">{new Date(n.sentAt).toLocaleString()}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
