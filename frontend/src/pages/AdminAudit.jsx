import { useState } from 'react'
import { api, fmt } from '../lib/api.js'
import { useLoad } from '../components/ui.jsx'

export default function AdminAudit() {
  const [tab, setTab] = useState('audit')
  const { data: audit } = useLoad(() => api('/api/admin/audit?size=100'), [tab])
  const { data: notes } = useLoad(() => api('/api/admin/notifications'), [tab])
  const { data: outbox } = useLoad(() => api('/api/admin/email-outbox'), [tab])
  return (
    <div className="stack">
      <h1>Audit log, notifications &amp; email outbox</h1>
      <div className="pill-group">{[['audit', '🧾 Audit log'], ['notes', '🔔 All notifications'], ['outbox', '📧 Simulated email outbox']].map(([k, l]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{l}</button>)}</div>
      {tab === 'audit' && <div className="card"><table><thead><tr><th>Time</th><th>Incident</th><th>Actor</th><th>Action</th><th>Transition</th><th>Description</th></tr></thead><tbody>
        {(audit?.content || []).map((a) => <tr key={a.id}><td className="small">{fmt.dt(a.timestamp)}</td><td className="mono">{a.incidentNumber}</td><td>{a.actor}</td><td><b>{a.action}</b></td><td className="small muted">{a.previousState !== a.newState ? `${a.previousState} → ${a.newState}` : a.newState}</td><td className="small">{a.description}</td></tr>)}
      </tbody></table></div>}
      {tab === 'notes' && <div className="card"><table><thead><tr><th>Time</th><th>Channel</th><th>Recipient</th><th>Subject</th><th>Event</th><th>Read</th></tr></thead><tbody>
        {(notes || []).map((n) => <tr key={n.id}><td className="small">{fmt.dt(n.sentAt)}</td><td><span className="badge role">{n.channel}</span></td><td className="small">{n.recipient}</td><td>{n.subject}</td><td className="small muted">{n.eventType}</td><td>{n.read ? '✔' : ''}</td></tr>)}
      </tbody></table></div>}
      {tab === 'outbox' && <div className="card">{(outbox || []).length === 0 && <p className="muted">No emails sent yet.</p>}{(outbox || []).map((l, i) => <div key={i} className="feed item mono small">{l}</div>)}</div>}
    </div>
  )
}
