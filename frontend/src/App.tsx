import { useEffect, useMemo, useState } from 'react'
import './App.css'
import { AuthProvider } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { PasswordChangePage } from './auth/PasswordChangePage'
import { useAuth } from './auth/useAuth'
import { LocaleProvider } from './i18n/LocaleProvider'
import { useLocale } from './i18n/useLocale'
import { AccountsPage } from './management/AccountsPage'
import { DashboardPage, type View } from './management/DashboardPage'
import { MembersPage } from './management/MembersPage'
import { ScoreboardsPage } from './management/ScoreboardsPage'
import { ScorePage } from './management/ScorePage'
import { TeamsPage } from './management/TeamsPage'
import { Brand } from './ui/Brand'
import { LanguageSwitcher } from './ui/LanguageSwitcher'
import { uiText } from './ui/text'

function LoadingScreen() {
  const { locale } = useLocale()
  return <main className="loading-screen"><Brand /><span className="loading-pulse" />{uiText(locale).loading}</main>
}

function ManagementApp() {
  const { session, logout } = useAuth()
  const { locale } = useLocale(); const text = uiText(locale)
  const isAdmin = session?.effectiveRoles.includes('Admin') ?? false
  const canScore = isAdmin || (session?.effectiveRoles.includes('Scorer') ?? false)
  const [view, setView] = useState<View>(canScore ? 'score' : 'dashboard')
  const navigation = useMemo(() => [
    { id: 'dashboard' as const, label: text.dashboard, icon: '⌂', visible: true },
    { id: 'score' as const, label: text.score, icon: '＋', visible: canScore },
    { id: 'teams' as const, label: text.teams, icon: '◉', visible: isAdmin },
    { id: 'members' as const, label: text.members, icon: '♙', visible: isAdmin },
    { id: 'scoreboards' as const, label: text.scoreboards, icon: '▤', visible: isAdmin },
    { id: 'accounts' as const, label: text.accounts, icon: '⚙', visible: isAdmin },
  ].filter((item) => item.visible), [canScore, isAdmin, text])

  return <main className="app-shell">
    <aside className="sidebar"><div className="sidebar__brand"><Brand /><p>{text.appSubtitle}</p></div>
      <nav aria-label={text.navigation}>{navigation.map((item) => <button key={item.id} type="button" className={view === item.id ? 'nav-item nav-item--active' : 'nav-item'} onClick={() => setView(item.id)}>
        <span aria-hidden="true">{item.icon}</span>{item.label}</button>)}</nav>
      <div className="sidebar__footer"><LanguageSwitcher compact /><div className="account-chip"><span>{session?.username.slice(0, 2).toUpperCase()}</span><div><strong>{session?.username}</strong><small>{session?.effectiveRoles.join(' · ')}</small></div></div>
        <button className="button button--ghost button--full" type="button" onClick={logout}>{text.logout}</button></div>
    </aside>
    <section className="workspace">
      {view === 'dashboard' && <DashboardPage isAdmin={isAdmin} canScore={canScore} navigate={setView} />}
      {view === 'score' && <ScorePage csrf={session?.csrfToken ?? null} canScore={canScore} isAdmin={isAdmin} accountId={session?.accountId ?? ''} />}
      {view === 'teams' && isAdmin && <TeamsPage csrf={session?.csrfToken ?? null} />}
      {view === 'members' && isAdmin && <MembersPage csrf={session?.csrfToken ?? null} />}
      {view === 'scoreboards' && isAdmin && <ScoreboardsPage csrf={session?.csrfToken ?? null} />}
      {view === 'accounts' && isAdmin && <AccountsPage csrf={session?.csrfToken ?? null} currentAccountId={session?.accountId ?? ''} />}
    </section>
    <nav className="mobile-nav" aria-label={text.navigation}>{navigation.map((item) => <button key={item.id} type="button" aria-current={view === item.id ? 'page' : undefined} onClick={() => setView(item.id)}><span aria-hidden="true">{item.icon}</span><small>{item.label}</small></button>)}</nav>
  </main>
}

function DisplaySurface() {
  const { logout } = useAuth(); const { locale, formatDateTime } = useLocale(); const text = uiText(locale)
  const [now, setNow] = useState(new Date())
  useEffect(() => { const timer = window.setInterval(() => setNow(new Date()), 30_000); return () => window.clearInterval(timer) }, [])
  return <main className="display-shell"><header className="display-header"><Brand /><div className="display-header__actions"><LanguageSwitcher compact /><a className="text-link" href="/">{text.back}</a><button className="button button--ghost" onClick={logout}>{text.logout}</button></div></header>
    <section className="display-stage"><div className="display-stage__glow" aria-hidden="true" /><p className="eyebrow">{text.display}</p><h1>{locale === 'de' ? 'Die Arena macht sich bereit.' : 'The arena is getting ready.'}</h1>
      <p>{locale === 'de' ? 'Die animierte Punkteanzeige folgt als eigenes Ticket.' : 'The animated scoreboard follows in its dedicated issue.'}</p><div className="display-status" role="status"><span className="display-status__dot" />API connected</div></section>
    <footer className="display-footer"><span>Scorebound display</span><time>{formatDateTime(now)}</time></footer></main>
}

function AuthenticatedApp() {
  const { session, loading } = useAuth()
  const display = window.location.pathname.replace(/\/+$/, '') === '/display'
  useEffect(() => { document.documentElement.dataset.surface = display ? 'display' : 'management'; return () => { delete document.documentElement.dataset.surface } }, [display])
  if (loading) return <LoadingScreen />
  if (!session) return <LoginPage displayMode={display} />
  if (session.mustChangePassword) return <PasswordChangePage />
  return display ? <DisplaySurface /> : <ManagementApp />
}

export default function App() {
  return <LocaleProvider><AuthProvider><AuthenticatedApp /></AuthProvider></LocaleProvider>
}
