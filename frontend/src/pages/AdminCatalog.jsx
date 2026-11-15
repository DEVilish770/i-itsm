import { useState } from 'react'
import { api, errText } from '../lib/api.js'
import { AI_REASON, modelName, setAiEnabled, useAi } from '../lib/ai.jsx'
import { PRIORITY, sentence } from '../lib/words.js'
import { Alert, Modal, Page, Section, Switch, useLoad } from '../components/ui.jsx'

const CRIT = ['LOW', 'MEDIUM', 'HIGH', 'MISSION_CRITICAL']

export default function AdminCatalog() {
  const { data: services, reload: rs } = useLoad(() => api('/api/services'))
  const { data: categories, reload: rc } = useLoad(() => api('/api/categories'))
  const { data: slas, reload: rl } = useLoad(() => api('/api/sla-policies'))
  const { data: strategies, reload: rst } = useLoad(() => api('/api/strategies'))
  const [err, setErr] = useState(null)
  const [ok, setOk] = useState(null)
  const [modal, setModal] = useState(null)
  const run = async (fn, msg) => { setErr(null); setOk(null); try { await fn(); setOk(msg) } catch (ex) { setErr(errText(ex)) } }

  return (
    <Page intro="Services, categories, deadlines and how work is shared. Changes apply at once, no restart.">
      <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
      <Alert kind="ok" onClose={() => setOk(null)}>{ok}</Alert>

      <AiSetting />

      <Section title="How work is shared" note="Strategy pattern: switch at runtime, no redeploy">
        {strategies && (
          <div className="grid-2">
            <div className="field"><label htmlFor="sa">Choosing a technician</label>
              <select id="sa" value={strategies.activeAssignment} title={`Assignment strategy: ${strategies.activeAssignment}`} onChange={(e) => run(async () => { await api('/api/admin/strategies', { method: 'PUT', body: { assignmentStrategy: e.target.value } }); rst() }, 'Assignment strategy updated.')}>
                {strategies.assignment.map((s) => <option key={s.name} value={s.name} title={s.name}>{sentence(s.name)} — {s.description}</option>)}
              </select></div>
            <div className="field"><label htmlFor="sp">Working out priority</label>
              <select id="sp" value={strategies.activePriority} title={`Priority strategy: ${strategies.activePriority}`} onChange={(e) => run(async () => { await api('/api/admin/strategies', { method: 'PUT', body: { priorityStrategy: e.target.value } }); rst() }, 'Priority strategy updated.')}>
                {strategies.priority.map((s) => <option key={s.name} value={s.name} title={s.name}>{sentence(s.name)} — {s.description}</option>)}
              </select></div>
          </div>
        )}
      </Section>

      <Section title="Deadlines by priority" note="SLA policies, in minutes">
        <div className="table-scroll">
          <table className="table">
            <thead><tr><th>Priority</th><th title="Response target (min)">Respond within</th><th title="Resolution target (min)">Fix within</th><th title="Warn when % remaining ≤">Warn when time left is below</th><th /></tr></thead>
            <tbody>
              {(slas || []).map((p) => <SlaRow key={p.priority} p={p} onSave={(body) => run(async () => { await api('/api/admin/sla-policies', { method: 'PUT', body }); rl() }, `Deadlines for ${PRIORITY[p.priority]?.word || p.priority} (${p.priority}) saved.`)} />)}
            </tbody>
          </table>
        </div>
      </Section>

      <div className="duo">
        <Section title="Services" note="IT services" right={<button type="button" className="link accent" onClick={() => setModal({ kind: 'service' })}>Add a service</button>}>
          <div className="table-scroll">
            <table className="table">
              <thead><tr><th>Name</th><th>Criticality</th><th>Owner</th><th>Users</th><th /></tr></thead>
              <tbody>
                {(services || []).map((s) => (
                  <tr key={s.id} className={s.active ? '' : 'inactive'}>
                    <td>{s.name}<div className="quiet">{s.description}</div></td>
                    <td title={s.criticality}>{s.criticality.replace('_', ' ').toLowerCase()}</td><td>{s.owner}</td><td className="num">{s.userBase}</td>
                    <td className="right"><button type="button" className={`link${s.active ? ' danger' : ' accent'}`} onClick={() => run(async () => { await api(`/api/admin/services/${s.id}`, { method: 'PUT', body: { ...s, active: !s.active } }); rs() }, 'Service updated.')}>{s.active ? 'Retire' : 'Reactivate'}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>
        <Section title="Categories" note="Words that sort new reports" right={<button type="button" className="link accent" onClick={() => setModal({ kind: 'category' })}>Add a category</button>}>
          <div className="table-scroll">
            <table className="table">
              <thead><tr><th>Name</th><th title="Auto-classification keywords">Keywords</th><th /></tr></thead>
              <tbody>
                {(categories || []).map((c) => (
                  <tr key={c.id} className={c.active ? '' : 'inactive'}>
                    <td>{c.name}<div className="quiet">{c.description}</div></td>
                    <td><textarea className="keywords" defaultValue={c.keywords} aria-label={`${c.name} keywords`} onBlur={(e) => e.target.value !== c.keywords && run(async () => { await api(`/api/admin/categories/${c.id}`, { method: 'PUT', body: { ...c, keywords: e.target.value } }); rc() }, 'Keywords saved.')} /></td>
                    <td className="right"><button type="button" className={`link${c.active ? ' danger' : ' accent'}`} onClick={() => run(async () => { await api(`/api/admin/categories/${c.id}`, { method: 'PUT', body: { ...c, active: !c.active } }); rc() }, 'Category updated.')}>{c.active ? 'Disable' : 'Enable'}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>
      </div>

      {modal?.kind === 'service' && <Modal title="Add a service" onClose={() => setModal(null)}>
        <SimpleForm fields={[['name', 'Name'], ['description', 'Description'], ['criticality', 'How critical it is to the business', CRIT, 'MEDIUM'],['owner', 'Owner team'], ['userBase', 'About how many users', 'number']]}
          onSubmit={(b) => run(async () => { await api('/api/admin/services', { method: 'POST', body: { ...b, userBase: Number(b.userBase || 0), criticality: b.criticality || 'MEDIUM' } }); rs(); setModal(null) }, 'Service created.')} /></Modal>}
      {modal?.kind === 'category' && <Modal title="Add a category" onClose={() => setModal(null)}>
        <SimpleForm fields={[['name', 'Name'], ['description', 'Description'], ['keywords', 'Keywords, comma separated']]}
          onSubmit={(b) => run(async () => { await api('/api/admin/categories', { method: 'POST', body: b }); rc(); setModal(null) }, 'Category created. Technicians can now be given this skill.')} /></Modal>}
    </Page>
  )
}

/** The admin's AI on/off switch (PUT /api/admin/ai), so rules mode can be shown live. */
function AiSetting() {
  const { status, setStatus } = useAi()
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)
  const [pending, setPending] = useState(null) // the position the switch is moving to
  const enabled = pending ?? (status ? status.reason !== 'DISABLED' : true)

  async function flip(next) {
    setBusy(true); setErr(null); setPending(next)
    try { setStatus(await setAiEnabled(next)) }
    catch { setErr("The AI setting couldn't be changed right now. Everything keeps working with the built-in rules.") }
    finally { setPending(null); setBusy(false) }
  }

  const line = !status ? 'Checking…'
    : status.mode === 'LIVE' ? `On, and live${status.model ? ` with ${modelName(status.model)}` : ''}${status.callsToday != null && status.dailyLimit ? `. ${status.callsToday} of ${status.dailyLimit} calls used today.` : '.'}`
      : status.reason === 'DISABLED' ? 'Off. Built-in rules are doing the work.'
        : `On, but in rules mode for now: ${AI_REASON[status.reason] || AI_REASON.UNAVAILABLE}.`

  return (
    <Section title="AI assistance" note="Screenshots, chat reports, plain-English search, summaries">
      <div className="setting">
        <div>
          <p>When it's off, the built-in rules do the same jobs, so everything still works.</p>
          <p className="quiet" title={status ? `mode ${status.mode}${status.reason ? ` · reason ${status.reason}` : ''}` : ''}>{line}</p>
          {err && <p className="tone-late">{err}</p>}
        </div>
        <Switch on={enabled} disabled={busy || !status} onChange={flip} label="AI assistance" />
      </div>
    </Section>
  )
}

function SlaRow({ p, onSave }) {
  const [v, setV] = useState(p)
  const m = PRIORITY[p.priority]
  return (
    <tr>
      <td><span className={`prio tone-${m?.tone}`}>{m?.word || p.priority}<small>{p.priority}</small></span></td>
      <td><input className="inline-num" type="number" min="1" value={v.responseMinutes} onChange={(e) => setV({ ...v, responseMinutes: Number(e.target.value) })} aria-label={`${p.priority} response minutes`} /> <span className="quiet">min</span></td>
      <td><input className="inline-num" type="number" min="1" value={v.resolutionMinutes} onChange={(e) => setV({ ...v, resolutionMinutes: Number(e.target.value) })} aria-label={`${p.priority} resolution minutes`} /> <span className="quiet">min</span></td>
      <td><input className="inline-num" type="number" min="1" max="99" value={v.warningThresholdPct} onChange={(e) => setV({ ...v, warningThresholdPct: Number(e.target.value) })} aria-label={`${p.priority} warning threshold percent`} /> <span className="quiet">%</span></td>
      <td className="right"><button type="button" className="link accent" onClick={() => onSave(v)}>Save</button></td>
    </tr>
  )
}

function SimpleForm({ fields, onSubmit }) {
  // A select shows its default from the start, and that same value is sent if it is left untouched.
  const [v, setV] = useState(() => Object.fromEntries(fields.filter(([, , t]) => Array.isArray(t)).map(([k, , t, d]) => [k, d ?? t[0]])))
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit(v) }}>
      {fields.map(([k, label, type]) => (
        <div key={k} className="field"><label htmlFor={`sf-${k}`}>{label}</label>
          {Array.isArray(type)
            ? <select id={`sf-${k}`} value={v[k] || type[0]} onChange={(e) => setV({ ...v, [k]: e.target.value })}>{type.map((o) => <option key={o} value={o}>{o.replace('_', ' ').toLowerCase()}</option>)}</select>
            : <input id={`sf-${k}`} type={type || 'text'} value={v[k] || ''} onChange={(e) => setV({ ...v, [k]: e.target.value })} required={k === 'name'} />}
        </div>
      ))}
      <div className="form-actions"><button className="btn">Save</button></div>
    </form>
  )
}
