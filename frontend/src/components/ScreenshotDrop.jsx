import { useEffect, useRef, useState } from 'react'
import { triageScreenshot, useAiMode } from '../lib/ai.jsx'
import { dialogOpen } from '../lib/dialog.js'
import { isMac } from '../lib/words.js'
import { Tag, TypingDots } from './ui.jsx'
import { Icon } from './icons.jsx'

const MAX_SIDE = 1568
const ACCEPT = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => resolve({ img, url })
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('That file could not be read as an image.')) }
    img.src = url
  })
}

/** Downscales to at most 1568 px on the longest side and re-encodes as JPEG (quality ~0.85). */
async function prepare(file) {
  const { img, url } = await loadImage(file)
  try {
    const w0 = img.naturalWidth || img.width, h0 = img.naturalHeight || img.height
    const scale = Math.min(1, MAX_SIDE / Math.max(w0, h0))
    const w = Math.max(1, Math.round(w0 * scale)), h = Math.max(1, Math.round(h0 * scale))
    const c = document.createElement('canvas')
    c.width = w; c.height = h
    const ctx = c.getContext('2d')
    ctx.fillStyle = '#ffffff' // transparent screenshots would turn black in a JPEG
    ctx.fillRect(0, 0, w, h)
    ctx.drawImage(img, 0, 0, w, h)
    let data = c.toDataURL('image/jpeg', 0.85)
    if (data.length > 4500000) data = c.toDataURL('image/jpeg', 0.7)
    return { base64: data.slice(data.indexOf(',') + 1), mediaType: 'image/jpeg', preview: data }
  } finally { URL.revokeObjectURL(url) }
}

const imageFrom = (list) => Array.from(list || []).find((f) => f && ACCEPT.includes(f.type))
const NOT_TEXT = new Set(['button', 'checkbox', 'color', 'file', 'image', 'radio', 'range', 'reset', 'submit'])

/** True for the fields a paste types into: text inputs, textareas and editable text. */
function typesText(el) {
  if (!el || el.nodeType !== 1) return false
  if (el.isContentEditable || el.tagName === 'TEXTAREA') return true
  return el.tagName === 'INPUT' && !NOT_TEXT.has(el.type)
}

/**
 * Screenshot to ticket. Paste (Ctrl/⌘+V anywhere on the page), drop, or choose a screenshot; the AI reads it and
 * the page fills the form from the draft. In rules mode the message is shown quietly and the person types instead.
 * While `disabled` (the chat is open) or any dialog is open, nothing is read, but a dropped file is still caught.
 */
