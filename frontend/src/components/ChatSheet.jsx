import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { chatTurn, useAiMode } from '../lib/ai.jsx'
import { errText } from '../lib/api.js'
import { useDialog } from '../lib/dialog.js'
import { prefersReducedMotion } from '../lib/theme.jsx'
import { LEVEL_WORD, cleanDraft } from '../lib/words.js'
import { Tag, TypingDots } from './ui.jsx'
import { Icon } from './icons.jsx'

const MAX_MESSAGES = 12 // the backend accepts at most 12 messages of history
const MAX_CHARS = 2000 // and at most 2000 characters per message

/**
 * Chat reporting: a calm side sheet. Each turn sends the whole history to POST /api/ai/chat.
 * When the assistant says it's done, the draft is shown for review; Submit goes through the normal create call.
 * Rules mode (or no AI at all): the reply is shown with a way back to the form, and the person's own words go
 * with them: onClose(words) hands them to the form whenever the chat ended without a draft.
 */
export default function ChatSheet({ onClose, onEdit, create, services, categories }) {
  const mode = useAiMode()
  const [history, setHistory] = useState([]) // [{ role, content, source?, local? }]
  const [text, setText] = useState('')
  const [thinking, setThinking] = useState(false)
  const [draft, setDraft] = useState(null) // cleaned draft once done
  const [rawDraft, setRawDraft] = useState(null)
  const [rulesMode, setRulesMode] = useState(false)
  const [ended, setEnded] = useState(false) // the assistant is done but has no draft (rules mode): finish in the form
  const [submitting, setSubmitting] = useState(false)
  const [dup, setDup] = useState(null)
  const [err, setErr] = useState(null)
  const sheet = useRef(null)
  const list = useRef(null)
  const input = useRef(null)

  const full = history.length + 1 > MAX_MESSAGES
  const closed = thinking || full || ended || !!draft
  // The chat is over without a draft: closing it in any way takes the person's words to the form.
  const handOff = !draft && (ended || full)
  const finish = () => onClose(handOff ? history.filter((m) => m.role === 'user').map((m) => m.content) : null)

  // Focus moves to the message box, Tab stays in the sheet, Escape closes it, and focus goes back on close.
  useDialog(sheet, 'chat', finish)

  useEffect(() => {
    const el = list.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }, [history, thinking, draft])

  // After each reply: back to the message box, or, once the chat is over, to the next step (back to the form, or Edit).
  useEffect(() => {
    if (thinking || !history.length) return
    const box = input.current
    if (box && !box.disabled) box.focus()
    else list.current?.querySelector('.msg-note button, .review-actions button')?.focus()
  }, [thinking]) // eslint-disable-line react-hooks/exhaustive-deps

  async function send(e) {
    e?.preventDefault()
    const content = text.trim()
    if (!content || closed) return
    const next = [...history, { role: 'user', content: content.slice(0, MAX_CHARS) }]
    setHistory(next); setText(''); setThinking(true); setErr(null)
    // Replies made in the browser (the AI could not be reached) are not part of the conversation the AI sees.
    const r = await chatTurn(next.filter((m) => !m.local).map(({ role, content: c }) => ({ role, content: c.slice(0, MAX_CHARS) })))
    setThinking(false)
    setHistory((h) => [...h, { role: 'assistant', content: r.reply, source: r.source, local: !!r.local }])
    setRulesMode(r.source !== 'AI')
    if (r.done && r.draft) {
      setRawDraft(r.draft)
      setDraft(cleanDraft(r.draft, { services, categories }))
    } else if (r.done) setEnded(true)
  }

  function onKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
  }

  const values = draft ? { title: '', description: '', serviceId: '', categoryId: '', impact: 'MEDIUM', urgency: 'MEDIUM', affectedUsers: 1, ...draft } : null
  const missing = values ? [!values.title && 'a title', !values.description && 'a description', !values.serviceId && 'the service'].filter(Boolean) : []

  async function submit(force = false) {
    if (!values || missing.length) return
    setSubmitting(true); setErr(null); setDup(null)
    try { await create(values, force) } // navigates away on success
    catch (ex) {
      if (ex.code === 'DUPLICATE_INCIDENT') setDup(ex.body)
      else setErr(errText(ex))
    } finally { setSubmitting(false) }
  }

  const name = (lst, id) => lst?.find((x) => String(x.id) === String(id))?.name

  return createPortal(
    <div className="scrim sheet-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) finish() }}>
      <aside className="sheet" ref={sheet} tabIndex={-1} role="dialog" aria-modal="true" aria-label="Describe the problem in a chat">
        <header className="sheet-head">
          <div>
            <h2>Tell me what happened</h2>
            <p className="quiet">{mode === 'RULES'
              ? 'AI is off: what you write here goes into the form.'
              : "I'll ask a few short questions, then show you the report before anything is sent."}</p>
          </div>
          <button type="button" className="icon-btn" onClick={() => finish()} aria-label="Close the chat"><Icon name="close" size={18} /></button>
        </header>

        <div className="msgs" ref={list} aria-live="polite">
          <div className="msg assistant intro">Hi. What's going wrong? Say it the way you'd tell a colleague.</div>
          {history.map((m, idx) => (
            <div key={idx} className={`msg ${m.role}`}>
              {m.content}
              {m.role === 'assistant' && m.source && <Tag source={m.source} />}
            </div>
          ))}
          {thinking && <div className="msg assistant typing-row"><TypingDots label="Writing a reply" /></div>}
          {(rulesMode || ended) && !draft && !thinking && history.length > 0 && (
            <div className="msg-note">
              <button type="button" className="link accent" onClick={() => finish()}><Icon name="back" size={14} /> Back to the form</button>
            </div>
          )}
          {full && !draft && !thinking && (
            <div className="msg-note">
              That's as long as a chat can go. <button type="button" className="link accent" onClick={() => finish()}>Finish it in the form</button>
            </div>
          )}

          {draft && (
            <div className="review">
              <div className="review-head"><h3>Here's what I'll report</h3><Tag source="AI" /></div>
              <dl className="review-list">
                <div><dt>Title</dt><dd>{values.title || <span className="quiet">Not given yet</span>}</dd></div>
                <div><dt>What happened</dt><dd className="pre">{values.description || <span className="quiet">Not given yet</span>}</dd></div>
                <div><dt>Service</dt><dd>{name(services, values.serviceId) || <span className="quiet">Not given yet</span>}</dd></div>
                <div><dt>Category</dt><dd>{name(categories, values.categoryId) || <span className="quiet">The system will decide</span>}</dd></div>
                <div><dt>How many it affects</dt><dd>{LEVEL_WORD[values.impact]} impact · {values.affectedUsers} {Number(values.affectedUsers) === 1 ? 'person' : 'people'}</dd></div>
                <div><dt>How urgent</dt><dd>{LEVEL_WORD[values.urgency]}</dd></div>
              </dl>
              {missing.length > 0 && <p className="review-miss">Still needed: {missing.join(', ')}. Use Edit to add it in the form.</p>}
              {err && <p className="pop-err">{err}</p>}
              {dup && (
                <div className="review-dup">
                  <p><b>This may already be reported.</b> {dup.message}</p>
                  <p className="quiet">{(dup.candidates || []).join(', ')}</p>
                  <button type="button" className="link accent" disabled={submitting} onClick={() => submit(true)}>It's a different problem — submit anyway</button>
                </div>
              )}
              <div className="review-actions">
                <button type="button" className="link" onClick={() => onEdit(rawDraft)}>Edit</button>
                <button type="button" className="btn" disabled={submitting || missing.length > 0} onClick={() => submit(false)}>{submitting ? 'Sending…' : 'Submit'}</button>
              </div>
            </div>
          )}
        </div>

        <form className="composer" onSubmit={send}>
          <textarea ref={input} rows={1} value={text} onChange={(e) => setText(e.target.value)} onKeyDown={onKey} maxLength={MAX_CHARS}
            placeholder={draft ? 'Anything to add? Use Edit to change the report.' : ended ? 'Chat finished. The form is ready for you.' : 'Type a message'}
            disabled={closed} aria-label="Your message" />
          <button type="submit" className="icon-btn send" disabled={!text.trim() || closed} aria-label="Send"><Icon name="send" size={18} /></button>
        </form>
      </aside>
    </div>,
    document.body
  )
}
