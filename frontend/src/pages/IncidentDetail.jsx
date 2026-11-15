import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errText, fmt } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { LEVEL_WORD, PRIORITIES, PRIORITY, sentence } from '../lib/words.js'
import { Alert, EmptyState, HealthWord, Modal, Page, PriorityWord, Section, SlaMeter, StatusWord, usePageTitle, useLoad, useNow } from '../components/ui.jsx'

const DONE_MSG = {
  acknowledge: 'Acknowledged. The response deadline is met.',
  start: 'Investigation started.',
  'request-info': 'Question sent to the reporter.',
  'provide-info': 'Reply sent to the technician.',
  resolve: 'Marked as resolved. The reporter has been asked to verify.',
  verify: 'Thanks for confirming the fix.',
  reopen: 'Reopened. The technician has been told.',
  close: 'Closed.',
  feedback: 'Thanks for the feedback.',
  notes: 'Note added.',
  assign: 'Assigned.',
  'auto-assign': 'Assigned by the active strategy.',
  classify: 'Re-classified.'
}
const NOTE_WORD = { INVESTIGATION: 'Investigation note', COMMENT: 'Comment', INFO_REQUEST: 'Question', INFO_RESPONSE: 'Answer' }

export default function IncidentDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const now = useNow(30000)
  const { data: i, reload, error } = useLoad(() => api(`/api/incidents/${id}`), [id])
  const { data: history, reload: reloadHistory } = useLoad(() => api(`/api/incidents/${id}/history`), [id])
  const isAdmin = user.role === 'ADMIN', isTech = user.role === 'TECHNICIAN', isEmp = user.role === 'EMPLOYEE'
  const [modal, setModal] = useState(null) // { path, label, fields }
  const [msg, setMsg] = useState(null)
  const [err, setErr] = useState(null)
  usePageTitle(i?.incidentNumber)

  if (error) return <Page intro="This incident could not be opened."><Alert kind="error">{error}</Alert></Page>
  if (!i) return <Page intro="Opening the incident…"><p className="quiet">Loading…</p></Page>

  const allowed = new Set(i.allowedActions)
  const mine = i.technician?.id === user.id
  const canWork = isAdmin || (isTech && mine)
  const reporter = isAdmin || (isEmp && i.reporter?.id === user.id)

  async function act(path, body) {
    setErr(null); setMsg(null)
    try {
      await api(`/api/incidents/${id}/${path}`, { method: 'POST', body })
      setMsg(DONE_MSG[path] || 'Done.'); setModal(null); reload(); reloadHistory()
    } catch (ex) { setErr(errText(ex)) }
  }

  const btn = (label, path, { cls = 'link', body, when = true, fields, tip } = {}) => when && (
    <button key={path} type="button" className={cls} title={tip} aria-label={label}
      onClick={() => { if (fields) { setErr(null); setModal({ path, label, fields }) } else act(path, body) }}>{label}</button>)

  const actions = [
    btn('Acknowledge', 'acknowledge', { cls: 'btn', when: canWork && allowed.has('acknowledge'), tip: 'Acknowledge: meets the SLA response deadline' }),
    btn('Start investigation', 'start', { cls: 'btn', when: canWork && allowed.has('startInvestigation'), tip: 'ACKNOWLEDGED → IN_PROGRESS' }),
    btn('Ask the reporter', 'request-info', { when: canWork && allowed.has('requestInfo'), fields: [['text', 'Your question for the reporter', 'textarea']], tip: 'Request information → PENDING_INFO' }),
    btn('Reply with information', 'provide-info', { cls: 'btn', when: reporter && allowed.has('provideInfo'), fields: [['text', 'Your answer', 'textarea']], tip: 'Provide information → IN_PROGRESS' }),
    btn('Resolve', 'resolve', { cls: 'btn', when: canWork && allowed.has('resolve'), fields: [['summary', 'How was it fixed?', 'textarea'], ['rootCause', 'Root cause', 'input']], tip: 'Resolve → RESOLVED' }),
    btn('Verify fix', 'verify', { cls: 'btn', when: reporter && allowed.has('verify'), tip: 'Verify → VERIFIED' }),
    btn('Reopen', 'reopen', { cls: 'link danger', when: reporter && allowed.has('reopen'), fields: [['text', "What's still not working?", 'textarea']], tip: 'Reopen → IN_PROGRESS' }),
    btn('Close', 'close', { when: allowed.has('close') && (isAdmin || (reporter && i.status === 'VERIFIED')), fields: [['text', 'Closing notes (optional)', 'textarea']], tip: 'Close → CLOSED' }),
    btn('Give feedback', 'feedback', { when: isEmp && reporter && !i.feedback && ['RESOLVED', 'VERIFIED', 'CLOSED'].includes(i.status), fields: [['rating', 'Rating, 1 to 5', 'number'], ['comment', 'Anything to add?', 'textarea']] }),
    btn('Add a note', 'notes', { when: (canWork || reporter) && i.status !== 'CLOSED', fields: [['text', 'Note', 'textarea']] })
  ].filter(Boolean)

  const p = PRIORITY[i.priority]
  const intro = <>Reported by {i.reporter?.name || 'someone'} · {fmt.ago(i.createdAt)}</>

  return (
    <Page intro={intro}>
      <header className="detail-head" style={{ '--bar': p ? `var(--${p.tone})` : 'var(--hairline)' }}>
        <div className="detail-kicker">
          <span className="irow-num">{i.incidentNumber}</span>
          <span className="tech" title="Lifecycle state (State pattern)">{i.status}</span>
          {i.priority && <span className="tech" title={`Priority ${p?.tech}`}>{i.priority}</span>}
        </div>
        <h2 className="detail-title">{i.title}</h2>
        <div className="detail-line">
          <PriorityWord p={i.priority} />
          <StatusWord s={i.status} />
          <HealthWord s={i.slaStatus || i.sla?.status} />
          {i.escalationLevel > 0 && <span className="tone-late" title={`escalationLevel ${i.escalationLevel}`}>Escalated, level {i.escalationLevel}</span>}
          {i.relatedIncidentNumber && <span className="quiet" title="Linked as a likely duplicate">Related to {i.relatedIncidentNumber}</span>}
        </div>
      </header>

      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
      <Alert kind="ok" onClose={() => setMsg(null)}>{msg}</Alert>

      <div className="actions-bar">
        {actions.length ? actions : <span className="quiet">{!canWork && !reporter ? 'View only.' : 'Nothing to do right now.'}</span>}
        {isAdmin && <AdminActions i={i} act={act} />}
      </div>

      <div className="split detail">
        <div>
          <Section title="What happened">
            <p className="prose">{i.description}</p>
            <dl className="kv">
              <div><dt>Service</dt><dd>{i.service?.name || '—'}</dd></div>
              <div><dt>Category</dt><dd>{i.category?.name || '—'}</dd></div>
              <div><dt>Impact</dt><dd title={i.impact}>{LEVEL_WORD[i.impact] || i.impact}</dd></div>
              <div><dt>Urgency</dt><dd title={i.urgency}>{LEVEL_WORD[i.urgency] || i.urgency}</dd></div>
              <div><dt>People affected</dt><dd>{i.affectedUsers}</dd></div>
              <div><dt>Priority score</dt><dd>{i.priorityScore ?? '—'}</dd></div>
              <div><dt>Reporter</dt><dd>{i.reporter?.name || '—'}</dd></div>
              <div><dt>Technician</dt><dd>{i.technician?.name || <span className="quiet">Not assigned yet</span>}</dd></div>
              <div><dt>Reported</dt><dd>{fmt.dt(i.createdAt)}</dd></div>
              <div><dt>Acknowledged</dt><dd>{fmt.dt(i.acknowledgedAt)}</dd></div>
            </dl>
            {i.priorityRationale && <p className="rationale"><span className="label">Why this priority</span> {i.priorityRationale}</p>}
          </Section>

          <Section title="Investigation log">
            {i.notes.length === 0 && <p className="quiet">No notes yet.</p>}
            <div className="notes">
              {i.notes.map((n) => (
                <div key={n.id} className={`note ${n.type}`}>
                  <div className="note-meta"><span title={n.type}>{NOTE_WORD[n.type] || sentence(n.type)}</span> · {n.author?.name} <span className="quiet">({sentence(n.authorRole)})</span> · {fmt.dt(n.createdAt)}</div>
                  <div className="prose">{n.content}</div>
                </div>
              ))}
            </div>
          </Section>

          {i.resolution && (
            <Section title="How it was fixed" note="Resolution">
              <p className="prose">{i.resolution.summary}</p>
              <p className="quiet">Root cause: {i.resolution.rootCause || '—'} · by {i.resolution.resolvedBy} · {fmt.dt(i.resolution.resolvedAt)}</p>
            </Section>
          )}
          {i.feedback && (
            <Section title="Reporter feedback">
              <p><span className="stars" aria-label={`${i.feedback.rating} out of 5`}>{'★'.repeat(i.feedback.rating)}<span className="stars-off">{'★'.repeat(5 - i.feedback.rating)}</span></span> {i.feedback.comment}</p>
            </Section>
          )}
          {i.closedAt && (
            <Section title="Closed">
              <p className="quiet">Closed by {i.closedBy?.name} · {fmt.dt(i.closedAt)}.{i.closureNotes ? ` ${i.closureNotes}` : ''}</p>
            </Section>
          )}
        </div>

        <div>
          <Section title="Time" note="SLA">
            <SlaMeter incident={i} now={now} />
            <dl className="kv one">
              <div><dt title="SLA response deadline">Response due</dt><dd>{fmt.dt(i.sla?.responseDeadline)}</dd></div>
              <div><dt>Responded</dt><dd>{fmt.dt(i.sla?.respondedAt)}{i.sla?.responseViolated && <span className="tone-late"> · late</span>}</dd></div>
              <div><dt title="SLA resolution deadline">Fix due</dt><dd>{fmt.dt(i.sla?.resolutionDeadline)}</dd></div>
              <div><dt>Resolved</dt><dd>{fmt.dt(i.sla?.resolvedAt)}{i.sla?.resolutionViolated && <span className="tone-late"> · late</span>}</dd></div>
            </dl>
            <p className="tech-line">SLA status {i.sla?.status || '—'} · {Math.round(i.sla?.percentRemaining ?? 0)}% of the time remaining</p>
          </Section>
          {canWork && <Suggestions id={id} />}
          <Section title="History" note="Audit trail">
            {!history?.length && <p className="quiet">No history yet.</p>}
            <ol className="timeline">
              {(history || []).map((h) => (
                <li key={h.id}>
                  <div className="tl-when">{fmt.dt(h.timestamp)} · {h.actor}</div>
                  <div className="tl-what"><span title={h.action}>{sentence(h.action)}</span>
                    {h.previousState !== h.newState && <span className="tech"> {h.previousState} → {h.newState}</span>}</div>
                  {h.description && <div className="tl-desc">{h.description}</div>}
                </li>
              ))}
            </ol>
          </Section>
        </div>
      </div>

      {modal && (
        <Modal title={modal.label} onClose={() => setModal(null)}>
          <ModalForm fields={modal.fields} error={err} onSubmit={(body) => act(modal.path, modal.path === 'feedback' ? { ...body, rating: Number(body.rating) } : body)} />
        </Modal>
      )}
    </Page>
  )
}

