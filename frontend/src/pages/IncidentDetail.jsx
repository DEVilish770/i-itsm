import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { Alert, Badge, Modal, SlaMeter, useLoad } from '../components/ui.jsx'
import { useAuth } from '../lib/auth.jsx'

export default function IncidentDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const { data: i, reload, error } = useLoad(() => api(`/api/incidents/${id}`), [id])
  const { data: history, reload: reloadHistory } = useLoad(() => api(`/api/incidents/${id}/history`), [id])
  const isAdmin = user.role === 'ADMIN', isTech = user.role === 'TECHNICIAN', isEmp = user.role === 'EMPLOYEE'
  const [modal, setModal] = useState(null) // { action, label, fields }
  const [msg, setMsg] = useState(null)
  const [err, setErr] = useState(null)

  if (error) return <Alert kind="error">{error}</Alert>
  if (!i) return <p className="muted">Loading…</p>

  const allowed = new Set(i.allowedActions)
  const mine = i.technician?.id === user.id
  const canWork = isAdmin || (isTech && mine)
  const reporter = isAdmin || (isEmp && i.reporter?.id === user.id)

  async function act(path, body) {
    setErr(null); setMsg(null)
    try { await api(`/api/incidents/${id}/${path}`, { method: 'POST', body }); setMsg(`Done: ${path.replace('-', ' ')}`); setModal(null); reload(); reloadHistory() }
    catch (ex) { setErr(ex.message) }
  }

  const btn = (label, path, { cls = '', body, when = true, fields } = {}) => when && (
    <button key={path} className={cls} onClick={() => (fields ? setModal({ path, label, fields }) : act(path, body))}>{label}</button>)

  return (
    <div className="stack">
      <div className="row spread">
        <div><h1><span className="mono">{i.incidentNumber}</span> · {i.title}</h1>
          <div className="row"><Badge v={i.priority} /><Badge v={i.status} /><Badge v={i.slaStatus || i.sla.status} />{i.escalationLevel > 0 && <span className="badge VIOLATED">Escalated L{i.escalationLevel}</span>}{i.relatedIncidentNumber && <span className="badge AT_RISK">Related: {i.relatedIncidentNumber}</span>}</div></div>
      </div>
      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
      <Alert kind="ok" onClose={() => setMsg(null)}>{msg}</Alert>

      <div className="card">
        <h3>Actions {!canWork && !reporter && <span className="small muted">(view only)</span>}</h3>
        <div className="row">
          {btn('✅ Acknowledge', 'acknowledge', { cls: 'primary', when: canWork && allowed.has('acknowledge') })}
          {btn('🔍 Start investigation', 'start', { cls: 'primary', when: canWork && allowed.has('startInvestigation') })}
          {btn('❓ Request information', 'request-info', { when: canWork && allowed.has('requestInfo'), fields: [['text', 'Question for the reporter', 'textarea']] })}
          {btn('💬 Reply with information', 'provide-info', { cls: 'primary', when: reporter && allowed.has('provideInfo'), fields: [['text', 'Your answer', 'textarea']] })}
          {btn('🛠 Resolve', 'resolve', { cls: 'success', when: canWork && allowed.has('resolve'), fields: [['summary', 'Resolution summary', 'textarea'], ['rootCause', 'Root cause', 'input']] })}
          {btn('👍 Verify fix', 'verify', { cls: 'success', when: reporter && allowed.has('verify') })}
          {btn('↩ Reopen', 'reopen', { cls: 'danger', when: reporter && allowed.has('reopen'), fields: [['text', 'Why is it not fixed?', 'textarea']] })}
          {btn('🔒 Close', 'close', { when: allowed.has('close') && (isAdmin || (reporter && i.status === 'VERIFIED')), fields: [['text', 'Closure notes (optional)', 'textarea']] })}
          {btn('⭐ Give feedback', 'feedback', { when: isEmp && reporter && !i.feedback && ['RESOLVED', 'VERIFIED', 'CLOSED'].includes(i.status), fields: [['rating', 'Rating 1–5', 'number'], ['comment', 'Comment', 'textarea']] })}
          {btn('📝 Add note', 'notes', { when: (canWork || reporter) && i.status !== 'CLOSED', fields: [['text', 'Note', 'textarea']] })}
          {isAdmin && <AdminActions i={i} act={act} setModal={setModal} />}
        </div>
      </div>

      <div className="grid cols-2" style={{ gridTemplateColumns: '3fr 2fr' }}>
        <div className="stack">
          <div className="card">
            <h3>Details</h3>
            <p style={{ whiteSpace: 'pre-wrap' }}>{i.description}</p>
            <table><tbody>
              <tr><th>Service</th><td>{i.service?.name}</td><th>Category</th><td>{i.category?.name}</td></tr>
              <tr><th>Impact</th><td>{i.impact}</td><th>Urgency</th><td>{i.urgency}</td></tr>
              <tr><th>Affected users</th><td>{i.affectedUsers}</td><th>Priority score</th><td>{i.priorityScore}</td></tr>
              <tr><th>Reporter</th><td>{i.reporter?.name}</td><th>Technician</th><td>{i.technician?.name || '—'}</td></tr>
              <tr><th>Created</th><td>{fmt.dt(i.createdAt)}</td><th>Acknowledged</th><td>{fmt.dt(i.acknowledgedAt)}</td></tr>
            </tbody></table>
            <p className="small muted" style={{ marginTop: '.5rem' }}>Priority rationale: {i.priorityRationale}</p>
          </div>
          <div className="card">
            <h3>Investigation log</h3>
            {i.notes.length === 0 && <p className="muted small">No notes yet.</p>}
            {i.notes.map((n) => <div key={n.id} className={`note ${n.type}`}><div className="meta">{n.type.replace('_', ' ')} · {n.author?.name} ({n.authorRole}) · {fmt.dt(n.createdAt)}</div>{n.content}</div>)}
          </div>
          {i.resolution && <div className="card"><h3>Resolution</h3><p>{i.resolution.summary}</p>
            <p className="small muted">Root cause: {i.resolution.rootCause || '—'} · by {i.resolution.resolvedBy} at {fmt.dt(i.resolution.resolvedAt)}</p></div>}
          {i.feedback && <div className="card"><h3>Reporter feedback</h3><p>{'★'.repeat(i.feedback.rating)}{'☆'.repeat(5 - i.feedback.rating)} {i.feedback.comment}</p></div>}
          {i.closedAt && <div className="card"><h3>Closure</h3><p className="small">Closed by {i.closedBy?.name} at {fmt.dt(i.closedAt)}. {i.closureNotes}</p></div>}
        </div>
        <div className="stack">
          <div className="card"><h3>SLA</h3><SlaMeter sla={i.sla} />
            <table style={{ marginTop: '.5rem' }}><tbody>
              <tr><th>Response by</th><td className="small">{fmt.dt(i.sla.responseDeadline)}</td></tr>
              <tr><th>Responded</th><td className="small">{fmt.dt(i.sla.respondedAt)} {i.sla.responseViolated && <span className="badge VIOLATED">late</span>}</td></tr>
              <tr><th>Resolve by</th><td className="small">{fmt.dt(i.sla.resolutionDeadline)}</td></tr>
              <tr><th>Resolved</th><td className="small">{fmt.dt(i.sla.resolvedAt)} {i.sla.resolutionViolated && <span className="badge VIOLATED">late</span>}</td></tr>
            </tbody></table></div>
          {(canWork) && <Suggestions id={id} />}
          <div className="card"><h3>History (audit trail)</h3>
            <ul className="timeline">{(history || []).map((h) => <li key={h.id}><div className="when">{fmt.dt(h.timestamp)} · {h.actor}</div><b>{h.action.replace(/_/g, ' ')}</b>{h.previousState !== h.newState && <span className="small muted"> {h.previousState} → {h.newState}</span>}<div className="small">{h.description}</div></li>)}</ul></div>
        </div>
      </div>

      {modal && <Modal title={modal.label} onClose={() => setModal(null)}>
        <ModalForm fields={modal.fields} onSubmit={(body) => act(modal.path, modal.path === 'feedback' ? { ...body, rating: Number(body.rating) } : body)} />
      </Modal>}
    </div>
  )
}

