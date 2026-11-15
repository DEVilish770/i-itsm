import { useEffect, useRef, useState } from 'react'
import { AI_REASON, modelName, useAi } from '../lib/ai.jsx'
import { dialogOpen, onDialogChange } from '../lib/dialog.js'
import { useDismiss, useReducedMotion } from './ui.jsx'

const W = 34, MID = 7
// About 30 frames a second: the wave travels about half a pixel a frame, so it looks as smooth as at 60.
const FRAME_MS = 1000 / 30
const SIXTIETH = 1000 / 60

/** y = 7 + sin(x*.5 + t*.008) * 4.2 * sin(PI*x/34), scaled by `amp` (0 = the flat rules-mode line). */
function wave(t, amp) {
  let d = ''
  for (let x = 0; x <= W; x += 2) {
    const y = MID + Math.sin(x * 0.5 + t * 0.008) * 4.2 * Math.sin((Math.PI * x) / W) * amp
    d += `${x === 0 ? 'M' : 'L'}${x} ${y.toFixed(2)} `
  }
  return d.trim()
}

/** Top-bar AI mode: a moving waveform and "AI live" with the model, or a flat line, "Rules mode" and the reason. */
export default function AiIndicator() {
  const { status } = useAi()
  const reduced = useReducedMotion()
  const live = status?.mode === 'LIVE'
  const path = useRef(null)
  const amp = useRef(0)
  const [open, setOpen] = useState(false)
  const box = useRef(null)
  useDismiss(box, open, () => setOpen(false))

  // The wave eases in when AI goes live and settles to a flat line in rules mode. Still under reduced motion.
  // The loop rests whenever the wave can't be seen: the tab is hidden, a dialog's scrim covers the page, or the
  // indicator is off screen. It picks up again where it can be seen, and stops for good once the line is flat.
  useEffect(() => {
    const el = path.current
    if (!el) return undefined
    if (reduced) { amp.current = live ? 1 : 0; el.setAttribute('d', wave(0, amp.current)); return undefined }
    const target = live ? 1 : 0
    let raf = 0, running = false, last = 0
    let shown = !document.hidden, covered = dialogOpen(), inView = true
    const step = (t) => {
      if (!running) return
      if (last && t - last < FRAME_MS - 3) { raf = requestAnimationFrame(step); return }
      const dt = last ? Math.min(100, t - last) : FRAME_MS
      last = t
      amp.current += (target - amp.current) * (1 - Math.pow(1 - 0.06, dt / SIXTIETH)) // the same easing per second as before
      if (!live && amp.current < 0.004) { amp.current = 0; el.setAttribute('d', wave(0, 0)); running = false; return }
      el.setAttribute('d', wave(t, amp.current))
      raf = requestAnimationFrame(step)
    }
    const start = () => {
      if (running || !shown || covered || !inView || (!live && amp.current === 0)) return
      running = true; last = 0
      raf = requestAnimationFrame(step)
    }
    const stop = () => { running = false; cancelAnimationFrame(raf) }
    const onVisible = () => { shown = !document.hidden; if (shown) start(); else stop() }
    const offDialogs = onDialogChange((isOpen) => { covered = isOpen; if (isOpen) stop(); else start() })
    const svg = el.ownerSVGElement
    const io = svg && typeof IntersectionObserver === 'function'
      ? new IntersectionObserver((entries) => { inView = entries[entries.length - 1].isIntersecting; if (inView) start(); else stop() })
      : null
    document.addEventListener('visibilitychange', onVisible)
    io?.observe(svg)
    start()
    return () => { stop(); document.removeEventListener('visibilitychange', onVisible); offDialogs(); io?.disconnect() }
  }, [live, reduced])

  const known = !!status
  const label = !known ? 'AI' : live ? 'AI live' : 'Rules mode'
  // The model in plain words ("Claude Sonnet 5.5"); the exact id stays in the tooltip.
  const sub = !known ? 'checking…' : live ? (modelName(status.model) || 'connected') : AI_REASON[status.reason] || AI_REASON.UNAVAILABLE
  const calls = known && status.callsToday != null && status.dailyLimit ? ` · ${status.callsToday} of ${status.dailyLimit} calls today` : ''
  const tech = !known ? 'AI mode: checking' : live ? `AI mode LIVE · ${status.model || 'model unknown'}${calls}` : `AI mode RULES · ${status.reason}${calls}`

  return (
    <div className="ai-ind-wrap" ref={box}>
      <button type="button" className={`ai-ind${live ? ' live' : ''}`} onClick={() => setOpen(!open)} aria-expanded={open}
        aria-label={`${label}, ${sub}. Show details`} title={tech}>
        <svg className="ai-wave" viewBox={`0 0 ${W} 14`} width={W} height={14} aria-hidden="true">
          <path ref={path} d={wave(0, 0)} fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span className="ai-txt"><span>{label}</span><small>{sub}</small></span>
      </button>
      {open && (
        <div className="popover ai-pop" role="status">
          <p>{live
            ? 'AI is on. It reads screenshots, drafts reports from a chat, understands plain-English search and writes summaries.'
            : 'Everything still works. Suggestions come from built-in rules until AI is back.'}</p>
        </div>
      )}
    </div>
  )
}
