import { type FormEvent, useState } from 'react'
import { ApiError } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Brand } from '../ui/Brand'
import { Feedback } from '../ui/Feedback'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { uiText } from '../ui/text'
import { useAuth } from './useAuth'

export function LoginPage({ displayMode = false }: { displayMode?: boolean }) {
  const { login } = useAuth()
  const { locale } = useLocale()
  const text = uiText(locale)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password, displayMode ? 'Display' : 'Normal')
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'invalid_credentials'
        ? (locale === 'de' ? 'Benutzername oder Passwort stimmt nicht.' : 'Username or password is incorrect.')
        : text.error)
    } finally {
      setBusy(false)
    }
  }

  return <main className="auth-shell">
    <section className="auth-story" aria-hidden="true">
      <div className="auth-story__orb auth-story__orb--one" />
      <div className="auth-story__orb auth-story__orb--two" />
      <p>10 · 25 · 50 · 100</p>
      <h1>{text.appSubtitle}</h1>
    </section>
    <section className="auth-panel">
      <header className="auth-panel__header"><Brand /><LanguageSwitcher compact /></header>
      <form className="auth-form" onSubmit={submit}>
        <p className="eyebrow">{displayMode ? text.display : text.welcome}</p>
        <h2>{text.login}</h2>
        <p>{text.loginCopy}</p>
        <label><span>{text.username}</span><input autoFocus autoComplete="username" required
          value={username} onChange={(event) => setUsername(event.target.value)} /></label>
        <label><span>{text.password}</span><input type="password" autoComplete="current-password" required
          value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <Feedback error={error} />
        <button className="button button--primary button--large" disabled={busy} type="submit">
          {busy ? text.loading : text.login}
        </button>
      </form>
    </section>
  </main>
}
