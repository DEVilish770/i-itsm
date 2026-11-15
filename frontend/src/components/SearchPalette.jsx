import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { api, fmt } from '../lib/api.js'
import { askSearch, useAiMode } from '../lib/ai.jsx'
import { useAuth } from '../lib/auth.jsx'
import { useDialog } from '../lib/dialog.js'
import { HEALTH, PRIORITY, STATUS, cleanFilters, filtersToQuery, isMac, plural } from '../lib/words.js'
import { IncidentRow, Tag, TypingDots, useNow } from './ui.jsx'
import { Icon } from './icons.jsx'

// The words around the search follow the AI mode. AI live: questions in plain English. Rules mode: the words you
// type are matched as they are, so the examples are a number and a word. Until the mode is known: words only.
const WORDS = {
  placeholder: 'Search by number or a word, like INC-000012 or vpn',
  label: 'Search incidents by number or words',
  input: 'Search by number or words',
  thinking: 'Searching'
}
const COPY = {
  LIVE: {
    placeholder: 'Ask anything, like “urgent payment problems this week”',
    label: 'Search incidents in plain English',
    input: 'Ask in plain English',
    thinking: 'Reading your question',
    hint: "Describe what you're looking for, then press Enter.",
    examples: 'Try “overdue network issues”, “Arjun\'s open incidents” or an incident number.'
  },
  RULES: {
    ...WORDS,
    hint: 'AI is off: search matches the words you type.',
    examples: 'Try an incident number like INC-000012, or a word like vpn, then press Enter.'
  },
  UNKNOWN: {
    ...WORDS,
    hint: "Type what you're looking for, then press Enter.",
    examples: 'Try an incident number like INC-000012, or a word like vpn.'
  }
}
const useCopy = () => COPY[useAiMode() || 'UNKNOWN']

/** The search line in the top bar. Clicking it (or ⌘K / Ctrl+K) opens the palette. */
export function SearchLine({ onOpen }) {
  const copy = useCopy()
  return (
    <button type="button" className="search-line" onClick={onOpen} aria-label={copy.label}>
      <Icon name="search" size={17} />
      <span className="search-ph">{copy.placeholder}</span>
      <kbd>{isMac() ? '⌘K' : 'Ctrl K'}</kbd>
    </button>
  )
}

/** Names for the services, categories and technicians a filter can point at (loaded once per palette or list). */
export function useLookups(role) {
  const [lk, setLk] = useState({ services: [], categories: [], technicians: [] })
  useEffect(() => {
    let alive = true
    // Every role loads the technician names: an employee's plain-English search can pick a technician too.
    Promise.all([
      api('/api/services').catch(() => []),
      api('/api/categories').catch(() => []),
      api('/api/technicians').catch(() => [])
    ]).then(([services, categories, technicians]) => { if (alive) setLk({ services: services || [], categories: categories || [], technicians: technicians || [] }) })
    return () => { alive = false }
  }, [role])
  return lk
}

/** The words shown for one interpreted filter. */
export function tokenLabel(k, v, lk) {
  const name = (list, id, key = 'name') => list.find((x) => String(x.id) === String(id))?.[key]
  switch (k) {
    case 'q': return `“${v}”`
    case 'status': return STATUS[v] || v
    case 'priority': return PRIORITY[v] ? `${PRIORITY[v].word} priority` : v
    case 'categoryId': return name(lk.categories, v) || `Category ${v}`
    case 'serviceId': return name(lk.services, v) || `Service ${v}`
    case 'technicianId': return name(lk.technicians, v, 'fullName') || `Technician ${v}`
    case 'slaStatus': return HEALTH[v]?.word || v
    case 'from': return `Since ${fmt.day(v)}`
    case 'to': return `Until ${fmt.day(v)}`
    default: return String(v)
  }
}

/** Underlined, removable text tokens for the interpreted filters. */
export function Tokens({ filters, lookups, onRemove }) {
  const keys = Object.keys(filters).filter((k) => filters[k] !== '' && filters[k] != null && k !== 'page')
  if (!keys.length) return <span className="quiet">No filters left. Showing everything you can see.</span>
  return (
    <span className="tokens">
      {keys.map((k) => (
        <button type="button" key={k} className="token" onClick={() => onRemove(k)} title={`${k} = ${filters[k]} · click to remove`}
          aria-label={`${tokenLabel(k, filters[k], lookups)}, remove this filter`}>
          {tokenLabel(k, filters[k], lookups)}<span className="token-x" aria-hidden="true">×</span>
        </button>
      ))}
    </span>
  )
}

