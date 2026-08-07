import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { api, type PeriodSummary, type Scoreboard, type ScoreboardSummary, type Team } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'

function localInput(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export function ScoreboardsPage({ csrf }: { csrf: string | null }) {
  const { locale, formatDateTime } = useLocale(); const text = uiText(locale)
  const [boards, setBoards] = useState<ScoreboardSummary[]>([]); const [teams, setTeams] = useState<Team[]>([])
  const [selectedId, setSelectedId] = useState(''); const [selected, setSelected] = useState<Scoreboard | null>(null)
  const [periods, setPeriods] = useState<PeriodSummary[]>([]); const [name, setName] = useState(''); const [description, setDescription] = useState('')
  const [periodName, setPeriodName] = useState(''); const [startsAt, setStartsAt] = useState(localInput(new Date()))
  const [endsAt, setEndsAt] = useState(localInput(new Date(Date.now() + 90 * 24 * 60 * 60 * 1000)))
  const [editName, setEditName] = useState(''); const [editDescription, setEditDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const refreshList = useCallback(async () => {
    try { const [nextBoards, nextTeams] = await Promise.all([api.scoreboards(true), api.teams(true)]); setBoards(nextBoards); setTeams(nextTeams); setSelectedId((id) => id || nextBoards[0]?.id || '') }
    catch { setError(text.error) }
  }, [text.error])
  const refreshDetails = useCallback(async (id: string) => {
    if (!id) { setSelected(null); setPeriods([]); return }
    try { const [board, boardPeriods] = await Promise.all([api.scoreboard(id), api.periods(id)]); setSelected(board); setPeriods(boardPeriods); setError(null) }
    catch { setError(text.error) }
  }, [text.error])
  useEffect(() => { void refreshList() }, [refreshList])
  useEffect(() => { void refreshDetails(selectedId) }, [refreshDetails, selectedId])
  useEffect(() => { setEditName(selected?.name ?? ''); setEditDescription(selected?.description ?? '') }, [selected])

  async function createBoard(event: FormEvent) { event.preventDefault(); try { const board = await api.createScoreboard({ name, description }, csrf); setName(''); setDescription(''); await refreshList(); setSelectedId(board.id) } catch { setError(text.error) } }
  async function selectTeam(teamId: string, checked: boolean) { if (!selectedId) return; try { await api.selectTeam(selectedId, teamId, checked, csrf); await refreshDetails(selectedId) } catch { setError(text.error) } }
  async function createPeriod(event: FormEvent) { event.preventDefault(); try { await api.createPeriod(selectedId, { name: periodName, startsAt: new Date(startsAt).toISOString(), endsAt: new Date(endsAt).toISOString() }, csrf); setPeriodName(''); await refreshDetails(selectedId) } catch { setError(text.error) } }
  async function transition(period: PeriodSummary, action: 'activate' | 'close' | 'reopen') { try { await api.transitionPeriod(selectedId, period.id, action, csrf); await refreshDetails(selectedId) } catch { setError(text.error) } }
  async function toggleBoard() { if (!selected) return; try { await api.updateScoreboard(selected.id, { active: !selected.active }, csrf); await refreshList(); await refreshDetails(selected.id) } catch { setError(text.error) } }
  async function saveBoard() { if (!selected) return; try { await api.updateScoreboard(selected.id, { name: editName, description: editDescription }, csrf); await refreshList(); await refreshDetails(selected.id) } catch { setError(text.error) } }

  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">{text.admin}</p><h1>{text.scoreboards}</h1><p>{text.scoreboardHint}</p></div>
    <label className="field field--compact"><span>{text.scoreboards}</span><select value={selectedId} onChange={(e) => setSelectedId(e.target.value)}>{boards.map((board) => <option key={board.id} value={board.id}>{board.name}{board.active ? '' : ` · ${text.inactive}`}</option>)}</select></label></header>
    <Feedback error={error} /><section className="split-layout"><form className="panel form-panel" onSubmit={createBoard}><div className="panel__header"><h2>{text.create}</h2></div>
      <label className="field"><span>{text.name}</span><input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label className="field"><span>{text.description}</span><textarea maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
      <button className="button button--primary" type="submit">{text.create}</button></form>
      <section className="panel"><div className="panel__header"><div><h2>{selected?.name ?? text.select}</h2><p>{selected?.description}</p></div>{selected && <button type="button" className="button button--ghost" onClick={toggleBoard}>{selected.active ? text.inactive : text.activate}</button>}</div>
        {selected && <><div className="field-row"><label className="field"><span>{text.name}</span><input maxLength={100} value={editName} onChange={(e) => setEditName(e.target.value)} /></label><label className="field"><span>{text.description}</span><input maxLength={500} value={editDescription} onChange={(e) => setEditDescription(e.target.value)} /></label></div><div className="button-row board-save"><button className="button button--ghost" type="button" onClick={saveBoard}>{text.save}</button></div>
          <h3 className="section-label">{text.selectedTeams}</h3><div className="check-grid">{teams.map((team) => <label className="check-card" key={team.id}>
          <input type="checkbox" checked={selected.selectedTeams.some((item) => item.id === team.id)} disabled={!team.active}
            onChange={(e) => selectTeam(team.id, e.target.checked)} /><span className="team-dot" style={{ background: team.color }} /><span>{team.name}</span></label>)}</div></>}
      </section></section>
    {selected && <section className="split-layout split-layout--reverse"><form className="panel form-panel" onSubmit={createPeriod}><div className="panel__header"><h2>{text.period}</h2></div>
      <label className="field"><span>{text.name}</span><input required maxLength={100} value={periodName} onChange={(e) => setPeriodName(e.target.value)} /></label>
      <label className="field"><span>{text.start}</span><input required type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
      <label className="field"><span>{text.end}</span><input required type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} /></label>
      <button className="button button--primary" type="submit">{text.create}</button></form>
      <section className="panel"><div className="panel__header"><h2>{text.periods}</h2><span className="count-badge">{periods.length}</span></div><div className="resource-list">{periods.map((period) => <article className="resource-row resource-row--period" key={period.id}>
        <div><strong>{period.name}</strong><small>{formatDateTime(new Date(period.startsAt))} — {formatDateTime(new Date(period.endsAt))}</small></div><span className={`status status--${period.status.toLowerCase()}`}>{period.status}</span>
        {period.status === 'Scheduled' && <button className="button button--ghost" type="button" onClick={() => transition(period, 'activate')}>{text.activate}</button>}
        {period.status === 'Active' && <button className="button button--ghost" type="button" onClick={() => transition(period, 'close')}>{text.close}</button>}
        {period.status === 'Closed' && <button className="button button--ghost" type="button" onClick={() => transition(period, 'reopen')}>{text.reopen}</button>}
      </article>)}</div></section></section>}
  </div>
}
