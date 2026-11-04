import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth } from './lib/auth.jsx'
import { Bell } from './components/ui.jsx'
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

function Shell({ children }) {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const role = user.role
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">I-ITSM<small>Intelligent IT Service Management</small></div>
        {role === 'EMPLOYEE' && <>
          <div className="section">Employee</div>
          <NavLink to="/incidents/new">➕ Report an incident</NavLink>
          <NavLink to="/incidents" end>📋 My incidents</NavLink>
        </>}
        {role === 'TECHNICIAN' && <>
          <div className="section">Technician</div>
          <NavLink to="/queue">🛠 My queue</NavLink>
          <NavLink to="/incidents" end>🔎 All incidents</NavLink>
        </>}
        {role === 'ADMIN' && <>
          <div className="section">Operations</div>
          <NavLink to="/dashboard">📡 Live dashboard</NavLink>
          <NavLink to="/incidents" end>🔎 Incidents</NavLink>
          <NavLink to="/reports">📊 Reports</NavLink>
          <div className="section">Configuration</div>
          <NavLink to="/admin/users">👥 Users & technicians</NavLink>
          <NavLink to="/admin/catalog">⚙️ Services, categories, SLA</NavLink>
          <NavLink to="/admin/audit">🧾 Audit log & outbox</NavLink>
        </>}
        <div className="me"><b>{user.fullName}</b><span className="badge role">{role}</span>
          <div style={{ marginTop: '.5rem' }}><button className="sm" onClick={() => { logout(); nav('/login') }}>Sign out</button></div></div>
      </aside>
      <main className="main">
        <div className="topbar"><div /><Bell /></div>
        {children}
      </main>
    </div>
  )
}

function Private({ roles, children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />
  return <Shell>{children}</Shell>
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
      <Route path="/incidents" element={<Private><IncidentList /></Private>} />
      <Route path="/incidents/new" element={<Private roles={['EMPLOYEE', 'ADMIN']}><NewIncident /></Private>} />
      <Route path="/incidents/:id" element={<Private><IncidentDetail /></Private>} />
      <Route path="/queue" element={<Private roles={['TECHNICIAN', 'ADMIN']}><TechnicianQueue /></Private>} />
      <Route path="/dashboard" element={<Private roles={['ADMIN']}><Dashboard /></Private>} />
      <Route path="/reports" element={<Private roles={['ADMIN']}><Reports /></Private>} />
      <Route path="/admin/users" element={<Private roles={['ADMIN']}><AdminUsers /></Private>} />
      <Route path="/admin/catalog" element={<Private roles={['ADMIN']}><AdminCatalog /></Private>} />
      <Route path="/admin/audit" element={<Private roles={['ADMIN']}><AdminAudit /></Private>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
