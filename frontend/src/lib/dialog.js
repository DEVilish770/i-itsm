import { useEffect, useRef } from 'react'

// The open dialogs (search palette, chat sheet, modal), newest last. Only the top one answers Escape, and Tab and
// Shift+Tab stay inside it. While any dialog is open, the app underneath is inert (no focus, hidden from screen
// readers), ⌘K leaves a chat or a modal alone, and the decorative animations behind the scrim rest.
const stack = []
const listeners = new Set()
let wasOpen = false

export const dialogOpen = () => stack.length > 0
export const topDialog = () => stack[stack.length - 1] || null

/** Calls fn(open) when the first dialog opens and when the last one closes. Returns the unsubscribe function. */
export function onDialogChange(fn) {
  listeners.add(fn)
  return () => { listeners.delete(fn) }
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex], [contenteditable="true"]'
const FIELD = 'input, select, textarea, [contenteditable="true"]'

/** The controls Tab can reach inside el, in page order (rendered ones only; tabindex -1 left out). */
function focusables(el) {
  return Array.from(el.querySelectorAll(FOCUSABLE)).filter((n) => n.tabIndex >= 0 && n.getClientRects().length > 0)
}

/** Where focus goes when a dialog opens: its first field, else its first control, else the dialog itself. */
function firstStop(el) {
  const list = focusables(el)
  return list.find((n) => n.matches(FIELD)) || list[0] || el
}

function onKey(e) {
  const top = topDialog()
  if (!top) return
  if (e.key === 'Escape') {
    if (e.isComposing) return // Escape cancels an IME composition first
    e.preventDefault()
    top.close()
    return
  }
  if (e.key !== 'Tab') return
  const list = focusables(top.el)
  if (!list.length) { e.preventDefault(); top.el.focus(); return }
  const first = list[0], last = list[list.length - 1]
  const active = document.activeElement
  const inside = active !== top.el && top.el.contains(active)
  if (e.shiftKey && (!inside || active === first)) { e.preventDefault(); last.focus() }
  else if (!e.shiftKey && (!inside || active === last)) { e.preventDefault(); first.focus() }
}

function sync() {
  const open = stack.length > 0
  document.getElementById('root')?.toggleAttribute('inert', open)
  if (open === wasOpen) return
  wasOpen = open
  if (open) window.addEventListener('keydown', onKey)
  else window.removeEventListener('keydown', onKey)
  listeners.forEach((fn) => fn(open))
}

/**
 * Makes the element in ref a modal dialog: focus moves in (unless something inside already took it, like an
 * autoFocus field), Tab wraps inside, Escape calls onClose (top dialog only), and on close focus returns to the
 * control that opened it. kind names the dialog ('palette', 'chat', 'modal') for the ⌘K check.
 */
export function useDialog(ref, kind, onClose) {
  const close = useRef(onClose)
  close.current = onClose
  // The control that opened the dialog, read during the first render: by the time effects run, an autoFocus field
  // inside the dialog already has focus.
  const opener = useRef(undefined)
  if (opener.current === undefined) opener.current = document.activeElement
  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const before = opener.current
    const entry = { kind, el, close: () => close.current?.() }
    stack.push(entry)
    sync()
    if (!el.contains(document.activeElement)) firstStop(el).focus({ preventScroll: true })
    return () => {
      const i = stack.indexOf(entry)
      if (i >= 0) stack.splice(i, 1)
      sync()
      if (before && before !== document.body && typeof before.focus === 'function' && document.contains(before)) {
        before.focus({ preventScroll: true })
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
}
