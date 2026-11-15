import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { errText } from '../lib/api.js'
import { Alert, Tabs, ThemeToggle } from '../components/ui.jsx'
import DotField from '../components/DotField.jsx'
import { Mark } from '../components/icons.jsx'

const DEMO = ['admin', 'tech.arjun', 'tech.priya', 'emp.raj']

export default function Login() {
  const { login, register } = useAuth()
  const nav = useNavigate()
  const [mode, setMode] = useState('login')
  const [f, setF] = useState({ username: '', password: '', fullName: '', email: '', phone: '', department: '' })
  const [err, setErr] = useState(null)
  const [busy, setBusy] = useState(false)
  const password = useRef(null)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  useEffect(() => { document.title = mode === 'login' ? 'Sign in · I-ITSM' : 'Register · I-ITSM' }, [mode])

  async function submit(e) {
    e.preventDefault(); setErr(null); setBusy(true)
    try {
      if (mode === 'login') await login(f.username, f.password); else await register(f)
      nav('/')
    } catch (ex) {
      setErr(errText(ex))
    } finally { setBusy(false) }
  }

  function pickDemo(name) {
    setF({ ...f, username: name })
    password.current?.focus()
  }

  return (
    <div className="login">
      <div className="login-art" aria-hidden="true">
        <DotField variant="panel" />
        <div className="login-mark"><Mark size={20} /><span>I-ITSM</span></div>
      </div>
      <div className="login-side">
        <div className="login-top"><ThemeToggle /></div>
        <form className="login-form" onSubmit={submit} key={mode}>
          <h1 className="login-title">{mode === 'login' ? 'Welcome back.' : 'Join your team.'}</h1>
          <p className="login-sub">IT help that keeps track, keeps time and keeps you posted.</p>
          <Tabs value={mode} onChange={(m) => { setMode(m); setErr(null) }} label="Sign in or register"
            options={[['login', 'Sign in'], ['register', 'Register as employee']]} />
          <Alert kind="error">{err}</Alert>
          <div className="field"><label htmlFor="u">Username</label><input id="u" value={f.username} onChange={set('username')} autoComplete="username" autoFocus required /></div>
          <div className="field"><label htmlFor="p">Password</label><input id="p" ref={password} type="password" value={f.password} onChange={set('password')} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required /></div>
          {mode === 'register' && <>
            <div className="field"><label htmlFor="n">Full name</label><input id="n" value={f.fullName} onChange={set('fullName')} autoComplete="name" required /></div>
            <div className="field"><label htmlFor="e">Email</label><input id="e" type="email" value={f.email} onChange={set('email')} autoComplete="email" required /></div>
            <div className="grid-2">
              <div className="field"><label htmlFor="ph">Phone, for text alerts</label><input id="ph" value={f.phone} onChange={set('phone')} autoComplete="tel" /></div>
              <div className="field"><label htmlFor="d">Department</label><input id="d" value={f.department} onChange={set('department')} /></div>
            </div>
          </>}
          <div className="form-actions"><button className="btn wide" disabled={busy}>{busy ? 'One moment…' : mode === 'login' ? 'Sign in' : 'Create account'}</button></div>
          {mode === 'login' && (
            <p className="demo-line">
              Demo accounts, password <code>password123</code>:{' '}
              {DEMO.map((d, i) => <span key={d}><button type="button" className="link" onClick={() => pickDemo(d)}>{d}</button>{i < DEMO.length - 1 ? ', ' : ''}</span>)}
            </p>
          )}
        </form>
      </div>
    </div>
  )
}
