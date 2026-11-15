import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { Alert, Badge, useLoad } from '../components/ui.jsx'

const LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function NewIncident() {
  const nav = useNavigate()
  const { data: services } = useLoad(() => api('/api/services'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const [f, setF] = useState({ title: '', description: '', serviceId: '', categoryId: '', impact: 'MEDIUM', urgency: 'MEDIUM', affectedUsers: 1 })
  const [analysis, setAnalysis] = useState(null)
  const [err, setErr] = useState(null)
  const [dup, setDup] = useState(null)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  // Live analysis while typing: category suggestion + similar open incidents (intelligent features A & F).
  useEffect(() => {
    if (f.title.length < 4 && f.description.length < 10) { setAnalysis(null); return }
    const id = setTimeout(() => {
      api('/api/incidents/analyze', { method: 'POST', body: { title: f.title, description: f.description, serviceId: f.serviceId || null } })
        .then(setAnalysis).catch(() => {})
    }, 500)
    return () => clearTimeout(id)
  }, [f.title, f.description, f.serviceId])

  async function submit(e, force = false) {
    e?.preventDefault(); setErr(null); setDup(null)
    try {
      const r = await api('/api/incidents', { method: 'POST', body: {
        ...f, serviceId: Number(f.serviceId), categoryId: f.categoryId ? Number(f.categoryId) : null,
        affectedUsers: Number(f.affectedUsers), autoAssign: true, forceCreate: force } })
      nav(`/incidents/${r.id}`)
    } catch (ex) {
      if (ex.code === 'DUPLICATE_INCIDENT') setDup(ex.body)
      else setErr(ex.body?.fields ? Object.entries(ex.body.fields).map(([k, v]) => `${k}: ${v}`).join('; ') : ex.message)
    }
  }

  return (
    <div className="grid cols-2" style={{ gridTemplateColumns: '3fr 2fr' }}>
      <form className="card" onSubmit={submit}>
        <h1>Report an incident</h1>
        <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
        {dup && <div className="alert warn">
          <b>Possible duplicate.</b> {dup.message}
          <div className="row" style={{ marginTop: '.5rem' }}>
            {dup.candidates.map((c) => <span key={c} className="badge status">{c}</span>)}
            <button type="button" className="sm" onClick={(e) => submit(e, true)}>It's a different problem — submit anyway</button>
          </div>
        </div>}
        <div className="field"><label>Affected service *</label>
          <select value={f.serviceId} onChange={set('serviceId')} required>
            <option value="">Select…</option>
            {(services || []).filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name} ({s.criticality.replace('_', ' ').toLowerCase()})</option>)}
          </select></div>
        <div className="field"><label>Title *</label><input value={f.title} onChange={set('title')} maxLength={200} placeholder="e.g. Payment application unavailable" required /></div>
        <div className="field"><label>Description *</label><textarea value={f.description} onChange={set('description')} placeholder="What happened, since when, error messages, who is affected…" required /></div>
        <div className="grid cols-3">
          <div className="field"><label>Impact</label><select value={f.impact} onChange={set('impact')}>{LEVELS.map((l) => <option key={l}>{l}</option>)}</select></div>
          <div className="field"><label>Urgency</label><select value={f.urgency} onChange={set('urgency')}>{LEVELS.map((l) => <option key={l}>{l}</option>)}</select></div>
          <div className="field"><label>Affected users</label><input type="number" min="0" value={f.affectedUsers} onChange={set('affectedUsers')} /></div>
        </div>
        <div className="field"><label>Category (leave blank to let the system classify)</label>
          <select value={f.categoryId} onChange={set('categoryId')}>
            <option value="">Auto-detect</option>
            {(categories || []).filter((c) => c.active).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select></div>
        <div className="actions"><button type="button" onClick={() => nav(-1)}>Cancel</button><button className="primary">Submit incident</button></div>
      </form>

      <div className="stack">
        <div className="card">
          <h3>🤖 Suggested category</h3>
          {!analysis?.categorySuggestions?.length && <p className="muted small">Start typing — the system will suggest a category from keywords.</p>}
          {analysis?.categorySuggestions?.map((s) => (
            <div key={s.categoryId} className="bar">
              <span style={{ width: 130 }}>{s.name}</span>
              <div className="track"><div style={{ width: `${s.confidence}%` }} /></div>
              <span className="small muted" style={{ width: 40 }}>{s.confidence}%</span>
              <button type="button" className="sm" onClick={() => setF({ ...f, categoryId: String(s.categoryId) })}>use</button>
            </div>
          ))}
          {analysis?.categorySuggestions?.[0] && <p className="small muted">Matched: {analysis.categorySuggestions[0].keywords.join(', ')}</p>}
        </div>
        <div className="card">
          <h3>🔁 Similar open incidents</h3>
          {!analysis?.similarIncidents?.length && <p className="muted small">Pick a service and describe the problem to check for existing reports.</p>}
          {/* For other people's incidents an employee gets number, status and similarity only (no id, no title). */}
          {analysis?.similarIncidents?.map((s) => (
            <div key={s.incidentNumber} className="feed item">
              <div className="row spread"><b>{s.incidentNumber}</b><span className={`badge ${s.likelyDuplicate ? 'VIOLATED' : 'AT_RISK'}`}>{s.similarity}% similar</span></div>
              <div className="small">{s.title} <Badge v={s.status} /></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
