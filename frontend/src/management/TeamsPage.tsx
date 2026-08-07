import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { api, type Team } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'

export function TeamsPage({ csrf }: { csrf: string | null }) {
  const { locale } = useLocale()
  const text = uiText(locale)
  const [teams, setTeams] = useState<Team[]>([])
  const [name, setName] = useState('')
  const [shortName, setShortName] = useState('')
  const [color, setColor] = useState('#7357FF')
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Team | null>(null)
  const [editName, setEditName] = useState(''); const [editShortName, setEditShortName] = useState(''); const [editColor, setEditColor] = useState('#7357FF')

  const refresh = useCallback(() => api.teams(true).then(setTeams).catch(() => setError(text.error)), [text.error])
  useEffect(() => { void refresh() }, [refresh])

  async function create(event: FormEvent) {
    event.preventDefault()
    try {
      await api.createTeam({ name, shortName, color }, csrf)
      setName(''); setShortName(''); await refresh()
    } catch { setError(text.error) }
  }

  async function toggle(team: Team) {
    try { await api.updateTeam(team.id, { active: !team.active }, csrf); await refresh() }
    catch { setError(text.error) }
  }
  function beginEdit(team: Team) { setEditing(team); setEditName(team.name); setEditShortName(team.shortName); setEditColor(team.color) }
  async function saveEdit(event: FormEvent) { event.preventDefault(); if (!editing) return; try { await api.updateTeam(editing.id, { name: editName, shortName: editShortName, color: editColor }, csrf); setEditing(null); await refresh() } catch { setError(text.error) } }

  return <div className="page-stack">
    <header className="page-header"><div><p className="eyebrow">{text.admin}</p><h1>{text.teams}</h1><p>{text.manageHint}</p></div></header>
    <Feedback error={error} />
    <section className="split-layout">
      <form className="panel form-panel" onSubmit={create}>
        <div className="panel__header"><h2>{text.create}</h2></div>
        <label className="field"><span>{text.name}</span><input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
        <label className="field"><span>{text.shortName}</span><input required maxLength={20} value={shortName} onChange={(e) => setShortName(e.target.value)} /></label>
        <label className="field"><span>{text.color}</span><span className="color-field"><input type="color" value={color} onChange={(e) => setColor(e.target.value.toUpperCase())} />
          <input required pattern="#[0-9A-Fa-f]{6}" value={color} onChange={(e) => setColor(e.target.value)} /></span></label>
        <button className="button button--primary" type="submit">{text.create}</button>
      </form>
      <section className="panel"><div className="panel__header"><h2>{text.teams}</h2><span className="count-badge">{teams.length}</span></div>
        <div className="resource-list">{teams.map((team) => <article className="resource-row" key={team.id}>
          <span className="team-avatar" style={{ background: team.color }}>{team.shortName.slice(0, 3)}</span>
          <div><strong>{team.name}</strong><small>{team.shortName} · {team.active ? text.active : text.inactive}</small></div>
          <div className="button-row"><button className="button button--ghost" type="button" onClick={() => beginEdit(team)}>{text.save}</button><button className="button button--ghost" type="button" onClick={() => toggle(team)}>{team.active ? text.inactive : text.activate}</button></div>
        </article>)}</div>
      </section>
    </section>{editing && <div className="modal-backdrop"><form className="modal" role="dialog" aria-modal="true" aria-labelledby="team-edit-title" onSubmit={saveEdit}><h2 id="team-edit-title">{editing.name}</h2>
      <label className="field"><span>{text.name}</span><input autoFocus required maxLength={100} value={editName} onChange={(e) => setEditName(e.target.value)} /></label>
      <label className="field"><span>{text.shortName}</span><input required maxLength={20} value={editShortName} onChange={(e) => setEditShortName(e.target.value)} /></label>
      <label className="field"><span>{text.color}</span><input required pattern="#[0-9A-Fa-f]{6}" value={editColor} onChange={(e) => setEditColor(e.target.value)} /></label>
      <div className="button-row"><button className="button button--ghost" type="button" onClick={() => setEditing(null)}>{text.cancel}</button><button className="button button--primary" type="submit">{text.save}</button></div>
    </form></div>}
  </div>
}