export default function ScreenshotDrop({ onDraft, disabled = false }) {
  const mode = useAiMode()
  const [state, setState] = useState('idle') // idle | busy | done | rules | error
  const [dragging, setDragging] = useState(false) // a file is being dragged anywhere over the page
  const [preview, setPreview] = useState(null)
  const [line, setLine] = useState('')
  const file = useRef(null)
  const busy = useRef(false)
  const onDraftRef = useRef(onDraft)
  onDraftRef.current = onDraft
  const off = useRef(disabled)
  off.current = disabled

  async function handle(f) {
    if (!f || busy.current) return
    if (!ACCEPT.includes(f.type)) { setState('error'); setLine('Use a PNG, JPEG, WebP or GIF image.'); return }
    if (f.size > 25 * 1024 * 1024) { setState('error'); setLine('That image is too large. Try a smaller screenshot.'); return }
    busy.current = true
    setState('busy'); setLine('')
    try {
      const img = await prepare(f)
      setPreview(img.preview)
      const r = await triageScreenshot(img.base64, img.mediaType)
      if (r.source === 'AI') {
        setState('done')
        setLine(r.summary || 'Filled in from your screenshot. Check it over before you send it.')
        onDraftRef.current?.(r.draft, r.summary)
      } else {
        setState('rules'); setLine(r.message)
      }
    } catch (ex) {
      setState('error'); setLine(ex.message || 'That screenshot could not be read.')
    } finally { busy.current = false }
  }

  // Ctrl/⌘+V anywhere on the page, and a screenshot dropped anywhere on the page (not only on the box), so a
  // missed drop never makes the browser open the image instead. Text pastes and text drags pass through untouched.
  // The listeners stay on while the chat (or another dialog) is open: a file dropped there is caught and ignored.
  useEffect(() => {
    const hasFiles = (e) => Array.from(e.dataTransfer?.types || []).includes('Files')
    const blocked = () => off.current || dialogOpen()
    const onPaste = (e) => {
      if (blocked()) return
      const f = imageFrom(e.clipboardData?.files)
      if (!f) return
      // Text copied from apps like Excel or Word also carries a picture of itself: in a text field, paste the text.
      if (typesText(e.target) && Array.from(e.clipboardData?.types || []).includes('text/plain')) return
      e.preventDefault()
      handle(f)
    }
    const onOver = (e) => {
      if (!hasFiles(e)) return
      e.preventDefault()
      if (blocked()) { if (e.dataTransfer) e.dataTransfer.dropEffect = 'none'; return }
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
      setDragging(true)
    }
    const onLeave = (e) => { if (!e.relatedTarget) setDragging(false) } // left the window
    const onDrop = (e) => {
      if (!hasFiles(e)) return
      e.preventDefault()
      setDragging(false)
      if (blocked()) return
      const f = imageFrom(e.dataTransfer?.files)
      if (f) handle(f)
      else { setState('error'); setLine('Use a PNG, JPEG, WebP or GIF image.') }
    }
    document.addEventListener('paste', onPaste)
    document.addEventListener('dragenter', onOver)
    document.addEventListener('dragover', onOver)
    document.addEventListener('dragleave', onLeave)
    document.addEventListener('drop', onDrop)
    return () => {
      document.removeEventListener('paste', onPaste)
      document.removeEventListener('dragenter', onOver)
      document.removeEventListener('dragover', onOver)
      document.removeEventListener('dragleave', onLeave)
      document.removeEventListener('drop', onDrop)
    }
  }, []) // eslint-disable-line

  const shown = dragging && state !== 'busy' ? 'over' : state
  const paste = isMac() ? '⌘V' : 'Ctrl+V'
  // Before a screenshot: what it will do. AI live fills the form; rules mode says so plainly; unknown says neither.
  const idleLine = mode === 'LIVE' ? `The form fills itself in from it. ${paste} works too.`
    : mode === 'RULES' ? "AI is off, so a screenshot can't fill in the form. Describe the problem below."
      : `${paste} works too.`

  return (
    <div className={`drop s-${shown}`}>
      <button type="button" className="drop-hit" onClick={() => file.current?.click()} disabled={state === 'busy'}
        aria-label="Choose a screenshot of the problem">
        {preview ? <img className="drop-thumb" src={preview} alt="Your screenshot" /> : <span className="drop-icon"><Icon name="image" size={22} /></span>}
        <span className="drop-text">
          {state === 'busy'
            ? <span className="drop-title"><TypingDots label="Reading the screenshot" /> Reading the screenshot…</span>
            : <span className="drop-title">{preview ? 'Use another screenshot' : 'Paste, drop or choose a screenshot of the problem'}</span>}
          {shown === 'over'
            ? <span className="drop-sub">Let go to read it.</span>
            : state === 'idle'
            ? <span className="drop-sub">{idleLine}</span>
            : line && <span className={`drop-sub${state === 'error' ? ' tone-late' : ''}`}>{line}{state === 'done' && <Tag source="AI" />}</span>}
        </span>
      </button>
      <input ref={file} type="file" accept={ACCEPT.join(',')} hidden onChange={(e) => { handle(e.target.files?.[0]); e.target.value = '' }} />
    </div>
  )
}
