import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import * as authApi from '../services/auth'
import { configureHttp } from '../services/http'
import { decodeJwt, isExpired, roleFromClaims } from '../utils/jwt'

const AuthContext = createContext(null)
const KEY = 'turf.token'

// Trade-off: the backend returns the JWT in the response body (no HttpOnly cookie), so JavaScript must hold it.
// sessionStorage keeps it per tab and drops it when the tab closes; it is readable by any script on this origin, so
// the app avoids injecting HTML and the token is never logged. There is no refresh endpoint: tokens last 1 hour.
const store = {
  get: () => { try { return sessionStorage.getItem(KEY) } catch { return null } },
  set: (t) => { try { sessionStorage.setItem(KEY, t) } catch { /* storage blocked: session lasts until reload */ } },
  clear: () => { try { sessionStorage.removeItem(KEY) } catch { /* ignore */ } },
}

function sessionFromToken(token) {
  const claims = decodeJwt(token)
  if (!claims || isExpired(claims)) return null
  return { token, email: claims.sub, role: roleFromClaims(claims), expiresAt: claims.exp * 1000 }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => { const t = store.get(); return t ? sessionFromToken(t) : null })
  const [profile, setProfile] = useState(null)
  const [notice, setNotice] = useState(null)
  const tokenRef = useRef(session?.token ?? null)
  tokenRef.current = session?.token ?? null

  const logout = useCallback((message) => {
    store.clear()
    setSession(null)
    setProfile(null)
    setNotice(typeof message === 'string' ? message : null)
  }, [])

  useEffect(() => {
    configureHttp({
      getToken: () => tokenRef.current,
      onUnauthorized: () => { if (tokenRef.current) logout('Your session expired. Please log in again.') },
    })
  }, [logout])

  // Sign out exactly when the token expires, instead of waiting for a 401.
  useEffect(() => {
    if (!session) return undefined
    const ms = Math.min(session.expiresAt - Date.now(), 2 ** 31 - 1)
    const id = setTimeout(() => logout('Your session expired. Please log in again.'), Math.max(ms, 0))
    return () => clearTimeout(id)
  }, [session, logout])

  // Restore the profile after a page reload (the token alone is enough to call /users/{email}).
  useEffect(() => {
    if (session && !profile) {
      authApi.getMe(session.email).then(setProfile).catch(() => { /* 401 is handled by the http client */ })
    }
  }, [session, profile])

  /** Logs in, then proves the token is accepted by the server before the UI treats the user as authenticated. */
  const login = useCallback(async (email, password) => {
    const token = await authApi.login(email, password)
    const next = sessionFromToken(token)
    if (!next) throw new Error('The server returned an unusable token.')
    tokenRef.current = token // so the verification call below carries it
    let me
    try {
      me = await authApi.getMe(next.email)
    } catch (e) {
      tokenRef.current = null
      throw e
    }
    store.set(token)
    setNotice(null)
    setSession(next)
    setProfile(me)
    return { ...next, role: me.role || next.role }
  }, [])

  const value = useMemo(() => ({
    isAuthenticated: !!session,
    token: session?.token ?? null,
    email: session?.email ?? null,
    role: session?.role ?? null,
    profile,
    notice,
    clearNotice: () => setNotice(null),
    login,
    logout,
  }), [session, profile, notice, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

export const homePathFor = (role) => (role === 'OWNER' ? '/owner' : role === 'ADMIN' ? '/admin' : '/')
