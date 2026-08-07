import { type PropsWithChildren, useCallback, useEffect, useMemo, useState } from 'react'
import { api, ApiError, type Session } from '../api/client'
import { AuthContext, type AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setSession(await api.currentSession())
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setSession(null)
      } else {
        throw error
      }
    }
  }, [])

  useEffect(() => {
    refresh()
      .catch(() => setSession(null))
      .finally(() => setLoading(false))
  }, [refresh])

  const value = useMemo<AuthContextValue>(() => ({
    session,
    loading,
    login: async (username, password, mode = 'Normal') => {
      await api.login(username, password, mode)
      await refresh()
    },
    logout: async () => {
      try {
        await api.logout(session?.csrfToken ?? null)
      } finally {
        setSession(null)
      }
    },
    changePassword: async (currentPassword, newPassword) => {
      await api.changePassword(currentPassword, newPassword, session?.csrfToken ?? null)
      setSession(null)
    },
    refresh,
  }), [loading, refresh, session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