function ModalForm({ fields, onSubmit, error }) {
  const [v, setV] = useState({})
  const [busy, setBusy] = useState(false)
  return (
    <form onSubmit={async (e) => { e.preventDefault(); setBusy(true); try { await onSubmit(v) } finally { setBusy(false) } }}>
      {error && <p className="pop-err">{error}</p>}
      {fields.map(([k, label, type], idx) => (
        <div key={k} className="field"><label htmlFor={`m-${k}`}>{label}</label>
          {type === 'textarea'
            ? <textarea id={`m-${k}`} value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} autoFocus={idx === 0} />
            : <input id={`m-${k}`} type={type} min={type === 'number' ? 1 : undefined} max={type === 'number' ? 5 : undefined} value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} autoFocus={idx === 0} />}
        </div>
      ))}
      <div className="form-actions"><button className="btn" disabled={busy}>{busy ? 'Sending…' : 'Send'}</button></div>
    </form>
  )
}

function Suggestions({ id }) {
  const { data: similar } = useLoad(() => api(`/api/incidents/${id}/similar`), [id])
  const { data: res } = useLoad(() => api(`/api/incidents/${id}/suggested-resolutions`), [id])
  return (
    <Section title="Help from history" note="Intelligence">
      <div className="label">Related open incidents</div>
      {!similar?.length && <p className="quiet">None found on this service.</p>}
      <div className="similar">
        {similar?.map((s) => (
          <div key={s.id ?? s.incidentNumber} className={`similar-row${s.likelyDuplicate ? ' likely' : ''}`}>
            <div className="similar-top">
              {s.id ? <Link to={`/incidents/${s.id}`} className="irow-num">{s.incidentNumber}</Link> : <span className="irow-num">{s.incidentNumber}</span>}
              <span className={s.likelyDuplicate ? 'tone-late' : 'quiet'}>{s.similarity}% similar{s.likelyDuplicate ? ' · likely duplicate' : ''}</span>
            </div>
            {s.title && <div className="similar-what">{s.title}</div>}
          </div>
        ))}
      </div>
      <div className="label spaced">Fixes that worked before</div>
      {!res?.length && <p className="quiet">No similar resolved incidents yet.</p>}
      <div className="notes">
        {res?.map((s) => (
          <div key={s.incidentNumber} className="note fix">
            <div className="note-meta">{s.incidentNumber} · {Math.round(s.similarity * 100)}% similar <span className="tech">suggested resolution</span></div>
            <div className="prose">{s.resolutionSummary}</div>
            {s.rootCause && <div className="quiet">Root cause: {s.rootCause}</div>}
          </div>
        ))}
      </div>
    </Section>
  )
}

