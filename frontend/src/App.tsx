import { useEffect, useState } from 'react'
import './App.css'
import { LocaleProvider } from './i18n/LocaleProvider'
import type { Locale } from './i18n/messages'
import { useLocale } from './i18n/useLocale'

type Surface = 'management' | 'display'

function getSurface(): Surface {
  const path = window.location.pathname.replace(/\/+$/, '')
  return path === '/display' ? 'display' : 'management'
}

function useClock() {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  return now
}

function LanguageSwitcher() {
  const { locale, setLocale, t } = useLocale()
  const options: Array<{ value: Locale; label: string }> = [
    { value: 'en', label: t('languageEnglish') },
    { value: 'de', label: t('languageGerman') },
  ]

  return (
    <div className="segmented-control" aria-label={t('languageLabel')}>
      {options.map((option) => (
        <button
          className="segmented-control__button"
          type="button"
          key={option.value}
          aria-pressed={locale === option.value}
          onClick={() => setLocale(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

function Brand() {
  return (
    <a className="brand" href="/" aria-label="Scorebound">
      <span className="brand__mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>Scorebound</span>
    </a>
  )
}

function ManagementSurface() {
  const { formatDateTime, formatNumber, timeZone, t } = useLocale()
  const now = useClock()

  return (
    <main className="management-shell">
      <header className="topbar">
        <Brand />
        <LanguageSwitcher />
      </header>

      <section className="intro" aria-labelledby="intro-title">
        <p className="eyebrow">{t('managementEyebrow')}</p>
        <h1 id="intro-title">{t('managementTitle')}</h1>
        <p className="intro__copy">{t('managementDescription')}</p>
      </section>

      <section className="settings-grid" aria-label={t('settingsLabel')}>
        <article className="settings-card settings-card--accent">
          <span className="settings-card__index" aria-hidden="true">01</span>
          <h2>{t('languageTitle')}</h2>
          <p>{t('languageDescription')}</p>
          <LanguageSwitcher />
        </article>

        <article className="settings-card">
          <span className="settings-card__index" aria-hidden="true">02</span>
          <h2>{t('managementThemeTitle')}</h2>
          <p>{t('managementThemeDescription')}</p>
          <div className="theme-sample" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        </article>

        <article className="settings-card">
          <span className="settings-card__index" aria-hidden="true">03</span>
          <h2>{t('formattingTitle')}</h2>
          <p>{t('formattingDescription')}</p>
          <dl className="format-sample">
            <div>
              <dt>{t('numberLabel')}</dt>
              <dd>{formatNumber(1250)}</dd>
            </div>
            <div>
              <dt>{t('timeLabel')}</dt>
              <dd>{formatDateTime(now)}</dd>
            </div>
            <div>
              <dt>{t('timezoneLabel')}</dt>
              <dd>{timeZone}</dd>
            </div>
          </dl>
        </article>
      </section>

      <a className="display-link" href="/display">
        <span>
          <strong>{t('displayLinkTitle')}</strong>
          <small>{t('displayLinkDescription')}</small>
        </span>
        <span className="display-link__arrow" aria-hidden="true">↗</span>
      </a>
    </main>
  )
}

function DisplaySurface() {
  const { formatDateTime, t } = useLocale()
  const now = useClock()

  return (
    <main className="display-shell">
      <header className="display-header">
        <Brand />
        <div className="display-header__actions">
          <LanguageSwitcher />
          <a className="text-link" href="/">{t('backToManagement')}</a>
        </div>
      </header>

      <section className="display-stage" aria-labelledby="display-title">
        <div className="display-stage__glow" aria-hidden="true" />
        <p className="eyebrow">{t('displayEyebrow')}</p>
        <h1 id="display-title">{t('displayTitle')}</h1>
        <p>{t('displayDescription')}</p>
        <div className="display-status" role="status">
          <span className="display-status__dot" aria-hidden="true" />
          {t('displayWaiting')}
        </div>
      </section>

      <footer className="display-footer">
        <span>{t('displayThemeLabel')}</span>
        <time dateTime={now.toISOString()}>{formatDateTime(now)}</time>
      </footer>
    </main>
  )
}

function ScoreboundApp() {
  const surface = getSurface()

  useEffect(() => {
    document.documentElement.dataset.surface = surface
    return () => {
      delete document.documentElement.dataset.surface
    }
  }, [surface])

  return surface === 'display' ? <DisplaySurface /> : <ManagementSurface />
}

function App() {
  return (
    <LocaleProvider>
      <ScoreboundApp />
    </LocaleProvider>
  )
}

export default App
