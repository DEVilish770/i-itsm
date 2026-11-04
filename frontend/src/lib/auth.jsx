import { createContext, useContext, useState } from 'react'
import { api } from './api.js'

const Ctx = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem('itsm.user')
    return raw ? JSON.parse(raw) : null
  })

  async function login(username, password) {
    const r = await api('/api/auth/login', { method: 'POST', body: { username, password }, token: '' })
    localStorage.setItem('itsm.token', r.token)
    localStorage.setItem('itsm.user', JSON.stringify(r))
    setUser(r)
    return r
  }
  async function register(form) {
    const r = await api('/api/auth/register', { method: 'POST', body: form, token: '' })
    localStorage.setItem('itsm.token', r.token)
    localStorage.setItem('itsm.user', JSON.stringify(r))
    setUser(r)
    return r
  }
  function logout() {
    localStorage.removeItem('itsm.token'); localStorage.removeItem('itsm.user'); setUser(null)
  }
  return <Ctx.Provider value={{ user, login, register, logout }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
