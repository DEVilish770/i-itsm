import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useState } from 'react'

// Dark and light themes. The OS setting decides until the person picks one with the toggle; the pick is
// remembered in localStorage. index.html sets data-theme before the first paint, so there is no flash.
const KEY = 'itsm.theme'

function stored() {
  try { const v = localStorage.getItem(KEY); return v === 'light' || v === 'dark' ? v : null } catch { return null }
}
function remember(v) {
  try { localStorage.setItem(KEY, v) } catch { /* private mode or blocked storage: the choice lasts this visit */ }
}
function osTheme() {
  try { return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark' } catch { return 'dark' }
}
export function prefersReducedMotion() {
  try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches } catch { return false }
}

function paint(theme) {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', theme === 'light' ? '#f3eee5' : '#0f0e0c')
}

const Ctx = createContext({ theme: 'dark', toggle: () => {} })

export function ThemeProvider({ children }) {
  const [choice, setChoice] = useState(stored)
  const [os, setOs] = useState(osTheme)
  const theme = choice || os

  useEffect(() => {
    let mq
    try { mq = window.matchMedia('(prefers-color-scheme: light)') } catch { return undefined }
    const on = () => setOs(mq.matches ? 'light' : 'dark')
    mq.addEventListener?.('change', on)
    return () => mq.removeEventListener?.('change', on)
  }, [])

  useLayoutEffect(() => { paint(theme) }, [theme])

  const toggle = useCallback(() => {
    const next = theme === 'dark' ? 'light' : 'dark'
    remember(next)
    const apply = () => { paint(next); setChoice(next) }
    if (prefersReducedMotion()) { apply(); return }
    // Cross-fade: a view transition where the browser has one, otherwise a short colour transition on everything.
    if (typeof document.startViewTransition === 'function') {
      try { document.startViewTransition(apply); return } catch { /* fall through */ }
    }
    const root = document.documentElement
    root.classList.add('theme-fading')
    apply()
    window.setTimeout(() => root.classList.remove('theme-fading'), 420)
  }, [theme])

  return <Ctx.Provider value={{ theme, toggle }}>{children}</Ctx.Provider>
}

export const useTheme = () => useContext(Ctx)
