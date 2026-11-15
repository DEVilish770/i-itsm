import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { onConnectionChange, subscribe } from '../lib/ws.js'
import { EVENT, PRIORITY, STATUS, STATUSES, firstName, greeting, plural, sentence } from '../lib/words.js'
import { Bars, EmptyState, Hero, IncidentRow, Page, PriorityWord, Section, Stat, StatusWord, useLoad, useNow } from '../components/ui.jsx'

/** Real-time operations dashboard: counters refresh on every WebSocket event; the event feed streams live. */
export default function Dashboard() {
  const { user } = useAuth()
  const now = useNow(30000)
  const { data: ov, reload } = useLoad(() => api('/api/admin/reports/overview'))
  const { data: open, reload: reloadOpen } = useLoad(() => api('/api/incidents?size=12&sort=createdAt,desc'))
  const { data: load, reload: reloadLoad } = useLoad(() => api('/api/admin/reports/workload'))
  const [feed, setFeed] = useState([])
  const [connected, setConnected] = useState(false)
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    const refresh = () => { reload(); reloadOpen(); reloadLoad() }
    let seq = 0 // stable keys, so each new event slides in on its own
    const unsub = subscribe('/topic/incidents', (ev) => {
      setFeed((f) => (f[0] && f[0].type === ev.type && f[0].incidentId === ev.incidentId && f[0].at === ev.at) ? f : [{ ...ev, _k: ++seq }, ...f].slice(0, 40))
      refresh()
    })
    // The live marker follows the real STOMP connection. After a drop, refresh once: events sent while offline were missed.
    let wasLive = false
    const offConn = onConnectionChange((live) => {
      setConnected(live)
      if (live && wasLive) refresh()
      if (live) wasLive = true
    })
    return () => { unsub(); offConn() }
  }, []) // eslint-disable-line

  async function runMonitor() {
    setChecking(true)
    try { await api('/api/admin/sla/run-monitor', { method: 'POST' }); reload(); reloadOpen() } finally { setChecking(false) }
  }

  const need = ov ? (ov.slaAtRisk || 0) + (ov.slaViolations || 0) : 0
  const summary = !ov ? 'Gathering the latest…'
    : ov.openIncidents === 0 ? 'All quiet. Nothing is open right now.'
      : `${plural(ov.openIncidents, 'incident')} open${ov.criticalIncidents ? `, ${ov.criticalIncidents} urgent` : ''}. ${need ? `${need} ${need === 1 ? 'needs' : 'need'} attention.` : 'Everything is on track.'}`

  const statusRows = ov ? STATUSES.map((s) => ({ key: s, label: STATUS[s], value: ov.byStatus?.[s] ?? 0, max: Math.max(1, ov.totalIncidents), title: `${s}: ${ov.byStatus?.[s] ?? 0}` })) : []
  const loadRows = (load || []).map((l) => ({ key: l.technician, label: l.technician, value: l.activeIncidents, title: `${l.technician}: ${l.activeIncidents} active` }))
  const rows = open?.content || []

  return (
    <Page hero={(
      <Hero title={`${greeting()}, ${firstName(user)}.`}>
        <span>{summary}</span>
        <span className={`live-dot${connected ? ' on' : ''}`} title={connected ? 'Live via WebSocket (STOMP /topic/incidents)' : 'Connecting to the live feed'}>{connected ? 'Live' : 'Connecting…'}</span>
        <button type="button" className="link accent" onClick={runMonitor} disabled={checking} title="Runs the SLA monitor sweep now instead of waiting for the scheduler">
          {checking ? 'Checking…' : 'Check deadlines now'}
        </button>
      </Hero>
    )}>
      <div className="figures five">
        <Stat label="All incidents" value={ov?.totalIncidents} title="Total incidents" />
        <Stat label="Open" value={ov?.openIncidents} title="Open incidents (not resolved, verified or closed)" />
        <Stat label="Urgent and open" value={ov?.criticalIncidents} tone={ov?.criticalIncidents ? 'urgent' : ''} title="Critical open (P1)" />
        <Stat label="At risk" value={ov?.slaAtRisk} tone={ov?.slaAtRisk ? 'high' : ''} title="SLA at risk" />
        <Stat label="Overdue" value={ov?.slaViolations} tone={ov?.slaViolations ? 'urgent' : ''} title="SLA violations" />
      </div>

      <div className="split">
        <div>
          <Section title="Latest" right={<Link to="/incidents" className="link accent">See all <span aria-hidden="true">→</span></Link>}>
            {open && rows.length === 0 && <EmptyState compact>No incidents yet. New reports appear here the moment they arrive.</EmptyState>}
            <div className="rows">{rows.map((i, idx) => <IncidentRow key={i.id} i={i} index={idx} now={now} />)}</div>
          </Section>
          <div className="duo">
            <Section title="By state">{ov && <Bars rows={statusRows} />}</Section>
            <Section title="Who's working on what" note="Technician workload"><Bars rows={loadRows} empty="No one has active work right now." /></Section>
          </div>
        </div>
        <Section title="Live activity" note="Event stream" className="feed-section">
          {feed.length === 0 && (
            <EmptyState title="Listening." compact>Changes from any tab appear here the moment they happen. Try reporting something in another tab.</EmptyState>
          )}
          <ol className="feed">
            {feed.map((e) => (
              <li key={e._k} className="feed-item">
                <div className="feed-top">
                  <span className="feed-type" title={e.type}>{EVENT[e.type] || sentence(e.type)}</span>
                  <span className="feed-time">{fmt.time(e.at)}</span>
                </div>
                <div className="feed-what">
                  <Link to={`/incidents/${e.incidentId}`} className="feed-num">{e.incidentNumber}</Link>
                  <span className="feed-title">{e.title}</span>
                </div>
                <div className="feed-meta">
                  {e.priority && PRIORITY[e.priority] && <PriorityWord p={e.priority} />}
                  {e.status && <StatusWord s={e.status} />}
                  <span>{e.actor}{e.description ? `: ${e.description}` : ''}</span>
                </div>
              </li>
            ))}
          </ol>
        </Section>
      </div>
    </Page>
  )
}
