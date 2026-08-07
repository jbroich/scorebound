import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { api, type Member, type Team } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'

export function MembersPage({ csrf }: { csrf: string | null }) {
  const { locale } = useLocale(); const text = uiText(locale)
  const [members, setMembers] = useState<Member[]>([]); const [teams, setTeams] = useState<Team[]>([])
  const [displayName, setDisplayName] = useState(''); const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState(''); const [teamId, setTeamId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Member | null>(null); const [editDisplayName, setEditDisplayName] = useState(''); const [editFirstName, setEditFirstName] = useState(''); const [editLastName, setEditLastName] = useState('')
  const refresh = useCallback(async () => {
    try { const [page, nextTeams] = await Promise.all([api.members(true), api.teams()]); setMembers(page.content); setTeams(nextTeams); setTeamId((id) => id || nextTeams[0]?.id || '') }
    catch { setError(text.error) }
  }, [text.error])
  useEffect(() => { void refresh() }, [refresh])
  async function create(event: FormEvent) {
    event.preventDefault(); try { await api.createMember({ displayName, firstName, lastName, teamId }, csrf); setDisplayName(''); setFirstName(''); setLastName(''); await refresh() }
    catch { setError(text.error) }
  }
  async function toggle(member: Member) { try { await api.updateMember(member.id, { active: !member.active }, csrf); await refresh() } catch { setError(text.error) } }
  async function move(member: Member, nextTeamId: string) { if (!nextTeamId || nextTeamId === member.activeTeamId) return; try { await api.changeMemberTeam(member.id, nextTeamId, csrf); await refresh() } catch { setError(text.error) } }
  function beginEdit(member: Member) { setEditing(member); setEditDisplayName(member.displayName); setEditFirstName(member.firstName ?? ''); setEditLastName(member.lastName ?? '') }
  async function saveEdit(event: FormEvent) { event.preventDefault(); if (!editing) return; try { await api.updateMember(editing.id, { displayName: editDisplayName, firstName: editFirstName, lastName: editLastName }, csrf); setEditing(null); await refresh() } catch { setError(text.error) } }

  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">{text.admin}</p><h1>{text.members}</h1><p>{text.manageHint}</p></div></header>
    <Feedback error={error} /><section className="split-layout"><form className="panel form-panel" onSubmit={create}>
      <div className="panel__header"><h2>{text.create}</h2></div>
      <label className="field"><span>{text.name}</span><input required maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} /></label>
      <div className="field-row"><label className="field"><span>{text.firstName}</span><input maxLength={100} value={firstName} onChange={(e) => setFirstName(e.target.value)} /></label>
        <label className="field"><span>{text.lastName}</span><input maxLength={100} value={lastName} onChange={(e) => setLastName(e.target.value)} /></label></div>
      <label className="field"><span>{text.team}</span><select required value={teamId} onChange={(e) => setTeamId(e.target.value)}>{teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></label>
      <button className="button button--primary" type="submit">{text.create}</button>
    </form><section className="panel"><div className="panel__header"><h2>{text.members}</h2><span className="count-badge">{members.length}</span></div>
      <div className="resource-list">{members.map((member) => <article className="resource-row resource-row--member" key={member.id}>
        <span className="person-avatar">{member.displayName.slice(0, 2).toUpperCase()}</span><div><strong>{member.displayName}</strong><small>{member.active ? text.active : text.inactive}</small></div>
        <label className="inline-select"><span className="sr-only">{text.changeTeam}</span><select aria-label={`${text.changeTeam}: ${member.displayName}`} value={member.activeTeamId ?? ''} onChange={(e) => move(member, e.target.value)}>
          {teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></label>
        <div className="button-row"><button className="button button--ghost" type="button" onClick={() => beginEdit(member)}>{text.save}</button><button className="button button--ghost" type="button" onClick={() => toggle(member)}>{member.active ? text.inactive : text.activate}</button></div>
      </article>)}</div></section></section>
      {editing && <div className="modal-backdrop"><form className="modal" role="dialog" aria-modal="true" aria-labelledby="member-edit-title" onSubmit={saveEdit}><h2 id="member-edit-title">{editing.displayName}</h2>
        <label className="field"><span>{text.name}</span><input autoFocus required maxLength={100} value={editDisplayName} onChange={(e) => setEditDisplayName(e.target.value)} /></label>
        <div className="field-row"><label className="field"><span>{text.firstName}</span><input maxLength={100} value={editFirstName} onChange={(e) => setEditFirstName(e.target.value)} /></label><label className="field"><span>{text.lastName}</span><input maxLength={100} value={editLastName} onChange={(e) => setEditLastName(e.target.value)} /></label></div>
        <div className="button-row"><button className="button button--ghost" type="button" onClick={() => setEditing(null)}>{text.cancel}</button><button className="button button--primary" type="submit">{text.save}</button></div>
      </form></div>}</div>
}
