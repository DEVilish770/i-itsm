import { useEffect, useRef } from 'react'
import { dialogOpen, onDialogChange } from '../lib/dialog.js'

/**
 * The decorative dot field: a grid of tiny dots (13 px apart, 11 px down) moving in slow waves, drawn in the
 * accent colour at low opacity. Dots lift and brighten within ~90 px of the cursor.
 *
 * variant  hero  – strongest toward the right, fading out behind text on the left (dashboard header band)
 *          band  – the thin band at the top of pages: same fade, smaller waves
 *          panel – fills the whole panel, no fade (login)
 *          empty – behind empty states: clear in the middle where the words are
 * clearRight – px kept clear at the right edge (for buttons sitting on a band)
 *
 * Rests whenever it can't be seen: the tab is hidden, the field is scrolled away (or sits under the sticky top
 * bar), or a dialog's scrim covers the page. Draws one still frame under reduced motion, re-reads the colour when
 * the theme changes, and follows devicePixelRatio and resizes. The waves are slow, so about 30 frames a second
 * looks the same as 60; dots of equal opacity are filled together, a few dozen fills a frame instead of thousands.
 */
const VARIANTS = {
  hero: { amp: 2.6, lift: 5, fade: 'right' },
  band: { amp: 1.15, lift: 2.6, fade: 'right' },
  panel: { amp: 2.6, lift: 5, fade: 'none' },
  empty: { amp: 2, lift: 4, fade: 'centre' }
}
const RADIUS = 90
const FRAME_MS = 1000 / 30
const SIXTIETH = 1000 / 60
const LEVELS = 64 // opacity steps for the batches; 64 look continuous at these low opacities

function readColor() {
  const css = getComputedStyle(document.documentElement)
  return css.getPropertyValue('--dots').trim() || css.getPropertyValue('--accent').trim() || '#c9a24e'
}
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v)
/** The share of the way an easing step tuned for 60 frames a second covers in dt ms (dt 0: none). */
const ease = (k, dt) => 1 - Math.pow(1 - k, dt / SIXTIETH)

