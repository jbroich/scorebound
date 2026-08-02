import { createContext } from 'react'
import type { Locale, MessageKey } from './messages'

export type LocaleContextValue = {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: MessageKey) => string
  formatNumber: (value: number) => string
  formatDateTime: (value: Date) => string
  timeZone: string
}

export const LocaleContext = createContext<LocaleContextValue | null>(null)
