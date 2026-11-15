import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errText } from '../lib/api.js'
import { useAiMode } from '../lib/ai.jsx'
import { LEVELS, LEVEL_WORD, cleanDraft } from '../lib/words.js'
import { Alert, Bars, Choice, Page, Section, StatusWord, Tag, useLoad } from '../components/ui.jsx'
import ScreenshotDrop from '../components/ScreenshotDrop.jsx'
import ChatSheet from '../components/ChatSheet.jsx'
import { Icon } from '../components/icons.jsx'

const START = { title: '', description: '', serviceId: '', categoryId: '', impact: 'MEDIUM', urgency: 'MEDIUM', affectedUsers: 1 }
const LEVEL_OPTIONS = LEVELS.map((l) => [l, LEVEL_WORD[l]])
const AI_HINT = 'Filled in by AI. Change it if it is not right.'

/** A title from a chat message: its first line, cut at a word to at most 80 characters. */
function shortTitle(s) {
  const line = String(s).split('\n')[0].trim()
  if (line.length <= 80) return line
  const cut = line.slice(0, 80), space = cut.lastIndexOf(' ')
  return (space > 40 ? cut.slice(0, space) : cut).trim()
}

export default function NewIncident() {
  const nav = useNavigate()
  const rules = useAiMode() === 'RULES'
  const { data: services } = useLoad(() => api('/api/services'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  const [f, setF] = useState(START)
  const [aiFields, setAiFields] = useState(() => new Set()) // fields last filled by AI (tag goes once edited)
  const [analysis, setAnalysis] = useState(null)
  const [err, setErr] = useState(null)
  const [dup, setDup] = useState(null)
  const [busy, setBusy] = useState(false)
  const [chat, setChat] = useState(false)
  const [dropKey, setDropKey] = useState(0) // a chat draft replaces the form, so the screenshot box starts over

  const unmark = (k) => setAiFields((s) => { if (!s.has(k)) return s; const n = new Set(s); n.delete(k); return n })
  const setVal = (k, v) => { setF((p) => ({ ...p, [k]: v })); unmark(k) }
  const set = (k) => (e) => setVal(k, e.target.value)
  const ai = (k) => aiFields.has(k) && <Tag source="AI" title={AI_HINT} />

  // Live analysis while typing: category suggestion + similar open incidents (intelligent features A & F).
  useEffect(() => {
    if (f.title.length < 4 && f.description.length < 10) { setAnalysis(null); return }
    const id = setTimeout(() => {
      api('/api/incidents/analyze', { method: 'POST', body: { title: f.title, description: f.description, serviceId: f.serviceId || null } })
        .then(setAnalysis).catch(() => {})
    }, 500)
    return () => clearTimeout(id)
  }, [f.title, f.description, f.serviceId])

  /** An AI draft (screenshot or chat) fills the form; each filled field gets a small AI tag. */
  const applyDraft = useCallback((draft) => {
    const clean = cleanDraft(draft, { services, categories })
    const keys = Object.keys(clean)
    if (!keys.length) return
    setF((p) => ({ ...p, ...clean }))
    setAiFields(new Set(keys))
    setErr(null); setDup(null)
  }, [services, categories])

  /**
   * A chat that ended without a draft (AI off or not answering): the person's own words go into the form, so
   * nothing has to be typed twice. An empty title gets the first message; the messages are added to the description.
   */
  const keepWords = useCallback((words) => {
    const said = (words || []).map((w) => String(w).trim()).filter(Boolean)
    if (!said.length) return
    setF((p) => ({
      ...p,
      title: p.title.trim() ? p.title : shortTitle(said[0]),
      description: [p.description.trim(), said.join('\n\n')].filter(Boolean).join('\n\n').slice(0, 4000)
    }))
    unmark('description')
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // The one create call, shared by the form and the chat review.
  const create = useCallback(async (values, force) => {
    const r = await api('/api/incidents', { method: 'POST', body: {
      ...values, serviceId: Number(values.serviceId), categoryId: values.categoryId ? Number(values.categoryId) : null,
      affectedUsers: Number(values.affectedUsers), autoAssign: true, forceCreate: force } })
    nav(`/incidents/${r.id}`)
    return r
  }, [nav])

  async function submit(e, force = false) {
    e?.preventDefault(); setErr(null); setDup(null); setBusy(true)
    try { await create(f, force) }
    catch (ex) {
      if (ex.code === 'DUPLICATE_INCIDENT') setDup(ex.body)
      else setErr(errText(ex))
    } finally { setBusy(false) }
  }

  const activeServices = (services || []).filter((s) => s.active)
  const activeCategories = (categories || []).filter((c) => c.active)
  const suggestions = analysis?.categorySuggestions || []
  const similar = analysis?.similarIncidents || []

  return (
    <Page intro="Tell us what's wrong. The system sorts it, sets a deadline and finds the right person.">
      <div className="split report">
        <form className="report-form" onSubmit={submit}>
          <ScreenshotDrop key={dropKey} onDraft={applyDraft} disabled={chat} />
          <div className="or-chat">
            <span className="quiet">or</span>
            <button type="button" className="link accent" onClick={() => setChat(true)} aria-describedby={rules ? 'chat-rules' : undefined}><Icon name="chat" size={16} /> Describe it in a chat instead</button>
            {rules && <span id="chat-rules" className="or-chat-note">AI is off: what you write there goes into the form.</span>}
          </div>

          <Alert kind="error" onClose={() => setErr(null)}>{err}</Alert>
          {dup && (
            <div className="notice warn">
              <div className="notice-body">
                <b>This may already be reported.</b> {dup.message}
                <div className="dup-line">
                  {(dup.candidates || []).map((c) => <span key={c} className="tech">{c}</span>)}
                  <button type="button" className="link accent" onClick={(e) => submit(e, true)}>It's a different problem — submit anyway</button>
                </div>
              </div>
            </div>
          )}

          <div className="field"><label htmlFor="svc">Service affected {ai('serviceId')}</label>
            <select id="svc" value={f.serviceId} onChange={set('serviceId')} required>
              <option value="">Choose a service…</option>
              {activeServices.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.criticality.replace('_', ' ').toLowerCase()})</option>)}
            </select></div>
          <div className="field"><label htmlFor="ttl">Title {ai('title')}</label>
            <input id="ttl" value={f.title} onChange={set('title')} maxLength={200} placeholder="e.g. Payment application unavailable" required /></div>
          <div className="field"><label htmlFor="dsc">What happened {ai('description')}</label>
            <textarea id="dsc" value={f.description} onChange={set('description')} placeholder="What you saw, since when, any error message, who else is affected…" required /></div>
          <div className="grid-2">
            <div className="field"><span className="label-like">Impact, how widely it's felt {ai('impact')}</span>
              <Choice label="Impact" value={f.impact} options={LEVEL_OPTIONS} onChange={(v) => setVal('impact', v)} /></div>
            <div className="field"><span className="label-like">Urgency, how soon it matters {ai('urgency')}</span>
              <Choice label="Urgency" value={f.urgency} options={LEVEL_OPTIONS} onChange={(v) => setVal('urgency', v)} /></div>
          </div>
          <div className="grid-2">
            <div className="field"><label htmlFor="aff">People affected {ai('affectedUsers')}</label>
              <input id="aff" type="number" min="0" value={f.affectedUsers} onChange={set('affectedUsers')} /></div>
            <div className="field"><label htmlFor="cat">Category {ai('categoryId')}</label>
              <select id="cat" value={f.categoryId} onChange={set('categoryId')} title="Leave it to the system to classify from keywords">
                <option value="">Let the system decide</option>
                {activeCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select></div>
          </div>
          <div className="form-actions">
            <button type="button" className="link" onClick={() => nav(-1)}>Cancel</button>
            <button className="btn" disabled={busy}>{busy ? 'Sending…' : 'Send report'}</button>
          </div>
        </form>

        <aside className="assist">
          <Section title="Suggested category" note="From keywords">
            {!suggestions.length && <p className="quiet">Start typing and a category will be suggested from the words you use.</p>}
            {suggestions.length > 0 && (
              <div className="suggest">
                <Bars rows={suggestions.map((s) => ({ key: s.categoryId, label: s.name, value: s.confidence, max: 100, title: `Confidence ${s.confidence}% (CategorySuggestionService)` }))} />
                <div className="suggest-use">
                  {suggestions.map((s) => (
                    <button key={s.categoryId} type="button" className={`link${String(s.categoryId) === String(f.categoryId) ? ' accent' : ''}`}
                      onClick={() => setVal('categoryId', String(s.categoryId))}>Use {s.name}</button>
                  ))}
                </div>
                {suggestions[0]?.keywords?.length > 0 && <p className="quiet">Matched: {suggestions[0].keywords.join(', ')}</p>}
              </div>
            )}
          </Section>
          <Section title="Similar open reports" note="Duplicate check">
            {!similar.length && <p className="quiet">Pick a service and describe the problem to check whether someone has reported it already.</p>}
            {/* For other people's incidents an employee gets number, status and similarity only (no id, no title). */}
            <div className="similar">
              {similar.map((s) => (
                <div key={s.incidentNumber} className={`similar-row${s.likelyDuplicate ? ' likely' : ''}`}>
                  <div className="similar-top">
                    <span className="irow-num">{s.incidentNumber}</span>
                    <span className={s.likelyDuplicate ? 'tone-late' : 'quiet'} title={s.likelyDuplicate ? 'Likely duplicate' : 'Similarity score'}>{s.similarity}% similar</span>
                  </div>
                  <div className="similar-what">{s.title && <span>{s.title}</span>} <StatusWord s={s.status} /></div>
                </div>
              ))}
            </div>
          </Section>
        </aside>
      </div>

      {chat && (
        <ChatSheet
          onClose={(words) => { setChat(false); if (words?.length) keepWords(words) }}
          onEdit={(draft) => { setChat(false); setDropKey((k) => k + 1); applyDraft(draft) }}
          create={create}
          services={services}
          categories={categories}
        />
      )}
    </Page>
  )
}
