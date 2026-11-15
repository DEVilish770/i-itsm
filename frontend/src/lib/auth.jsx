import { createContext, useContext, useState } from 'react'
import { api } from './api.js'

const Ctx = createContext(null)

// sessionStorage, not localStorage: every browser tab/window keeps its own login,
// so the demo can run employee, technician and admin side by side in one browser.
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = sessionStorage.getItem('itsm.user')
    return raw ? JSON.parse(raw) : null
  })

  async function login(username, password) {
    const r = await api('/api/auth/login', { method: 'POST', body: { username, password }, token: '' })
    sessionStorage.setItem('itsm.token', r.token)
    sessionStorage.setItem('itsm.user', JSON.stringify(r))
    setUser(r)
    return r
  }
  async function register(form) {
    const r = await api('/api/auth/register', { method: 'POST', body: form, token: '' })
    sessionStorage.setItem('itsm.token', r.token)
    sessionStorage.setItem('itsm.user', JSON.stringify(r))
    setUser(r)
    return r
  }
  function logout() {
    sessionStorage.removeItem('itsm.token'); sessionStorage.removeItem('itsm.user'); setUser(null)
  }
  return <Ctx.Provider value={{ user, login, register, logout }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
