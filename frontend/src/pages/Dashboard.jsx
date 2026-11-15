import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { Badge, Stat, useLoad } from '../components/ui.jsx'
import { onConnectionChange, subscribe } from '../lib/ws.js'

/** Real-time operations dashboard: counters refresh on every WebSocket event; event feed streams live. */
export default function Dashboard() {
  const nav = useNavigate()
  const { data: ov, reload } = useLoad(() => api('/api/admin/reports/overview'))
  const { data: open, reload: reloadOpen } = useLoad(() => api('/api/incidents?size=12&sort=createdAt,desc'))
  const { data: load, reload: reloadLoad } = useLoad(() => api('/api/admin/reports/workload'))
  const [feed, setFeed] = useState([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const refresh = () => { reload(); reloadOpen(); reloadLoad() }
    const unsub = subscribe('/topic/incidents', (ev) => {
      setFeed((f) => (f[0] && f[0].type === ev.type && f[0].incidentId === ev.incidentId && f[0].at === ev.at) ? f : [ev, ...f].slice(0, 40))
      refresh()
    })
    // The badge follows the real STOMP connection. After a drop, refresh once: events sent while offline were missed.
    let wasLive = false
    const offConn = onConnectionChange((live) => {
      setConnected(live)
      if (live && wasLive) refresh()
      if (live) wasLive = true
    })
    return () => { unsub(); offConn() }
  }, []) // eslint-disable-line

  async function runMonitor() { await api('/api/admin/sla/run-monitor', { method: 'POST' }); reload(); reloadOpen() }

  return (
    <div className="stack">
      <div className="row spread"><h1>Live operations dashboard</h1><div className="row"><span className={connected ? 'live' : 'live off'}>{connected ? 'live via WebSocket' : 'connecting…'}</span><button className="sm" onClick={runMonitor}>Run SLA monitor now</button></div></div>
      <div className="grid cols-5">
        <Stat label="Total incidents" value={ov?.totalIncidents} />
        <Stat label="Open" value={ov?.openIncidents} />
        <Stat label="Critical open (P1)" value={ov?.criticalIncidents} tone={ov?.criticalIncidents ? 'bad' : ''} />
        <Stat label="SLA at risk" value={ov?.slaAtRisk} tone={ov?.slaAtRisk ? 'warn' : ''} />
        <Stat label="SLA violations" value={ov?.slaViolations} tone={ov?.slaViolations ? 'bad' : 'ok'} />
      </div>
      <div className="grid cols-2" style={{ gridTemplateColumns: '3fr 2fr' }}>
        <div className="stack">
          <div className="card"><h3>Latest incidents</h3>
            <table><thead><tr><th>Number</th><th>Title</th><th>Priority</th><th>Status</th><th>SLA</th><th>Technician</th></tr></thead><tbody>
              {(open?.content || []).map((i) => <tr key={i.id} className="clickable" onClick={() => nav(`/incidents/${i.id}`)}><td className="mono">{i.incidentNumber}</td><td>{i.title}</td><td><Badge v={i.priority} /></td><td><Badge v={i.status} /></td><td><Badge v={i.slaStatus} /></td><td>{i.technician?.name || '—'}</td></tr>)}
            </tbody></table></div>
          <div className="grid cols-2">
            <div className="card"><h3>By status</h3>{ov && Object.entries(ov.byStatus).map(([k, v]) => <div key={k} className="bar"><span style={{ width: 110 }} className="small">{k}</span><div className="track"><div style={{ width: `${ov.totalIncidents ? (v / ov.totalIncidents) * 100 : 0}%` }} /></div><span className="small muted">{v}</span></div>)}</div>
            <div className="card"><h3>Technician workload</h3>{(load || []).map((l) => <div key={l.technician} className="bar"><span style={{ width: 110 }} className="small">{l.technician}</span><div className="track"><div style={{ width: `${Math.min(100, l.activeIncidents * 15)}%` }} /></div><span className="small muted">{l.activeIncidents}</span></div>)}{!load?.length && <p className="muted small">No active assignments.</p>}</div>
          </div>
        </div>
        <div className="card"><h3>Event stream</h3>
          <div className="feed">
            {feed.length === 0 && <p className="muted small">Waiting for events… create or update an incident in another tab to see it appear here instantly.</p>}
            {feed.map((e, idx) => <div key={idx} className="item"><div className="row spread"><b>{e.type.replace(/_/g, ' ')}</b><span className="t">{new Date(e.at).toLocaleTimeString()}</span></div>
              <div><a href={`/incidents/${e.incidentId}`} className="mono">{e.incidentNumber}</a> {e.title} <Badge v={e.priority} /> <Badge v={e.status} /></div><div className="small muted">{e.actor}: {e.description}</div></div>)}
          </div>
        </div>
      </div>
    </div>
  )
}
