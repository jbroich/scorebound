import { createContext } from 'react'
import type { Session } from '../api/client'

export type AuthContextValue = {
  session: Session | null
  loading: boolean
  login: (username: string, password: string, mode?: 'Normal' | 'Display') => Promise<void>
  logout: () => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
