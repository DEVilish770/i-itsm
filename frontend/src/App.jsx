import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './lib/auth.jsx'
import { AiProvider } from './lib/ai.jsx'
import { topDialog } from './lib/dialog.js'
import { ROLE_WORD, initials, isMac } from './lib/words.js'
import { Bell, ThemeToggle, TitleCtx } from './components/ui.jsx'
import { Icon, Mark } from './components/icons.jsx'
import AiIndicator from './components/AiIndicator.jsx'
import SearchPalette, { SearchLine } from './components/SearchPalette.jsx'
import Login from './pages/Login.jsx'
import IncidentList from './pages/IncidentList.jsx'
import NewIncident from './pages/NewIncident.jsx'
import IncidentDetail from './pages/IncidentDetail.jsx'
import TechnicianQueue from './pages/TechnicianQueue.jsx'
import Dashboard from './pages/Dashboard.jsx'
import AdminUsers from './pages/AdminUsers.jsx'
import AdminCatalog from './pages/AdminCatalog.jsx'
import AdminAudit from './pages/AdminAudit.jsx'
import Reports from './pages/Reports.jsx'

const NAV = {
  EMPLOYEE: [
    [{ to: '/incidents/new', icon: 'report', label: 'Report a problem' }, { to: '/incidents', icon: 'incidents', label: 'My incidents', also: /^\/incidents\/\d+/ }]
  ],
  TECHNICIAN: [
    [{ to: '/queue', icon: 'queue', label: 'My queue' }, { to: '/incidents', icon: 'incidents', label: 'All incidents', also: /^\/incidents\/\d+/ }]
  ],
  ADMIN: [
    [{ to: '/dashboard', icon: 'dashboard', label: 'Live dashboard' }, { to: '/incidents', icon: 'incidents', label: 'Incidents', also: /^\/incidents\/(\d+|new)/ }, { to: '/reports', icon: 'reports', label: 'Reports' }],
    [{ to: '/admin/users', icon: 'people', label: 'Users & technicians' }, { to: '/admin/catalog', icon: 'settings', label: 'Settings: services, categories, SLA, strategies, AI' }, { to: '/admin/audit', icon: 'audit', label: 'Audit log & email outbox' }]
  ]
}

function routeTitle(path, role) {
  if (path === '/dashboard') return 'Dashboard'
  if (path === '/incidents') return role === 'EMPLOYEE' ? 'My incidents' : 'Incidents'
  if (path === '/incidents/new') return 'Report a problem'
  if (path.startsWith('/incidents/')) return 'Incident'
  if (path === '/queue') return 'My queue'
  if (path === '/reports') return 'Reports'
  if (path === '/admin/users') return 'People'
  if (path === '/admin/catalog') return 'Settings'
  if (path === '/admin/audit') return 'Audit log'
  return 'I-ITSM'
}

function Rail({ user, onSignOut }) {
  const { pathname } = useLocation()
  const groups = NAV[user.role] || []
  return (
    <nav className="rail" aria-label="Main">
      <Link to="/" className="rail-mark" aria-label="I-ITSM home" data-tip="I-ITSM"><Mark /></Link>
      {groups.map((items, gi) => (
        <div className="rail-group" key={gi}>
          {items.map((it) => {
            const active = pathname === it.to || !!it.also?.test(pathname)
            return (
              <Link key={it.to} to={it.to} className={`rail-link${active ? ' active' : ''}`} aria-label={it.label} aria-current={active ? 'page' : undefined} data-tip={it.label}>
                <Icon name={it.icon} />
              </Link>
            )
          })}
        </div>
      ))}
      <div className="rail-foot">
        <div className="rail-me" data-tip={`${user.fullName} · ${ROLE_WORD[user.role] || user.role}`} tabIndex={0} aria-label={`Signed in as ${user.fullName}, ${ROLE_WORD[user.role] || user.role}`}>
          <span className="rail-initials">{initials(user)}</span>
          <span className="rail-role">{ROLE_WORD[user.role] || user.role}</span>
        </div>
        <button type="button" className="rail-link" onClick={onSignOut} aria-label="Sign out" data-tip="Sign out"><Icon name="signout" /></button>
      </div>
    </nav>
  )
}

/** Logged-in frame: icon rail, top bar (serif title, plain-English search, AI mode, theme, bell), the page. */
function Shell() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const [override, setOverride] = useState(null) // { path, text } set by the page
  const [palette, setPalette] = useState(false)
  const setTitle = useCallback((t) => setOverride((o) => (o && o.path === t.path && o.text === t.text ? o : t)), [])
  const title = override?.path === loc.pathname ? override.text : routeTitle(loc.pathname, user.role)

  useEffect(() => { document.title = `${title} · I-ITSM` }, [title])

  // ⌘K (Ctrl+K on Windows and Linux) opens the search palette from anywhere, and closes it again. Not while a chat
  // sheet or a modal is open: the palette would cover it, and one Escape could lose what was typed there. On a Mac,
  // Ctrl+K is left to text fields (delete to the end of the line).
  useEffect(() => {
    const mac = isMac()
    const key = (e) => {
      const mod = mac ? e.metaKey && !e.ctrlKey : e.metaKey || e.ctrlKey
      if (!mod || e.altKey || String(e.key).toLowerCase() !== 'k') return
      const top = topDialog()
      if (top && top.kind !== 'palette') return
      e.preventDefault(); setPalette((p) => !p)
    }
    window.addEventListener('keydown', key)
    return () => window.removeEventListener('keydown', key)
  }, [])

  const closePalette = useCallback(() => setPalette(false), [])

  return (
    <AiProvider>
      <TitleCtx.Provider value={setTitle}>
        <div className="app">
          <Rail user={user} onSignOut={() => { logout(); nav('/login') }} />
          <div className="main">
            <header className="topbar">
              <div className="topbar-in">
                <h1 className="page-title" key={title}>{title}</h1>
                <SearchLine onOpen={() => setPalette(true)} />
                <div className="top-tools">
                  <AiIndicator />
                  <ThemeToggle />
                  <Bell />
                </div>
              </div>
            </header>
            <main className="page" key={loc.pathname}><Outlet /></main>
          </div>
        </div>
        {palette && <SearchPalette onClose={closePalette} />}
      </TitleCtx.Provider>
    </AiProvider>
  )
}

function Private() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Shell />
}

function Only({ roles, children }) {
  const { user } = useAuth()
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />
  return children
}

function Home() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={user.role === 'ADMIN' ? '/dashboard' : user.role === 'TECHNICIAN' ? '/queue' : '/incidents'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Home />} />
      <Route element={<Private />}>
        <Route path="/incidents" element={<IncidentList />} />
        <Route path="/incidents/new" element={<Only roles={['EMPLOYEE', 'ADMIN']}><NewIncident /></Only>} />
        <Route path="/incidents/:id" element={<IncidentDetail />} />
        <Route path="/queue" element={<Only roles={['TECHNICIAN', 'ADMIN']}><TechnicianQueue /></Only>} />
        <Route path="/dashboard" element={<Only roles={['ADMIN']}><Dashboard /></Only>} />
        <Route path="/reports" element={<Only roles={['ADMIN']}><Reports /></Only>} />
        <Route path="/admin/users" element={<Only roles={['ADMIN']}><AdminUsers /></Only>} />
        <Route path="/admin/catalog" element={<Only roles={['ADMIN']}><AdminCatalog /></Only>} />
        <Route path="/admin/audit" element={<Only roles={['ADMIN']}><AdminAudit /></Only>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
