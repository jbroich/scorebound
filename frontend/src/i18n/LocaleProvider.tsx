import {
  type PropsWithChildren,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { LocaleContext, type LocaleContextValue } from './LocaleContext'
import { intlLocales, messages, type Locale } from './messages'

const STORAGE_KEY = 'scorebound.locale'
const DEFAULT_TIME_ZONE = 'Europe/Berlin'

function readSavedLocale(): Locale {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'de' ? 'de' : 'en'
  } catch {
    return 'en'
  }
}

export function LocaleProvider({ children }: PropsWithChildren) {
  const [locale, setLocale] = useState<Locale>(readSavedLocale)
  const timeZone = import.meta.env.VITE_SCOREBOUND_TIME_ZONE?.trim() || DEFAULT_TIME_ZONE

  useEffect(() => {
    document.documentElement.lang = locale
    try {
      window.localStorage.setItem(STORAGE_KEY, locale)
    } catch {
      // The interface still works when browser storage is unavailable.
    }
  }, [locale])

  const value = useMemo<LocaleContextValue>(() => {
    const intlLocale = intlLocales[locale]
    const numberFormatter = new Intl.NumberFormat(intlLocale)
    const dateTimeFormatter = new Intl.DateTimeFormat(intlLocale, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone,
    })

    return {
      locale,
      setLocale,
      t: (key) => messages[locale][key],
      formatNumber: (number) => numberFormatter.format(number),
      formatDateTime: (date) => dateTimeFormatter.format(date),
      timeZone,
    }
  }, [locale, timeZone])

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
}