function ModalForm({ fields, onSubmit }) {
  const [v, setV] = useState({})
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit(v) }}>
      {fields.map(([k, label, type]) => <div key={k} className="field"><label>{label}</label>
        {type === 'textarea' ? <textarea value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} autoFocus />
          : <input type={type} min={type === 'number' ? 1 : undefined} max={type === 'number' ? 5 : undefined} value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} autoFocus />}</div>)}
      <div className="actions"><button className="primary">Submit</button></div>
    </form>
  )
}

function Suggestions({ id }) {
  const { data: similar } = useLoad(() => api(`/api/incidents/${id}/similar`), [id])
  const { data: res } = useLoad(() => api(`/api/incidents/${id}/suggested-resolutions`), [id])
  return (
    <div className="card"><h3>🤖 Intelligence</h3>
      <b className="small">Related open incidents</b>
      {!similar?.length && <p className="muted small">None found on this service.</p>}
      {similar?.map((s) => <div key={s.id} className="small"><a href={`/incidents/${s.id}`}>{s.incidentNumber}</a> {s.title} · {s.similarity}% {s.likelyDuplicate && <span className="badge VIOLATED">likely duplicate</span>}</div>)}
      <b className="small" style={{ display: 'block', marginTop: '.6rem' }}>Suggested resolutions from history</b>
      {!res?.length && <p className="muted small">No similar resolved incidents yet.</p>}
      {res?.map((s) => <div key={s.incidentNumber} className="note small"><div className="meta">{s.incidentNumber} · {Math.round(s.similarity * 100)}% similar</div>{s.resolutionSummary}{s.rootCause && <div className="muted">Root cause: {s.rootCause}</div>}</div>)}
    </div>
  )
}

