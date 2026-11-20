import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { setUnauthorizedHandler, tokenStore } from '../../shared/api/client'
import { authApi } from './authApi'

/*
 * SDD 6.1 AuthContext: holds the signed-in user and role, and decides which menus are shown.
 * Hiding a menu is only for convenience. The server checks the role again on every call.
 */

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) {
      setReady(true)
      return
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setReady(true))
  }, [logout])

  const login = useCallback(async (username, password) => {
    const res = await authApi.login(username, password)
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }, [])

  /** The server ends older sessions on a password change, so the new token replaces the old one. */
  const changePassword = useCallback(async (currentPassword, newPassword) => {
    const res = await authApi.changePassword(currentPassword, newPassword)
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }, [])

  const value = useMemo(
    () => ({
      user,
      ready,
      login,
      logout,
      changePassword,
      can: (roles) => !!user && (!roles || roles.includes(user.role)),
    }),
    [user, ready, login, logout, changePassword],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