export default function SearchPalette({ onClose }) {
  const { user } = useAuth()
  const nav = useNavigate()
  const copy = useCopy()
  const lookups = useLookups(user?.role)
  const now = useNow(30000)
  const box = useRef(null)
  const [q, setQ] = useState('')
  const [phase, setPhase] = useState('idle') // idle | thinking | done
  const [result, setResult] = useState(null) // { query, source, explanation, filters }
  const [page, setPage] = useState(null)
  const [loadErr, setLoadErr] = useState(null)
  const runId = useRef(0)
  const asking = useRef(false) // a search question is on its way (in AI mode each one is a paid call)
  const answers = useRef(new Map()) // query -> AI answer, for this palette: the same words are not paid for twice

  // Focus goes to the input, Tab stays in the palette, Escape closes it, and focus goes back to where it was.
  useDialog(box, 'palette', onClose)

  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])

  async function load(filters, id) {
    setLoadErr(null)
    try {
      const qs = filtersToQuery(filters)
      const r = await api(`/api/incidents?${qs}${qs ? '&' : ''}size=8`)
      if (id === runId.current) setPage(r)
    } catch (ex) {
      if (id === runId.current) { setPage(null); setLoadErr(ex.message) }
    }
  }

  async function run(e) {
    e.preventDefault()
    const query = q.trim()
    if (!query || asking.current) return // one question at a time: Enter again while it reads is ignored
    const known = answers.current.get(query)
    if (known) {
      // The same words again: show the interpreted filters once more (removed ones come back), without asking again.
      const id = ++runId.current
      setResult(known); setPhase('done')
      load(known.filters, id)
      return
    }
    const id = ++runId.current
    asking.current = true
    setPhase('thinking'); setPage(null); setLoadErr(null)
    let r
    try { r = await askSearch(query) } finally { asking.current = false }
    if (id !== runId.current) return
    let filters = cleanFilters(r.filters)
    // An AI answer with no filters means "everything you can see". Search the words only when nothing usable came
    // back: a rules answer, or AI filters that did not survive cleaning.
    const proposed = r.filters && typeof r.filters === 'object' ? Object.keys(r.filters).length : 0
    if (!Object.keys(filters).length && (r.source !== 'AI' || proposed > 0)) filters = { q: query }
    const answer = { query, source: r.source, explanation: r.explanation, filters }
    if (r.source === 'AI') answers.current.set(query, answer) // rules answers cost nothing, so they may be asked again
    setResult(answer)
    setPhase('done')
    load(filters, id)
  }

  function remove(k) {
    const filters = { ...result.filters }
    delete filters[k]
    const id = ++runId.current
    setResult({ ...result, filters })
    load(filters, id)
  }

  function openAll() {
    nav('/incidents', { state: { ai: result } })
    onClose()
  }

  const rows = page?.content || []
  // Read out when results arrive (the visible count is only shown when there are some).
  const announce = phase !== 'done' ? '' : loadErr || (page ? (page.totalElements > 0 ? `${plural(page.totalElements, 'incident')} found.` : 'Nothing matches.') : '')
  return createPortal(
    <div className="scrim palette-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="palette" ref={box} tabIndex={-1} role="dialog" aria-modal="true" aria-label="Search">
        <form className="palette-input" onSubmit={run} aria-busy={phase === 'thinking'}>
          <Icon name="search" size={18} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={copy.placeholder} aria-label={copy.input} maxLength={300} />
          <kbd>esc</kbd>
        </form>
        <div className="palette-body">
          <p className="sr-only" role="status">{announce}</p>
          {phase === 'idle' && (
            <div className="palette-hint">
              <p>{copy.hint}</p>
              <p className="quiet">{copy.examples}</p>
            </div>
          )}
          {phase === 'thinking' && <div className="palette-hint"><TypingDots label={copy.thinking} /> <span className="quiet">{copy.thinking}…</span></div>}
          {phase === 'done' && result && (
            <>
              <div className="interpreted">
                <div className="interpreted-line">
                  <span className="label">Showing</span>
                  <Tokens filters={result.filters} lookups={lookups} onRemove={remove} />
                  <Tag source={result.source} />
                </div>
                {result.explanation && <p className="interpreted-why">{result.explanation}</p>}
              </div>
              {loadErr && <p className="pop-err">{loadErr}</p>}
              {page && rows.length === 0 && <p className="palette-none">Nothing matches. Remove a filter, or try other words.</p>}
              <div className="rows">
                {rows.map((i, idx) => <IncidentRow key={i.id} i={i} index={idx} now={now} compact onOpen={onClose} />)}
              </div>
              {page && page.totalElements > 0 && (
                <div className="palette-foot">
                  <span className="quiet">{page.totalElements === 1 ? '1 incident' : `${page.totalElements} incidents`}</span>
                  <button type="button" className="link accent" onClick={openAll}>Open the full list <span aria-hidden="true">→</span></button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>,
    document.body
  )
}
