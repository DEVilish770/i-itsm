import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { api } from './api.js'

// The AI layer. Every call here is safe: when an AI endpoint fails, is missing (404) or answers in rules mode,
// the caller gets the same RULES-shaped answer, so the screen behaves exactly like rules mode.

export const AI_REASON = {
  NO_KEY: 'no key',
  DISABLED: 'switched off',
  DAILY_LIMIT: "today's limit reached",
  CREDITS_EXHAUSTED: 'credits ran out',
  KEY_INVALID: 'key not accepted',
  UNAVAILABLE: 'AI not responding'
}

const DOWN = { mode: 'RULES', reason: 'UNAVAILABLE', model: null, callsToday: null, dailyLimit: null }
const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null)
const str = (v) => (typeof v === 'string' ? v.trim() : '')

export function normalizeStatus(s) {
  if (!s || typeof s !== 'object') return DOWN
  const mode = s.mode === 'LIVE' ? 'LIVE' : 'RULES'
  return {
    mode,
    reason: mode === 'LIVE' ? null : (AI_REASON[s.reason] ? s.reason : 'UNAVAILABLE'),
    model: str(s.model) || null,
    callsToday: num(s.callsToday),
    dailyLimit: num(s.dailyLimit)
  }
}

// After every AI call the indicator re-reads the status, so it follows what just happened.
const listeners = new Set()
const ping = () => listeners.forEach((f) => f())

const Ctx = createContext({ status: null, refresh: () => {}, setStatus: () => {} })

/** Polls GET /api/ai/status every 30 s (while the tab is visible) and after every AI call. */
export function AiProvider({ children }) {
  const [status, setRaw] = useState(null) // null until the first answer
  const seq = useRef(0)

  const refresh = useCallback(async () => {
    const n = ++seq.current
    let next
    try { next = normalizeStatus(await api('/api/ai/status')) } catch { next = DOWN }
    if (n === seq.current) setRaw(next)
  }, [])

  const setStatus = useCallback((s) => { seq.current++; setRaw(normalizeStatus(s)) }, [])

  useEffect(() => {
    refresh()
    const id = window.setInterval(() => { if (!document.hidden) refresh() }, 30000)
    const onVisible = () => { if (!document.hidden) refresh() }
    document.addEventListener('visibilitychange', onVisible)
    listeners.add(refresh)
    return () => { window.clearInterval(id); document.removeEventListener('visibilitychange', onVisible); listeners.delete(refresh) }
  }, [refresh])

  return <Ctx.Provider value={{ status, refresh, setStatus }}>{children}</Ctx.Provider>
}

export const useAi = () => useContext(Ctx)

/** 'LIVE' or 'RULES', or null while the first status answer is on its way (show copy that is true in both). */
export const useAiMode = () => useContext(Ctx).status?.mode ?? null

/**
 * The model id in plain words: "claude-sonnet-5-5" -> "Claude Sonnet 5.5", "claude-3-5-haiku-20241022" ->
 * "Claude 3.5 Haiku". Dates, "latest", version suffixes and provider prefixes are dropped; any other id is shown as given.
 */
export function modelName(id) {
  const raw = str(id)
  const m = /claude[-a-z0-9.]*/i.exec(raw)
  if (!m) return raw || null
  const parts = m[0].toLowerCase().split('-').slice(1).filter((p) => p && p !== 'latest' && !/^\d{8}$/.test(p) && !/^v\d+$/.test(p))
  const words = []
  let version = []
  const flush = () => { if (version.length) { words.push(version.join('.')); version = [] } }
  for (const p of parts) {
    if (/^\d+$/.test(p)) version.push(p)
    else { flush(); words.push(p.charAt(0).toUpperCase() + p.slice(1)) }
  }
  flush()
  return ['Claude', ...words].join(' ')
}

async function post(path, body) {
  try { return await api(path, { method: 'POST', body }) } catch { return null } finally { ping() }
}

/** POST /api/ai/screenshot-triage -> { source:'AI', draft, summary } or { source:'RULES', message } */
export async function triageScreenshot(imageBase64, mediaType) {
  const r = await post('/api/ai/screenshot-triage', { imageBase64, mediaType })
  if (r && r.source === 'AI' && r.available !== false && r.draft && typeof r.draft === 'object') {
    return { source: 'AI', draft: r.draft, summary: str(r.summary) }
  }
  return {
    source: 'RULES',
    message: str(r?.message) || "AI can't read screenshots right now. Describe the problem below and the built-in rules will sort it."
  }
}

/**
 * POST /api/ai/chat with the whole history -> { source, reply, done, draft, local? }
 * A failed call (network, 5xx) ends the chat exactly like the backend's rules answer (done, no draft). That reply is
 * made here, so it is marked local and never sent back to the backend as an assistant turn.
 */
export async function chatTurn(messages) {
  const r = await post('/api/ai/chat', { messages })
  const reply = str(r?.reply)
  if (!r || !reply) {
    return { source: 'RULES', reply: "I can't chat right now. What you wrote goes into the form, and the built-in rules will still sort your report.", done: true, draft: null, local: true }
  }
  return {
    source: r.source === 'AI' ? 'AI' : 'RULES',
    reply,
    done: r.done === true,
    draft: r.draft && typeof r.draft === 'object' ? r.draft : null
  }
}

/** POST /api/ai/search -> { source, filters, explanation }; rules mode searches the words as typed. */
export async function askSearch(query) {
  const words = { source: 'RULES', filters: { q: query }, explanation: 'Searching titles, descriptions and numbers for the words you typed.' }
  // An incident number needs no interpretation. Numbers are INC-%06d and search matches a part of the text, so
  // short forms ("INC-12", "inc12", "INC 000012") are padded to the full number first.
  const n = /^\s*inc\s*-?\s*0*(\d{1,9})\s*$/i.exec(query)
  if (n) return { ...words, filters: { q: `INC-${n[1].padStart(6, '0')}` }, explanation: 'Looking up that incident number.' }
  const r = await post('/api/ai/search', { query })
  if (!r || !r.filters || typeof r.filters !== 'object') return words
  return { source: r.source === 'AI' ? 'AI' : 'RULES', filters: r.filters, explanation: str(r.explanation) || words.explanation }
}

/** GET /api/admin/reports/summary -> { source, text, generatedAt }, or null when it can't be had. */
export async function reportSummary() {
  try {
    const r = await api('/api/admin/reports/summary')
    const text = str(r?.text)
    return text ? { source: r.source === 'AI' ? 'AI' : 'RULES', text, generatedAt: r.generatedAt || null } : null
  } catch { return null } finally { ping() }
}

/** PUT /api/admin/ai -> the new status (throws on failure, so the switch can flip back). */
export async function setAiEnabled(enabled) {
  try { return normalizeStatus(await api('/api/admin/ai', { method: 'PUT', body: { enabled } })) } finally { ping() }
}
