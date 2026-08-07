import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { uiText } from '../ui/text'

export type View = 'dashboard' | 'score' | 'teams' | 'members' | 'scoreboards' | 'accounts'

export function DashboardPage({ isAdmin, canScore, navigate }: { isAdmin: boolean; canScore: boolean; navigate: (view: View) => void }) {
  const { locale } = useLocale(); const text = uiText(locale)
  const [counts, setCounts] = useState({ teams: 0, members: 0, scoreboards: 0 })
  useEffect(() => { Promise.all([api.teams(), api.members(), api.scoreboards()]).then(([teams, members, boards]) => setCounts({ teams: teams.length, members: members.totalElements, scoreboards: boards.length })).catch(() => undefined) }, [])
  const cards: Array<{ view: View; value: number | string; title: string; copy: string; visible: boolean }> = [
    { view: 'score', value: '10 · 25 · 50 · 100', title: text.score, copy: text.scoreHint, visible: canScore },
    { view: 'teams', value: counts.teams, title: text.teams, copy: text.manageHint, visible: isAdmin },
    { view: 'members', value: counts.members, title: text.members, copy: text.manageHint, visible: isAdmin },
    { view: 'scoreboards', value: counts.scoreboards, title: text.scoreboards, copy: text.scoreboardHint, visible: isAdmin },
  ]
  return <div className="page-stack"><header className="page-header page-header--hero"><div><p className="eyebrow">Scorebound office league</p><h1>{text.dashboard}</h1><p>{text.overviewCopy}</p></div></header>
    <section className="dashboard-grid">{cards.filter((card) => card.visible).map((card, index) => <button className={`dashboard-card dashboard-card--${index + 1}`} type="button" key={card.view} onClick={() => navigate(card.view)}>
      <span className="dashboard-card__value">{card.value}</span><span><strong>{card.title}</strong><small>{card.copy}</small></span><b aria-hidden="true">↗</b>
    </button>)}</section>
    {!cards.some((card) => card.visible) && <section className="panel empty-state">{locale === 'de' ? 'Dein Account kann freigegebene Scoreboards ansehen.' : 'Your account can view authorized scoreboards.'}</section>}
  </div>
}
