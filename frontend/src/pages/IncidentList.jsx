import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { Badge, useLoad } from '../components/ui.jsx'
import { useAuth } from '../lib/auth.jsx'

const STATUSES = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'PENDING_INFO', 'RESOLVED', 'VERIFIED', 'CLOSED']
const SLA = ['ON_TRACK', 'AT_RISK', 'VIOLATED', 'MET']

export default function IncidentList() {
  const { user } = useAuth()
  const nav = useNavigate()
  const [f, setF] = useState({ q: '', status: '', priority: '', categoryId: '', serviceId: '', technicianId: '', slaStatus: '', from: '', to: '', page: 0 })
  const { data: services } = useLoad(() => api('/api/services'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const { data: techs } = useLoad(() => (user.role === 'EMPLOYEE' ? Promise.resolve([]) : api('/api/technicians')))
  const qs = Object.entries(f).filter(([, v]) => v !== '').map(([k, v]) => `${k}=${encodeURIComponent(k === 'from' || k === 'to' ? v + ':00' : v)}`).join('&')
  const { data, loading } = useLoad(() => api(`/api/incidents?${qs}&size=20`), [qs])
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value, page: 0 })

  return (
    <div className="stack">
      <div className="row spread"><h1>{user.role === 'EMPLOYEE' ? 'My incidents' : 'Incidents'}</h1>
        {user.role !== 'TECHNICIAN' && <Link to="/incidents/new"><button className="primary">+ Report incident</button></Link>}</div>
      <div className="card">
        <div className="grid cols-5">
          <div className="field"><label>Search (number, title, text)</label><input value={f.q} onChange={set('q')} placeholder="INC-000012, vpn, payment…" /></div>
          <div className="field"><label>Status</label><select value={f.status} onChange={set('status')}><option value="">Any</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select></div>
          <div className="field"><label>Priority</label><select value={f.priority} onChange={set('priority')}><option value="">Any</option>{['P1', 'P2', 'P3', 'P4'].map((s) => <option key={s}>{s}</option>)}</select></div>
          <div className="field"><label>Category</label><select value={f.categoryId} onChange={set('categoryId')}><option value="">Any</option>{(categories || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
          <div className="field"><label>Service</label><select value={f.serviceId} onChange={set('serviceId')}><option value="">Any</option>{(services || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
          {user.role !== 'EMPLOYEE' && <div className="field"><label>Technician</label><select value={f.technicianId} onChange={set('technicianId')}><option value="">{user.role === 'TECHNICIAN' ? 'Mine' : 'Any'}</option>{(techs || []).map((c) => <option key={c.id} value={c.id}>{c.fullName}</option>)}</select></div>}
          <div className="field"><label>SLA status</label><select value={f.slaStatus} onChange={set('slaStatus')}><option value="">Any</option>{SLA.map((s) => <option key={s}>{s}</option>)}</select></div>
          <div className="field"><label>Created from</label><input type="datetime-local" value={f.from} onChange={set('from')} /></div>
          <div className="field"><label>Created to</label><input type="datetime-local" value={f.to} onChange={set('to')} /></div>
          <div className="field"><label>&nbsp;</label><button onClick={() => setF({ q: '', status: '', priority: '', categoryId: '', serviceId: '', technicianId: '', slaStatus: '', from: '', to: '', page: 0 })}>Clear filters</button></div>
        </div>
      </div>
      <div className="card">
        {loading && <p className="muted">Loading…</p>}
        {data && <>
          <p className="small muted">{data.totalElements} incident(s)</p>
          <table>
            <thead><tr><th>Number</th><th>Title</th><th>Service</th><th>Category</th><th>Priority</th><th>Status</th><th>SLA</th><th>Technician</th><th>Created</th></tr></thead>
            <tbody>
              {data.content.map((i) => (
                <tr key={i.id} className="clickable" onClick={() => nav(`/incidents/${i.id}`)}>
                  <td className="mono">{i.incidentNumber}{i.escalationLevel > 0 && <span title="escalated"> ⬆︎</span>}</td>
                  <td>{i.title}</td><td>{i.service?.name}</td><td>{i.category?.name}</td>
                  <td><Badge v={i.priority} /></td><td><Badge v={i.status} /></td><td><Badge v={i.slaStatus} /></td>
                  <td>{i.technician?.name || <span className="muted">—</span>}</td><td className="small muted">{fmt.ago(i.createdAt)}</td>
                </tr>
              ))}
              {data.content.length === 0 && <tr><td colSpan={9} className="muted">No incidents match.</td></tr>}
            </tbody>
          </table>
          <div className="row" style={{ marginTop: '.75rem' }}>
            <button className="sm" disabled={f.page === 0} onClick={() => setF({ ...f, page: f.page - 1 })}>‹ Prev</button>
            <span className="small muted">Page {data.number + 1} of {Math.max(1, data.totalPages)}</span>
            <button className="sm" disabled={data.last} onClick={() => setF({ ...f, page: f.page + 1 })}>Next ›</button>
          </div>
        </>}
      </div>
    </div>
  )
}
