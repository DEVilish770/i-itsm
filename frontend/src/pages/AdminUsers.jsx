import { useState } from 'react'
import { api } from '../lib/api.js'
import { Alert, Modal, useLoad } from '../components/ui.jsx'

export default function AdminUsers() {
  const { data: users, reload } = useLoad(() => api('/api/admin/users'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const [modal, setModal] = useState(null)
  const [err, setErr] = useState(null)
  const [f, setF] = useState({ username: '', password: '', fullName: '', email: '', phone: '', role: 'TECHNICIAN', department: '', experienceYears: 2, maxWorkload: 5, teamLead: false, skillCategoryIds: [] })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  async function create(e) {
    e.preventDefault(); setErr(null)
    try {
      await api('/api/admin/users', { method: 'POST', body: { ...f, experienceYears: Number(f.experienceYears), maxWorkload: Number(f.maxWorkload) } })
      setModal(null); reload()
    } catch (ex) { setErr(ex.body?.fields ? Object.entries(ex.body.fields).map(([k, v]) => `${k}: ${v}`).join('; ') : ex.message) }
  }
  async function toggle(u) { await api(`/api/admin/users/${u.id}/active?active=${!u.active}`, { method: 'PATCH' }); reload() }
  async function updateTech(t, patch) { try { await api(`/api/admin/technicians/${t.id}`, { method: 'PUT', body: patch }); reload() } catch (ex) { setErr(ex.message) } }

  const techs = (users || []).filter((u) => u.role === 'TECHNICIAN')
  const others = (users || []).filter((u) => u.role !== 'TECHNICIAN')
  return (
    <div className="stack">
      <div className="row spread"><h1>Users &amp; technicians</h1><button className="primary" onClick={() => setModal(true)}>+ Create user</button></div>
      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
      <div className="card"><h3>Technicians</h3>
        <table><thead><tr><th>Name</th><th>Skills</th><th>Exp (yrs)</th><th>Workload</th><th>Available</th><th>Team lead</th><th>Active</th></tr></thead><tbody>
          {techs.map((t) => <tr key={t.id}>
            <td>{t.fullName}<div className="small muted mono">{t.username}</div></td>
            <td><div className="pill-group">{(categories || []).map((c) => <button key={c.id} className={`sm ${t.skills?.includes(c.name) ? 'on' : ''}`} onClick={() => {
              const ids = (categories || []).filter((x) => t.skills?.includes(x.name)).map((x) => x.id)
              const next = ids.includes(c.id) ? ids.filter((x) => x !== c.id) : [...ids, c.id]
              updateTech(t, { skillCategoryIds: next })
            }}>{c.name}</button>)}</div></td>
            <td><input type="number" min="0" style={{ width: 70 }} defaultValue={t.experienceYears} onBlur={(e) => Number(e.target.value) !== t.experienceYears && updateTech(t, { experienceYears: Number(e.target.value) })} /></td>
            <td>{t.activeWorkload} / <input type="number" min="1" max="50" style={{ width: 60 }} defaultValue={t.maxWorkload} onBlur={(e) => Number(e.target.value) !== t.maxWorkload && updateTech(t, { maxWorkload: Number(e.target.value) })} /></td>
            <td><input type="checkbox" style={{ width: 'auto' }} checked={t.available} onChange={() => updateTech(t, { available: !t.available })} /></td>
            <td><input type="checkbox" style={{ width: 'auto' }} checked={t.teamLead} onChange={() => updateTech(t, { teamLead: !t.teamLead })} /></td>
            <td><button className={`sm ${t.active ? 'danger' : ''}`} onClick={() => toggle(t)}>{t.active ? 'Deactivate' : 'Activate'}</button></td>
          </tr>)}
        </tbody></table></div>
      <div className="card"><h3>Employees &amp; administrators</h3>
        <table><thead><tr><th>Name</th><th>Role</th><th>Email</th><th>Phone</th><th>Department</th><th>Active</th></tr></thead><tbody>
          {others.map((u) => <tr key={u.id}><td>{u.fullName}<div className="small muted mono">{u.username}</div></td><td><span className="badge role">{u.role}</span></td><td>{u.email}</td><td>{u.phone || '—'}</td><td>{u.department || '—'}</td>
            <td><button className={`sm ${u.active ? 'danger' : ''}`} onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}
        </tbody></table></div>

      {modal && <Modal title="Create user" onClose={() => setModal(null)}>
        <form onSubmit={create}>
          <Alert kind="error">{err}</Alert>
          <div className="grid cols-2">
            <div className="field"><label>Role</label><select value={f.role} onChange={set('role')}>{['EMPLOYEE', 'TECHNICIAN', 'ADMIN'].map((r) => <option key={r}>{r}</option>)}</select></div>
            <div className="field"><label>Username</label><input value={f.username} onChange={set('username')} required /></div>
            <div className="field"><label>Password (min 8)</label><input type="password" value={f.password} onChange={set('password')} required /></div>
            <div className="field"><label>Full name</label><input value={f.fullName} onChange={set('fullName')} required /></div>
            <div className="field"><label>Email</label><input type="email" value={f.email} onChange={set('email')} required /></div>
            <div className="field"><label>Phone</label><input value={f.phone} onChange={set('phone')} /></div>
          </div>
          {f.role === 'EMPLOYEE' && <div className="field"><label>Department</label><input value={f.department} onChange={set('department')} /></div>}
          {f.role === 'TECHNICIAN' && <>
            <div className="grid cols-3">
              <div className="field"><label>Experience (yrs)</label><input type="number" min="0" value={f.experienceYears} onChange={set('experienceYears')} /></div>
              <div className="field"><label>Max workload</label><input type="number" min="1" value={f.maxWorkload} onChange={set('maxWorkload')} /></div>
              <div className="field"><label>Team lead</label><input type="checkbox" style={{ width: 'auto' }} checked={f.teamLead} onChange={set('teamLead')} /></div>
            </div>
            <div className="field"><label>Skills</label><div className="pill-group">{(categories || []).map((c) => <button type="button" key={c.id} className={`sm ${f.skillCategoryIds.includes(c.id) ? 'on' : ''}`}
              onClick={() => setF({ ...f, skillCategoryIds: f.skillCategoryIds.includes(c.id) ? f.skillCategoryIds.filter((x) => x !== c.id) : [...f.skillCategoryIds, c.id] })}>{c.name}</button>)}</div></div>
          </>}
          <div className="actions"><button className="primary">Create</button></div>
        </form>
      </Modal>}
    </div>
  )
}
