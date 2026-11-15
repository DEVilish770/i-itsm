// Small line icons (20 px grid, 1.5 px stroke), drawn inline so they follow the text colour.
const PATHS = {
  dashboard: <path d="M2.5 10.5h3.2l2.1-5.2 4.2 9.4 2.1-4.2h3.4" />,
  incidents: <><path d="M7.5 5.5h9M7.5 10h9M7.5 14.5h9" /><path d="M3.6 5.5h.01M3.6 10h.01M3.6 14.5h.01" strokeWidth="2.2" /></>,
  report: <><path d="M10 4.5v11M4.5 10h11" /></>,
  queue: <><path d="M3 11.6l2.1-5.9A1.6 1.6 0 0 1 6.6 4.6h6.8a1.6 1.6 0 0 1 1.5 1.1l2.1 5.9v3.3a1.5 1.5 0 0 1-1.5 1.5H4.5A1.5 1.5 0 0 1 3 14.9z" /><path d="M3.2 11.6h3.9l1 1.9h3.8l1-1.9h3.9" /></>,
  reports: <path d="M4.5 16V10M8.2 16V4.5M11.8 16v-4.5M15.5 16V7.5" />,
  people: <><circle cx="7.6" cy="7.2" r="2.6" /><path d="M2.9 16c.6-2.6 2.5-4.1 4.7-4.1s4.1 1.5 4.7 4.1" /><path d="M13.1 4.8a2.5 2.5 0 0 1 0 4.8M14.7 12.3c1.4.6 2.3 1.9 2.6 3.7" /></>,
  settings: <><path d="M3.5 6.5h7M15 6.5h1.5M3.5 13.5h1.5M9 13.5h7.5" /><circle cx="12.8" cy="6.5" r="1.9" /><circle cx="7" cy="13.5" r="1.9" /></>,
  audit: <><circle cx="10" cy="10" r="6.6" /><path d="M10 6.4V10l2.6 1.6" /></>,
  signout: <><path d="M8.2 4.2H5.6a1.5 1.5 0 0 0-1.5 1.5v8.6a1.5 1.5 0 0 0 1.5 1.5h2.6" /><path d="M12.4 6.8L15.6 10l-3.2 3.2M15.4 10H8.2" /></>,
  bell: <><path d="M5.9 8.4a4.1 4.1 0 0 1 8.2 0c0 3.7 1.6 5.1 1.6 5.1H4.3s1.6-1.4 1.6-5.1z" /><path d="M8.4 15.9a1.7 1.7 0 0 0 3.2 0" /></>,
  sun: <><circle cx="10" cy="10" r="3.1" /><path d="M10 2.6v1.7M10 15.7v1.7M2.6 10h1.7M15.7 10h1.7M4.8 4.8l1.2 1.2M14 14l1.2 1.2M4.8 15.2L6 14M14 6l1.2-1.2" /></>,
  moon: <path d="M15.6 12.3A6.2 6.2 0 0 1 7.7 4.4a6.2 6.2 0 1 0 7.9 7.9z" />,
  search: <><circle cx="8.8" cy="8.8" r="5.1" /><path d="M12.6 12.6L16.4 16.4" /></>,
  image: <><rect x="3" y="4.5" width="14" height="11" rx="1.6" /><circle cx="7.3" cy="8.4" r="1.2" /><path d="M3.6 14.1l3.9-3.4 3 2.4 2.4-1.9 3.5 2.9" /></>,
  chat: <path d="M4 5.6A1.6 1.6 0 0 1 5.6 4h8.8A1.6 1.6 0 0 1 16 5.6v5.8a1.6 1.6 0 0 1-1.6 1.6H9.2L5.8 16v-3H5.6A1.6 1.6 0 0 1 4 11.4z" />,
  close: <path d="M5.6 5.6l8.8 8.8M14.4 5.6l-8.8 8.8" />,
  arrow: <path d="M4 10h11.5M11.2 5.7L15.5 10l-4.3 4.3" />,
  back: <path d="M16 10H4.5M8.8 5.7L4.5 10l4.3 4.3" />,
  send: <path d="M4 10h11.5M11.2 5.7L15.5 10l-4.3 4.3" />,
  check: <path d="M4.5 10.5l3.4 3.3 7.6-7.6" />
}

export function Icon({ name, size = 20, className = '', title }) {
  return (
    <svg className={`icon ${className}`} viewBox="0 0 20 20" width={size} height={size} fill="none" stroke="currentColor"
      strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden={title ? undefined : 'true'} role={title ? 'img' : undefined}>
      {title && <title>{title}</title>}
      {PATHS[name]}
    </svg>
  )
}

/** The mark: nine dots, a small echo of the dot field. */
export function Mark({ size = 22 }) {
  const dots = []
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) dots.push([c, r])
  return (
    <svg className="mark-dots" viewBox="0 0 22 22" width={size} height={size} aria-hidden="true">
      {dots.map(([c, r]) => <circle key={`${c}${r}`} cx={4 + c * 7} cy={4 + r * 7} r={1.7} fill="currentColor" opacity={0.35 + 0.3 * c + (r === 1 ? 0.05 : 0)} />)}
    </svg>
  )
}
