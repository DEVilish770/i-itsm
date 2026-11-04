import { useState } from 'react'
import { api } from '../lib/api.js'
import { Alert, Modal, useLoad } from '../components/ui.jsx'

const CRIT = ['LOW', 'MEDIUM', 'HIGH', 'MISSION_CRITICAL']

export default function AdminCatalog() {
  const { data: services, reload: rs } = useLoad(() => api('/api/services'))
  const { data: categories, reload: rc } = useLoad(() => api('/api/categories'))
  const { data: slas, reload: rl } = useLoad(() => api('/api/sla-policies'))
  const { data: strategies, reload: rst } = useLoad(() => api('/api/strategies'))
  const [err, setErr] = useState(null)
  const [ok, setOk] = useState(null)
  const [modal, setModal] = useState(null)
  const run = async (fn, msg) => { setErr(null); setOk(null); try { await fn(); setOk(msg) } catch (ex) { setErr(ex.body?.fields ? Object.entries(ex.body.fields).map(([k, v]) => `${k}: ${v}`).join('; ') : ex.message) } }

  return (
    <div className="stack">
      <h1>Services, categories, SLA &amp; strategies</h1>
      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert><Alert kind="ok" onClose={() => setOk(null)}>{ok}</Alert>

      <div className="card"><h3>Active strategies <span className="small muted">(Strategy pattern — switch at runtime, no redeploy)</span></h3>
        {strategies && <div className="grid cols-2">
          <div className="field"><label>Technician assignment</label><select value={strategies.activeAssignment} onChange={(e) => run(async () => { await api('/api/admin/strategies', { method: 'PUT', body: { assignmentStrategy: e.target.value } }); rst() }, 'Assignment strategy updated')}>
            {strategies.assignment.map((s) => <option key={s.name} value={s.name}>{s.name} — {s.description}</option>)}</select></div>
          <div className="field"><label>Priority calculation</label><select value={strategies.activePriority} onChange={(e) => run(async () => { await api('/api/admin/strategies', { method: 'PUT', body: { priorityStrategy: e.target.value } }); rst() }, 'Priority strategy updated')}>
            {strategies.priority.map((s) => <option key={s.name} value={s.name}>{s.name} — {s.description}</option>)}</select></div>
        </div>}
      </div>

      <div className="card"><h3>SLA policies</h3>
        <table><thead><tr><th>Priority</th><th>Response target (min)</th><th>Resolution target (min)</th><th>Warn when % remaining ≤</th><th /></tr></thead><tbody>
          {(slas || []).map((p) => <SlaRow key={p.priority} p={p} onSave={(body) => run(async () => { await api('/api/admin/sla-policies', { method: 'PUT', body }); rl() }, `SLA for ${p.priority} saved`)} />)}
        </tbody></table></div>

      <div className="grid cols-2">
        <div className="card"><div className="row spread"><h3>IT services</h3><button className="sm primary" onClick={() => setModal({ kind: 'service' })}>+ Add</button></div>
          <table><thead><tr><th>Name</th><th>Criticality</th><th>Owner</th><th>Users</th><th>Active</th></tr></thead><tbody>
            {(services || []).map((s) => <tr key={s.id}><td>{s.name}<div className="small muted">{s.description}</div></td><td>{s.criticality}</td><td>{s.owner}</td><td>{s.userBase}</td>
              <td><button className="sm" onClick={() => run(async () => { await api(`/api/admin/services/${s.id}`, { method: 'PUT', body: { ...s, active: !s.active } }); rs() }, 'Service updated')}>{s.active ? 'Retire' : 'Reactivate'}</button></td></tr>)}
          </tbody></table></div>
        <div className="card"><div className="row spread"><h3>Incident categories</h3><button className="sm primary" onClick={() => setModal({ kind: 'category' })}>+ Add</button></div>
          <table><thead><tr><th>Name</th><th>Auto-classification keywords</th><th /></tr></thead><tbody>
            {(categories || []).map((c) => <tr key={c.id}><td>{c.name}<div className="small muted">{c.description}</div></td>
              <td><textarea defaultValue={c.keywords} style={{ minHeight: 44, fontSize: '.8rem' }} onBlur={(e) => e.target.value !== c.keywords && run(async () => { await api(`/api/admin/categories/${c.id}`, { method: 'PUT', body: { ...c, keywords: e.target.value } }); rc() }, 'Keywords saved')} /></td>
              <td><button className="sm" onClick={() => run(async () => { await api(`/api/admin/categories/${c.id}`, { method: 'PUT', body: { ...c, active: !c.active } }); rc() }, 'Category updated')}>{c.active ? 'Disable' : 'Enable'}</button></td></tr>)}
          </tbody></table></div>
      </div>

      {modal?.kind === 'service' && <Modal title="Add IT service" onClose={() => setModal(null)}>
        <SimpleForm fields={[['name', 'Name'], ['description', 'Description'], ['criticality', 'Business criticality', CRIT], ['owner', 'Owner team'], ['userBase', 'Approx. users', 'number']]}
          onSubmit={(b) => run(async () => { await api('/api/admin/services', { method: 'POST', body: { ...b, userBase: Number(b.userBase || 0), criticality: b.criticality || 'MEDIUM' } }); rs(); setModal(null) }, 'Service created')} /></Modal>}
      {modal?.kind === 'category' && <Modal title="Add category" onClose={() => setModal(null)}>
        <SimpleForm fields={[['name', 'Name'], ['description', 'Description'], ['keywords', 'Keywords (comma separated)']]}
          onSubmit={(b) => run(async () => { await api('/api/admin/categories', { method: 'POST', body: b }); rc(); setModal(null) }, 'Category created — technicians can now be given this skill')} /></Modal>}
    </div>
  )
}

function SlaRow({ p, onSave }) {
  const [v, setV] = useState(p)
  return <tr><td><span className={`badge ${p.priority}`}>{p.priority}</span></td>
    <td><input type="number" min="1" value={v.responseMinutes} onChange={(e) => setV({ ...v, responseMinutes: Number(e.target.value) })} /></td>
    <td><input type="number" min="1" value={v.resolutionMinutes} onChange={(e) => setV({ ...v, resolutionMinutes: Number(e.target.value) })} /></td>
    <td><input type="number" min="1" max="99" value={v.warningThresholdPct} onChange={(e) => setV({ ...v, warningThresholdPct: Number(e.target.value) })} /></td>
    <td><button className="sm" onClick={() => onSave(v)}>Save</button></td></tr>
}

function SimpleForm({ fields, onSubmit }) {
  const [v, setV] = useState({})
  return <form onSubmit={(e) => { e.preventDefault(); onSubmit(v) }}>
    {fields.map(([k, label, type]) => <div key={k} className="field"><label>{label}</label>
      {Array.isArray(type) ? <select value={v[k] || type[0]} onChange={(e) => setV({ ...v, [k]: e.target.value })}>{type.map((o) => <option key={o}>{o}</option>)}</select>
        : <input type={type || 'text'} value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} required={k === 'name'} />}</div>)}
    <div className="actions"><button className="primary">Save</button></div></form>
}
