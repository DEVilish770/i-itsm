import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { Alert } from '../components/ui.jsx'

export default function Login() {
  const { login, register } = useAuth()
  const nav = useNavigate()
  const [mode, setMode] = useState('login')
  const [f, setF] = useState({ username: '', password: '', fullName: '', email: '', phone: '', department: '' })
  const [err, setErr] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault(); setErr(null); setBusy(true)
    try {
      if (mode === 'login') await login(f.username, f.password); else await register(f)
      nav('/')
    } catch (ex) {
      setErr(ex.body?.fields ? Object.entries(ex.body.fields).map(([k, v]) => `${k}: ${v}`).join('; ') : ex.message)
    } finally { setBusy(false) }
  }

  return (
    <div className="login">
      <form className="card" onSubmit={submit}>
        <h1>I-ITSM</h1>
        <p className="muted">Intelligent IT Service Management &amp; Incident Resolution</p>
        <div className="pill-group" style={{ marginBottom: '1rem' }}>
          <button type="button" className={mode === 'login' ? 'on' : ''} onClick={() => setMode('login')}>Sign in</button>
          <button type="button" className={mode === 'register' ? 'on' : ''} onClick={() => setMode('register')}>Register as employee</button>
        </div>
        <Alert kind="error">{err}</Alert>
        <div className="field"><label>Username</label><input value={f.username} onChange={set('username')} autoFocus required /></div>
        <div className="field"><label>Password</label><input type="password" value={f.password} onChange={set('password')} required /></div>
        {mode === 'register' && <>
          <div className="field"><label>Full name</label><input value={f.fullName} onChange={set('fullName')} required /></div>
          <div className="field"><label>Email</label><input type="email" value={f.email} onChange={set('email')} required /></div>
          <div className="grid cols-2">
            <div className="field"><label>Phone (for SMS)</label><input value={f.phone} onChange={set('phone')} /></div>
            <div className="field"><label>Department</label><input value={f.department} onChange={set('department')} /></div>
          </div>
        </>}
        <div className="actions"><button className="primary" disabled={busy}>{mode === 'login' ? 'Sign in' : 'Create account'}</button></div>
        {mode === 'login' && <p className="small muted" style={{ marginTop: '1rem' }}>Demo accounts (password <span className="kbd">password123</span>): <span className="mono">admin</span>, <span className="mono">tech.arjun</span>, <span className="mono">tech.priya</span>, <span className="mono">emp.raj</span></p>}
      </form>
    </div>
  )
}