export default function DotField({ variant = 'hero', clearRight = 0, className = '' }) {
  const ref = useRef(null)
  const props = useRef({ variant, clearRight })
  props.current = { variant, clearRight }
  const still = useRef(null) // redraws once when the loop isn't running (reduced motion, hidden)

  useEffect(() => { still.current?.() }, [variant, clearRight])

  useEffect(() => {
    const canvas = ref.current
    const ctx = canvas?.getContext('2d')
    if (!ctx) return undefined

    let W = 0, H = 0, dpr = 1
    let raf = 0, running = false, last = 0
    let color = readColor()
    let docVisible = !document.hidden, inView = true, covered = dialogOpen() && !canvas.closest('[role="dialog"]')
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    let reduced = motion.matches
    // cursor: client position, eased position relative to the canvas, eased strength (0 outside the band)
    let cx = -1e5, cy = -1e5, mx = -1e5, my = -1e5, hover = 0
    // where the canvas is on screen: read again only after the pointer moved, the page scrolled or resized,
    // and twice a second at most otherwise (content above it can move it)
    let left = 0, top = 0, rectStale = true, rectAt = 0

    function draw(t, dt) {
      const { variant: v, clearRight: cr } = props.current
      const cfg = VARIANTS[v] || VARIANTS.hero
      const tt = reduced ? 0 : t
      // cursor easing (skipped when still: a still frame has no cursor reaction)
      if (!reduced) {
        if (rectStale) { const r = canvas.getBoundingClientRect(); left = r.left; top = r.top; rectStale = false; rectAt = t }
        const tx = cx - left, ty = cy - top
        const near = tx > -RADIUS && tx < W + RADIUS && ty > -RADIUS && ty < H + RADIUS
        if (near) {
          if (hover < 0.01) { mx = tx; my = ty } // first touch: no slide in from far away
          const k = ease(0.22, dt)
          mx += (tx - mx) * k; my += (ty - my) * k
        }
        hover += ((near ? 1 : 0) - hover) * ease(0.12, dt)
      } else hover = 0

      ctx.clearRect(0, 0, W, H)
      ctx.fillStyle = color
      const R2 = RADIUS * RADIUS
      const batches = [] // one path per opacity step
      for (let y = 7; y < H; y += 11) {
        for (let x = 7; x < W; x += 13) {
          let fade = 1
          if (cfg.fade === 'right') fade = clamp01((x - W * 0.32) / (W * 0.42))
          else if (cfg.fade === 'centre') {
            const dx = (x - W / 2) / (W * 0.5), dy = (y - H / 2) / (H * 0.5)
            fade = clamp01((Math.sqrt(dx * dx + dy * dy) - 0.32) / 0.55)
          }
          if (cr) fade *= clamp01((W - cr - x) / 56)
          const z = Math.sin(x * 0.016 + tt * 0.0007 + y * 0.04) * Math.cos(y * 0.09 - tt * 0.0005)
          let yy = y + z * cfg.amp
          let k = 0
          if (hover > 0.01) {
            const dx = x - mx, dy = yy - my, d2 = dx * dx + dy * dy
            if (d2 < R2) k = (1 - Math.sqrt(d2) / RADIUS) * hover
          }
          yy -= k * cfg.lift
          const level = Math.round(Math.min(1, (0.05 + 0.32 * fade) * (0.55 + 0.45 * (z + 1) / 2) + k * 0.55) * LEVELS)
          if (level <= 0) continue
          const rad = 0.8 + 0.5 * (z + 1) / 2 + k * 0.9
          const p = batches[level] || (batches[level] = new Path2D())
          p.moveTo(x + rad, yy)
          p.arc(x, yy, rad, 0, 6.283)
        }
      }
      for (let i = 1; i <= LEVELS; i++) {
        if (!batches[i]) continue
        ctx.globalAlpha = i / LEVELS
        ctx.fill(batches[i])
      }
      ctx.globalAlpha = 1
    }

    function frame(t) {
      if (!running) return
      if (!last || t - last >= FRAME_MS - 3) {
        const dt = last ? Math.min(100, t - last) : FRAME_MS
        last = t
        if (t - rectAt > 500) rectStale = true
        draw(t, dt)
      }
      raf = requestAnimationFrame(frame)
    }
    function start() {
      if (running || reduced || !docVisible || !inView || covered || !W || !H) return
      running = true
      last = 0
      raf = requestAnimationFrame(frame)
    }
    function stop() { running = false; cancelAnimationFrame(raf) }
    const redraw = () => { if (W && H) draw(reduced ? 0 : performance.now(), 0) }

    function resize() {
      const r = canvas.getBoundingClientRect()
      dpr = Math.min(3, window.devicePixelRatio || 1)
      W = Math.max(0, Math.round(r.width)); H = Math.max(0, Math.round(r.height))
      canvas.width = Math.max(1, Math.round(W * dpr)); canvas.height = Math.max(1, Math.round(H * dpr))
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      rectStale = true
      redraw() // resizing clears the canvas: draw now rather than wait for the next frame
      start()
    }

    const ro = new ResizeObserver(resize)
    ro.observe(canvas)
    window.addEventListener('resize', resize) // devicePixelRatio changes (zoom, another screen)

    // Inside the app the sticky top bar covers the top of the viewport: a band scrolled under it counts as hidden.
    const bar = document.querySelector('.topbar')
    const under = bar ? Math.round(bar.getBoundingClientRect().height) : 0
    const io = typeof IntersectionObserver === 'function'
      ? new IntersectionObserver((entries) => { inView = entries[entries.length - 1].isIntersecting; if (inView) start(); else stop() },
        { rootMargin: `${-under}px 0px 0px 0px` })
      : null
    io?.observe(canvas)

    const onVisibility = () => { docVisible = !document.hidden; if (docVisible) start(); else stop() }
    document.addEventListener('visibilitychange', onVisibility)

    const offDialogs = onDialogChange((open) => { covered = open && !canvas.closest('[role="dialog"]'); if (covered) stop(); else start() })

    const onMotion = () => { reduced = motion.matches; if (reduced) { stop(); draw(0, 0) } else start() }
    motion.addEventListener?.('change', onMotion)

    const onTheme = new MutationObserver(() => { color = readColor(); if (!running) redraw() })
    onTheme.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    const onMove = (e) => { cx = e.clientX; cy = e.clientY; rectStale = true }
    const onLeave = () => { cx = -1e5; cy = -1e5 }
    const onScroll = () => { rectStale = true }
    window.addEventListener('pointermove', onMove, { passive: true })
    window.addEventListener('scroll', onScroll, { passive: true, capture: true })
    document.documentElement.addEventListener('pointerleave', onLeave)

    still.current = () => { if (!running) redraw() }
    resize()
    return () => {
      still.current = null
      stop()
      ro.disconnect(); io?.disconnect(); onTheme.disconnect(); offDialogs()
      window.removeEventListener('resize', resize)
      document.removeEventListener('visibilitychange', onVisibility)
      motion.removeEventListener?.('change', onMotion)
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('scroll', onScroll, { capture: true })
      document.documentElement.removeEventListener('pointerleave', onLeave)
    }
  }, [])

  return <canvas ref={ref} className={`dots ${className}`} aria-hidden="true" />
}
