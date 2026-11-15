import { useState } from 'react'
import { api, errText } from '../lib/api.js'
import { ROLE_WORD } from '../lib/words.js'
import { Alert, Choice, Modal, Page, Section, useLoad } from '../components/ui.jsx'

const ROLES = ['EMPLOYEE', 'TECHNICIAN', 'ADMIN']

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
    } catch (ex) { setErr(errText(ex)) }
  }
  async function toggle(u) {
    try { setErr(null); await api(`/api/admin/users/${u.id}/active?active=${!u.active}`, { method: 'PATCH' }); reload() } catch (ex) { setErr(errText(ex)) }
  }
  async function updateTech(t, patch) { try { await api(`/api/admin/technicians/${t.id}`, { method: 'PUT', body: patch }); reload() } catch (ex) { setErr(ex.message) } }

  const techs = (users || []).filter((u) => u.role === 'TECHNICIAN')
  const others = (users || []).filter((u) => u.role !== 'TECHNICIAN')
  return (
    <Page intro="Who can sign in, and what each technician can take on." actions={<button type="button" className="btn" onClick={() => { setErr(null); setModal(true) }}>Add a person</button>}>
      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>

      <Section title="Technicians" note="Skills decide who gets which work">
        <div className="table-scroll">
          <table className="table">
            <thead><tr><th>Name</th><th>Skills</th><th>Experience</th><th title="Active workload / maximum workload">Workload</th><th>Available</th><th>Team lead</th><th /></tr></thead>
            <tbody>
              {techs.map((t) => (
                <tr key={t.id} className={t.active ? '' : 'inactive'}>
                  <td>{t.fullName}<div className="quiet mono">{t.username}</div></td>
                  <td>
                    <div className="skills">
                      {(categories || []).map((c) => {
                        const on = !!t.skills?.includes(c.name)
                        return (
                          <button key={c.id} type="button" className={`skill${on ? ' on' : ''}`} aria-pressed={on} title={on ? `Remove ${c.name}` : `Add ${c.name}`} onClick={() => {
                            const ids = (categories || []).filter((x) => t.skills?.includes(x.name)).map((x) => x.id)
                            const next = ids.includes(c.id) ? ids.filter((x) => x !== c.id) : [...ids, c.id]
                            updateTech(t, { skillCategoryIds: next })
                          }}>{c.name}</button>
                        )
                      })}
                    </div>
                  </td>
                  <td><input className="inline-num" type="number" min="0" defaultValue={t.experienceYears} aria-label={`${t.fullName} years of experience`}
                    onBlur={(e) => Number(e.target.value) !== t.experienceYears && updateTech(t, { experienceYears: Number(e.target.value) })} /> <span className="quiet">yrs</span></td>
                  <td className="nowrap">{t.activeWorkload} / <input className="inline-num" type="number" min="1" max="50" defaultValue={t.maxWorkload} aria-label={`${t.fullName} maximum workload`}
                    onBlur={(e) => Number(e.target.value) !== t.maxWorkload && updateTech(t, { maxWorkload: Number(e.target.value) })} /></td>
                  <td><input type="checkbox" checked={t.available} onChange={() => updateTech(t, { available: !t.available })} aria-label={`${t.fullName} available`} /></td>
                  <td><input type="checkbox" checked={t.teamLead} onChange={() => updateTech(t, { teamLead: !t.teamLead })} aria-label={`${t.fullName} team lead`} /></td>
                  <td className="right"><button type="button" className={`link${t.active ? ' danger' : ' accent'}`} onClick={() => toggle(t)}>{t.active ? 'Deactivate' : 'Activate'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section title="Employees and administrators">
        <div className="table-scroll">
          <table className="table">
            <thead><tr><th>Name</th><th>Role</th><th>Email</th><th>Phone</th><th>Department</th><th /></tr></thead>
            <tbody>
              {others.map((u) => (
                <tr key={u.id} className={u.active ? '' : 'inactive'}>
                  <td>{u.fullName}<div className="quiet mono">{u.username}</div></td>
                  <td title={u.role}>{ROLE_WORD[u.role] || u.role}</td>
                  <td>{u.email}</td><td>{u.phone || '—'}</td><td>{u.department || '—'}</td>
                  <td className="right"><button type="button" className={`link${u.active ? ' danger' : ' accent'}`} onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      {modal && (
        <Modal title="Add a person" onClose={() => setModal(null)}>
          <form onSubmit={create}>
            <Alert kind="error">{err}</Alert>
            <div className="field"><span className="label-like">Role</span>
              <Choice label="Role" value={f.role} options={ROLES.map((r) => [r, ROLE_WORD[r]])} onChange={(v) => setF({ ...f, role: v })} /></div>
            <div className="grid-2">
              <div className="field"><label htmlFor="nu">Username</label><input id="nu" value={f.username} onChange={set('username')} autoComplete="off" required /></div>
              <div className="field"><label htmlFor="np">Password, at least 8 characters</label><input id="np" type="password" value={f.password} onChange={set('password')} autoComplete="new-password" required /></div>
              <div className="field"><label htmlFor="nn">Full name</label><input id="nn" value={f.fullName} onChange={set('fullName')} required /></div>
              <div className="field"><label htmlFor="ne">Email</label><input id="ne" type="email" value={f.email} onChange={set('email')} required /></div>
              <div className="field"><label htmlFor="nph">Phone</label><input id="nph" value={f.phone} onChange={set('phone')} /></div>
              {f.role === 'EMPLOYEE' && <div className="field"><label htmlFor="nd">Department</label><input id="nd" value={f.department} onChange={set('department')} /></div>}
            </div>
            {f.role === 'TECHNICIAN' && <>
              <div className="grid-3">
                <div className="field"><label htmlFor="nx">Experience, years</label><input id="nx" type="number" min="0" value={f.experienceYears} onChange={set('experienceYears')} /></div>
                <div className="field"><label htmlFor="nm">Most incidents at once</label><input id="nm" type="number" min="1" value={f.maxWorkload} onChange={set('maxWorkload')} /></div>
                <div className="field check"><label htmlFor="nt"><input id="nt" type="checkbox" checked={f.teamLead} onChange={set('teamLead')} /> Team lead</label></div>
              </div>
              <div className="field"><span className="label-like">Skills</span>
                <div className="skills">{(categories || []).map((c) => {
                  const on = f.skillCategoryIds.includes(c.id)
                  return <button type="button" key={c.id} className={`skill${on ? ' on' : ''}`} aria-pressed={on}
                    onClick={() => setF({ ...f, skillCategoryIds: on ? f.skillCategoryIds.filter((x) => x !== c.id) : [...f.skillCategoryIds, c.id] })}>{c.name}</button>
                })}</div></div>
            </>}
            <div className="form-actions"><button type="button" className="link" onClick={() => setModal(null)}>Cancel</button><button className="btn">Create</button></div>
          </form>
        </Modal>
      )}
    </Page>
  )
}