function AdminActions({ i, act, setModal }) {
  const [open, setOpen] = useState(false)
  const { data: recs } = useLoad(() => (open ? api(`/api/incidents/${i.id}/recommendations`) : Promise.resolve(null)), [open])
  const { data: strategies } = useLoad(() => api('/api/strategies'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const [strategy, setStrategy] = useState('')
  const [cat, setCat] = useState('')
  const [prio, setPrio] = useState('')
  const canAssign = i.allowedActions.includes('assign')
  return (<>
    {canAssign && <button onClick={() => setOpen(!open)}>👤 Assign technician…</button>}
    {i.status !== 'CLOSED' && i.status !== 'RESOLVED' && i.status !== 'VERIFIED' && <button onClick={() => setModal({ path: 'classify', label: 'Re-classify / override priority', fields: [] })} style={{ display: 'none' }} />}
    {open && <div className="card" style={{ width: '100%' }}>
      <div className="grid cols-2">
        <div>
          <b>Smart recommendation (ranked)</b>
          <table><thead><tr><th>Technician</th><th>Score</th><th>Load</th><th>Skill</th><th /></tr></thead><tbody>
            {(recs || []).map((r) => <tr key={r.technicianId}><td>{r.name}<div className="small muted">{r.experienceYears} yrs</div></td><td><b>{r.score}</b></td><td>{r.workload}/{r.maxWorkload}</td><td>{r.skillMatch ? '✔' : '—'}</td>
              <td><button className="sm primary" onClick={() => act('assign', { technicianId: r.technicianId, reason: 'Admin pick from recommendation list' })}>Assign</button></td></tr>)}
          </tbody></table>
        </div>
        <div>
          <b>Auto-assign with a strategy</b>
          <div className="field"><select value={strategy} onChange={(e) => setStrategy(e.target.value)}><option value="">Active: {strategies?.activeAssignment}</option>{strategies?.assignment.map((s) => <option key={s.name} value={s.name}>{s.name} — {s.description}</option>)}</select></div>
          <button className="primary" onClick={() => act('auto-assign', { strategy: strategy || null })}>Run strategy</button>
          <hr />
          <b>Re-classify</b>
          <div className="field"><label>Category</label><select value={cat} onChange={(e) => setCat(e.target.value)}><option value="">keep {i.category?.name}</option>{categories?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
          <div className="field"><label>Priority override</label><select value={prio} onChange={(e) => setPrio(e.target.value)}><option value="">recalculate with active strategy</option>{['P1', 'P2', 'P3', 'P4'].map((p) => <option key={p}>{p}</option>)}</select></div>
          <button onClick={() => act('classify', { categoryId: cat ? Number(cat) : null, priority: prio || null, reason: 'Admin re-classification' })}>Apply</button>
        </div>
      </div>
    </div>}
  </>)
}
