import { useNavigate } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { Badge, Stat, useLoad } from '../components/ui.jsx'
import { useAuth } from '../lib/auth.jsx'

export default function TechnicianQueue() {
  const { user } = useAuth()
  const nav = useNavigate()
  const { data } = useLoad(() => api('/api/incidents/queue'))
  const { data: techs } = useLoad(() => api('/api/technicians'))
  const me = (techs || []).find((t) => t.id === user.id)
  const list = data || []
  const need = list.filter((i) => i.status === 'ASSIGNED').length
  const risk = list.filter((i) => i.slaStatus === 'AT_RISK' || i.slaStatus === 'VIOLATED').length
  return (
    <div className="stack">
      <h1>My queue</h1>
      <div className="grid cols-4">
        <Stat label="Active incidents" value={me?.activeWorkload ?? list.length} />
        <Stat label="Capacity" value={me ? `${me.activeWorkload} / ${me.maxWorkload}` : '—'} tone={me && me.activeWorkload >= me.maxWorkload ? 'bad' : ''} />
        <Stat label="Awaiting acknowledgement" value={need} tone={need ? 'warn' : ''} />
        <Stat label="SLA at risk / breached" value={risk} tone={risk ? 'bad' : 'ok'} />
      </div>
      {me && <div className="card small muted">Skills: {(me.skills || []).join(', ') || 'none'} · {me.experienceYears} yrs experience {me.teamLead && '· Team lead'}</div>}
      <div className="card">
        <table>
          <thead><tr><th>Number</th><th>Title</th><th>Service</th><th>Priority</th><th>Status</th><th>SLA</th><th>Deadline</th><th>Age</th></tr></thead>
          <tbody>
            {list.map((i) => (
              <tr key={i.id} className="clickable" onClick={() => nav(`/incidents/${i.id}`)}>
                <td className="mono">{i.incidentNumber}</td><td>{i.title}</td><td>{i.service?.name}</td>
                <td><Badge v={i.priority} /></td><td><Badge v={i.status} /></td><td><Badge v={i.slaStatus} /></td>
                <td className="small">{fmt.dt(i.resolutionDeadline)}</td><td className="small muted">{fmt.ago(i.createdAt)}</td>
              </tr>
            ))}
            {list.length === 0 && <tr><td colSpan={8} className="muted">Nothing assigned to you right now.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
