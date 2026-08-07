import { type FormEvent, useState } from 'react'
import { useLocale } from '../i18n/useLocale'
import { Brand } from '../ui/Brand'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'
import { useAuth } from './useAuth'

export function PasswordChangePage() {
  const { changePassword, logout } = useAuth()
  const { locale } = useLocale()
  const text = uiText(locale)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== confirmation) {
      setError(locale === 'de' ? 'Die neuen Passwörter stimmen nicht überein.' : 'The new passwords do not match.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await changePassword(currentPassword, newPassword)
    } catch {
      setError(text.error)
    } finally {
      setBusy(false)
    }
  }

  return <main className="password-shell">
    <header><Brand /><button className="button button--ghost" type="button" onClick={logout}>{text.logout}</button></header>
    <form className="auth-form password-card" onSubmit={submit}>
      <p className="eyebrow">Scorebound security</p>
      <h1>{text.forcedPassword}</h1>
      <p>{text.forcedPasswordCopy}</p>
      <label><span>{text.currentPassword}</span><input autoFocus type="password" required
        autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
      <label><span>{text.newPassword}</span><input type="password" required minLength={12}
        autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
      <label><span>{locale === 'de' ? 'Neues Passwort wiederholen' : 'Repeat new password'}</span>
        <input type="password" required minLength={12} autoComplete="new-password"
          value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /></label>
      <Feedback error={error} />
      <button className="button button--primary button--large" disabled={busy} type="submit">{text.save}</button>
    </form>
  </main>
}
