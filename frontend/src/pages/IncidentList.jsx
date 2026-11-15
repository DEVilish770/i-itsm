import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { DONE, EMPTY_FILTERS, HEALTH, HEALTHS, PRIORITIES, PRIORITY, STATUS, STATUSES, cleanFilters, filtersToQuery, firstName, greeting, plural } from '../lib/words.js'
import { EmptyState, Hero, IncidentRow, Page, Tag, useLoad, useNow } from '../components/ui.jsx'
import { Tokens } from '../components/SearchPalette.jsx'

export default function IncidentList() {
  const { user } = useAuth()
  const loc = useLocation()
  const now = useNow(30000)
  const isEmp = user.role === 'EMPLOYEE'
  const [f, setF] = useState(EMPTY_FILTERS)
  const [ai, setAi] = useState(null) // { query, source, explanation, keys: Set } when filters came from plain-English search
  const { data: services } = useLoad(() => api('/api/services'))
  const { data: categories } = useLoad(() => api('/api/categories'))
  // Every role loads the names: a plain-English search can filter an employee's incidents by technician too.
  // Employees still get no technician picker below.
  const { data: techs } = useLoad(() => api('/api/technicians'))
  const qs = filtersToQuery(f)
  const { data, loading } = useLoad(() => api(`/api/incidents?${qs}&size=20`), [qs])
  const lookups = { services: services || [], categories: categories || [], technicians: techs || [] }

  // Arriving from the search palette: apply the interpreted filters.
  useEffect(() => {
    const s = loc.state?.ai
    if (!s?.filters) return
    const clean = cleanFilters(s.filters)
    setF({ ...EMPTY_FILTERS, ...clean })
    setAi({ query: s.query, source: s.source, explanation: s.explanation, keys: new Set(Object.keys(clean)) })
  }, [loc.key]) // eslint-disable-line

  const set = (k) => (e) => {
    setF({ ...f, [k]: e.target.value, page: 0 })
    if (ai?.keys.has(k)) { const keys = new Set(ai.keys); keys.delete(k); setAi({ ...ai, keys }) } // a filter changed by hand is theirs now
  }
  const clearAll = () => { setF(EMPTY_FILTERS); setAi(null) }
  const removeToken = (k) => {
    setF({ ...f, [k]: '', page: 0 })
    const keys = new Set(ai.keys); keys.delete(k); setAi({ ...ai, keys })
  }
  const aiFilters = ai ? Object.fromEntries([...ai.keys].filter((k) => f[k] !== '').map((k) => [k, f[k]])) : {}
  const filtered = Object.entries(f).some(([k, v]) => k !== 'page' && v !== '')

  // The employee's home: a greeting over the dot field, with how their reports are doing.
  const { data: mine } = useLoad(() => (isEmp ? api('/api/incidents/mine') : Promise.resolve(null)))
  const openMine = (mine || []).filter((i) => !DONE.has(i.status))
  const behind = openMine.filter((i) => i.slaStatus === 'AT_RISK' || i.slaStatus === 'VIOLATED').length
  const hero = isEmp && (
    <Hero title={`${greeting()}, ${firstName(user)}.`}>
      <span>{!mine ? 'Checking on your reports…'
        : openMine.length === 0 ? 'All quiet. Nothing you reported is open.'
          : `${plural(openMine.length, 'open report')}. ${behind ? `${behind} running late, and flagged for the team.` : openMine.length === 1 ? 'It is on track.' : 'All on track.'}`}</span>
      <Link to="/incidents/new" className="link accent">Report a problem <span aria-hidden="true">→</span></Link>
    </Hero>
  )

  const actions = user.role !== 'TECHNICIAN' && !isEmp && <Link to="/incidents/new" className="btn">Report a problem</Link>
  const intro = user.role === 'TECHNICIAN' ? 'Your incidents by default. Pick a technician to see theirs.' : 'Everything reported to IT, newest first.'

  return (
    <Page hero={hero} intro={intro} actions={actions}>
      {ai && Object.keys(aiFilters).length > 0 && (
        <div className="interpreted page-level">
          <div className="interpreted-line">
            <span className="label">Showing</span>
            <Tokens filters={aiFilters} lookups={lookups} onRemove={removeToken} />
            <Tag source={ai.source} />
          </div>
          {ai.explanation && <p className="interpreted-why">{ai.explanation}</p>}
        </div>
      )}

      <div className="filters">
        <div className="field grow"><label htmlFor="fq">Search</label><input id="fq" value={f.q} onChange={set('q')} placeholder="Number, title or words, like INC-000012 or vpn" /></div>
        <div className="field"><label htmlFor="fs">State</label><select id="fs" value={f.status} onChange={set('status')} title="Lifecycle state"><option value="">Any</option>{STATUSES.map((s) => <option key={s} value={s}>{STATUS[s]}</option>)}</select></div>
        <div className="field"><label htmlFor="fp">Priority</label><select id="fp" value={f.priority} onChange={set('priority')}><option value="">Any</option>{PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY[p].word} ({p})</option>)}</select></div>
        <div className="field"><label htmlFor="fh">Deadline</label><select id="fh" value={f.slaStatus} onChange={set('slaStatus')} title="SLA status"><option value="">Any</option>{HEALTHS.map((s) => <option key={s} value={s}>{HEALTH[s].word}</option>)}</select></div>
        <div className="field"><label htmlFor="fc">Category</label><select id="fc" value={f.categoryId} onChange={set('categoryId')}><option value="">Any</option>{(categories || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
        <div className="field"><label htmlFor="fv">Service</label><select id="fv" value={f.serviceId} onChange={set('serviceId')}><option value="">Any</option>{(services || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
        {!isEmp && <div className="field"><label htmlFor="ft">Technician</label><select id="ft" value={f.technicianId} onChange={set('technicianId')}><option value="">{user.role === 'TECHNICIAN' ? 'Mine' : 'Anyone'}</option>{(techs || []).map((c) => <option key={c.id} value={c.id}>{c.fullName}</option>)}</select></div>}
        <div className="field"><label htmlFor="ff">Reported from</label><input id="ff" type="datetime-local" value={f.from} onChange={set('from')} /></div>
        <div className="field"><label htmlFor="fto">Reported until</label><input id="fto" type="datetime-local" value={f.to} onChange={set('to')} /></div>
        <div className="field end"><button type="button" className="link" onClick={clearAll} disabled={!filtered && !ai}>Clear filters</button></div>
      </div>

      <div className="list-head">
        <span className="quiet">{data ? plural(data.totalElements, 'incident') : loading ? 'Loading…' : ''}</span>
      </div>
      {data && data.content.length === 0 && (
        filtered
          ? <EmptyState title="Nothing matches.">Try fewer filters, or <button type="button" className="link accent" onClick={clearAll}>clear them all</button>.</EmptyState>
          : <EmptyState action={isEmp ? <Link to="/incidents/new" className="link accent">Report a problem <span aria-hidden="true">→</span></Link> : null}>
              {isEmp ? "You haven't reported anything. When something breaks, it starts here." : 'Nothing needs you right now.'}
            </EmptyState>
      )}
      {data && (
        <div className="rows" key={qs}>
          {data.content.map((i, idx) => <IncidentRow key={i.id} i={i} index={idx} now={now} />)}
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button type="button" className="link" disabled={f.page === 0} onClick={() => setF({ ...f, page: f.page - 1 })}>Previous</button>
          <span className="quiet">Page {data.number + 1} of {Math.max(1, data.totalPages)}</span>
          <button type="button" className="link" disabled={data.last} onClick={() => setF({ ...f, page: f.page + 1 })}>Next</button>
        </div>
      )}
    </Page>
  )
}