function AdminActions({ i, act }) {
  const [open, setOpen] = useState(false)
  const { data: recs } = useLoad(() => (open ? api(`/api/incidents/${i.id}/recommendations`) : Promise.resolve(null)), [open])
  const { data: strategies } = useLoad(() => api('/api/strategies'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const [strategy, setStrategy] = useState('')
  const [cat, setCat] = useState('')
  const [prio, setPrio] = useState('')
  const canAssign = i.allowedActions.includes('assign')
  return (<>
    {canAssign && <button type="button" className={`link${open ? ' accent' : ''}`} onClick={() => setOpen(!open)} aria-expanded={open}>Assign or re-classify…</button>}
    {open && (
      <div className="admin-panel">
        <div>
          <div className="label">Smart recommendation <span className="tech">ranked</span></div>
          {!recs && <p className="quiet">Ranking technicians…</p>}
          {recs && recs.length === 0 && <EmptyState compact title="No one available.">Every technician is busy or away.</EmptyState>}
          {recs && recs.length > 0 && (
            <table className="table">
              <thead><tr><th>Technician</th><th>Score</th><th>Load</th><th>Skill</th><th /></tr></thead>
              <tbody>
                {recs.map((r) => (
                  <tr key={r.technicianId}>
                    <td>{r.name}<div className="quiet">{r.experienceYears} yrs</div></td>
                    <td className="num">{r.score}</td>
                    <td className="num">{r.workload} / {r.maxWorkload}</td>
                    <td>{r.skillMatch ? 'Matches' : <span className="quiet">—</span>}</td>
                    <td className="right"><button type="button" className="btn sm" onClick={() => act('assign', { technicianId: r.technicianId, reason: 'Admin pick from recommendation list' })}>Assign</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div>
          <div className="label">Let a strategy choose</div>
          <div className="field"><select value={strategy} onChange={(e) => setStrategy(e.target.value)} aria-label="Assignment strategy">
            <option value="">Active: {sentence(strategies?.activeAssignment)}</option>
            {strategies?.assignment.map((s) => <option key={s.name} value={s.name} title={s.name}>{sentence(s.name)} — {s.description}</option>)}
          </select></div>
          <button type="button" className="btn" onClick={() => act('auto-assign', { strategy: strategy || null })}>Run strategy</button>
          <div className="label spaced">Re-classify</div>
          <div className="field"><label htmlFor="rc-cat">Category</label><select id="rc-cat" value={cat} onChange={(e) => setCat(e.target.value)}>
            <option value="">Keep {i.category?.name}</option>
            {categories?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select></div>
          <div className="field"><label htmlFor="rc-pri">Priority override</label><select id="rc-pri" value={prio} onChange={(e) => setPrio(e.target.value)}>
            <option value="">Recalculate with the active strategy</option>
            {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY[p].word} ({p})</option>)}
          </select></div>
          <button type="button" className="link accent" onClick={() => act('classify', { categoryId: cat ? Number(cat) : null, priority: prio || null, reason: 'Admin re-classification' })}>Apply</button>
        </div>
      </div>
    )}
  </>)
}
