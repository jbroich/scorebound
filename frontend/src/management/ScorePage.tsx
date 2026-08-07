import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { api, ApiError, type Member, type ScoreboardSummary, type Standings, type TransactionPage } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'

type Props = { csrf: string | null; canScore: boolean; accountId: string; isAdmin: boolean }

export function ScorePage({ csrf, canScore, accountId, isAdmin }: Props) {
  const { locale, formatDateTime, formatNumber } = useLocale()
  const text = uiText(locale)
  const [scoreboards, setScoreboards] = useState<ScoreboardSummary[]>([])
  const [scoreboardId, setScoreboardId] = useState('')
  const [standings, setStandings] = useState<Standings | null>(null)
  const [transactions, setTransactions] = useState<TransactionPage | null>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [targetType, setTargetType] = useState<'team' | 'member'>('team')
  const [targetId, setTargetId] = useState('')
  const [kind, setKind] = useState<'Credit' | 'Debit'>('Credit')
  const [amount, setAmount] = useState(25)
  const [reason, setReason] = useState('')
  const [cancelId, setCancelId] = useState<string | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([api.scoreboards(), api.members()]).then(([boards, memberPage]) => {
      setScoreboards(boards)
      setMembers(memberPage.content)
      setScoreboardId((current) => current || boards[0]?.id || '')
    }).catch(() => setError(text.error))
  }, [text.error])

  const refresh = useCallback(async (boardId: string) => {
    if (!boardId) return
    try {
      const [nextStandings, nextTransactions] = await Promise.all([
        api.standings(boardId), api.transactions(boardId),
      ])
      setStandings(nextStandings)
      setTransactions(nextTransactions)
      setTargetId((current) => current || nextStandings.standings[0]?.teamId || '')
      setError(null)
    } catch (caught) {
      setStandings(null)
      setTransactions(null)
      setError(caught instanceof ApiError && caught.code === 'resource_not_found' ? text.noActivePeriod : text.error)
    }
  }, [text.error, text.noActivePeriod])

  useEffect(() => { void refresh(scoreboardId) }, [refresh, scoreboardId])

  const eligibleMembers = useMemo(() => {
    const teams = new Set(standings?.standings.map((standing) => standing.teamId) ?? [])
    return members.filter((member) => member.active && member.activeTeamId && teams.has(member.activeTeamId))
  }, [members, standings])

  const targetOptions = useMemo(() => targetType === 'team'
    ? (standings?.standings ?? []).map((item) => ({ id: item.teamId, label: item.teamName }))
    : eligibleMembers.map((item) => ({ id: item.id, label: item.displayName })),
  [eligibleMembers, standings, targetType])

  useEffect(() => {
    const options = targetOptions.map((item) => item.id)
    if (!options.includes(targetId)) setTargetId(options[0] ?? '')
  }, [targetId, targetOptions])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!scoreboardId || !targetId) return
    setBusy(true)
    setError(null)
    try {
      await api.score(scoreboardId, {
        ...(targetType === 'team' ? { teamId: targetId } : { memberId: targetId }),
        kind, amount, reason,
      }, csrf)
      setReason('')
      await refresh(scoreboardId)
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'score_would_be_negative'
        ? (locale === 'de' ? 'Der Punktestand darf nicht unter 0 fallen.' : 'The score cannot fall below 0.')
        : text.error)
    } finally {
      setBusy(false)
    }
  }

  async function cancelTransaction(event: FormEvent) {
    event.preventDefault()
    if (!cancelId) return
    setBusy(true)
    try {
      await api.cancel(scoreboardId, cancelId, cancelReason, csrf)
      setCancelId(null)
      setCancelReason('')
      await refresh(scoreboardId)
    } catch {
      setError(text.error)
    } finally {
      setBusy(false)
    }
  }

  return <div className="page-stack">
    <header className="page-header">
      <div><p className="eyebrow">Live desk</p><h1>{text.score}</h1><p>{text.scoreHint}</p></div>
      <label className="field field--compact"><span>{text.scoreboards}</span>
        <select value={scoreboardId} onChange={(event) => setScoreboardId(event.target.value)}>
          {scoreboards.map((board) => <option key={board.id} value={board.id}>{board.name}</option>)}
        </select></label>
    </header>
    <Feedback error={error} />
    <section className="score-layout">
      <div className="panel standings-panel">
        <div className="panel__header"><div><p className="eyebrow">{text.period}</p><h2>{standings?.periodName ?? '—'}</h2></div>
          {standings && <span className={`status status--${standings.status.toLowerCase()}`}>{standings.status}</span>}</div>
        <div className="standings-list">
          {standings?.standings.map((standing) => <article className="standing" key={standing.teamId}>
            <span className="standing__rank">{standing.rank}</span><span className="team-dot" style={{ background: standing.color }} />
            <div><strong>{standing.teamName}</strong><small>{standing.shortName}</small></div>
            <b>{formatNumber(standing.score)}</b>
          </article>)}
          {!standings?.standings.length && <p className="empty-state">{text.empty}</p>}
        </div>
      </div>
      {canScore && <form className="panel score-form" onSubmit={submit}>
        <div className="panel__header"><div><p className="eyebrow">{text.target}</p><h2>{text.submitScore}</h2></div></div>
        <div className="segmented-control segmented-control--wide">
          {(['team', 'member'] as const).map((type) => <button key={type} type="button" aria-pressed={targetType === type}
            className="segmented-control__button" onClick={() => setTargetType(type)}>{type === 'team' ? text.teamTarget : text.memberTarget}</button>)}
        </div>
        <label className="field"><span>{text.target}</span><select required value={targetId} onChange={(event) => setTargetId(event.target.value)}>
          {targetOptions.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>
        <div className="segmented-control segmented-control--wide">
          {(['Credit', 'Debit'] as const).map((value) => <button key={value} type="button" aria-pressed={kind === value}
            className="segmented-control__button" onClick={() => setKind(value)}>{value === 'Credit' ? `+ ${text.credit}` : `− ${text.debit}`}</button>)}
        </div>
        <fieldset className="quick-values"><legend>{text.quickValues}</legend>
          {[10, 25, 50, 100].map((value) => <button type="button" className={amount === value ? 'quick-value quick-value--active' : 'quick-value'}
            key={value} onClick={() => setAmount(value)}>{value}</button>)}
        </fieldset>
        <label className="field"><span>{text.customAmount}</span><input type="number" min="1" max="1000" required value={amount}
          onChange={(event) => setAmount(Number(event.target.value))} /></label>
        <label className="field"><span>{text.reason}</span><textarea required maxLength={500} value={reason}
          onChange={(event) => setReason(event.target.value)} /></label>
        <button className={`button button--large ${kind === 'Credit' ? 'button--credit' : 'button--debit'}`} disabled={busy} type="submit">
          {kind === 'Credit' ? '+' : '−'} {amount} · {text.submitScore}
        </button>
      </form>}
    </section>
    <section className="panel">
      <div className="panel__header"><div><p className="eyebrow">Ledger</p><h2>{text.transactions}</h2></div></div>
      <div className="transaction-list">
        {transactions?.content.map((transaction) => <article className={transaction.cancellation ? 'transaction transaction--cancelled' : 'transaction'} key={transaction.id}>
          <span className={`transaction__amount transaction__amount--${transaction.kind.toLowerCase()}`}>
            {transaction.kind === 'Credit' ? '+' : '−'}{transaction.amount}</span>
          <div><strong>{transaction.memberName ?? transaction.teamName}</strong><p>{transaction.reason}</p>
            <small>{formatDateTime(new Date(transaction.createdAt))} · {transaction.createdByUsername}</small>
            {transaction.cancellation && <small className="cancel-note">{text.cancel}: {transaction.cancellation.reason}</small>}</div>
          {!transaction.cancellation && canScore && (isAdmin || transaction.createdBy === accountId) &&
            <button className="button button--ghost" type="button" onClick={() => setCancelId(transaction.id)}>{text.cancel}</button>}
        </article>)}
        {!transactions?.content.length && <p className="empty-state">{text.empty}</p>}
      </div>
    </section>
    {cancelId && <div className="modal-backdrop"><form className="modal" role="dialog" aria-modal="true" aria-labelledby="transaction-cancel-title" onSubmit={cancelTransaction}>
      <h2 id="transaction-cancel-title">{text.cancel}</h2><label className="field"><span>{text.cancellationReason}</span><textarea autoFocus required maxLength={500}
        value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} /></label>
      <div className="button-row"><button className="button button--ghost" type="button" onClick={() => setCancelId(null)}>{text.back}</button>
        <button className="button button--danger" disabled={busy} type="submit">{text.cancel}</button></div>
    </form></div>}
  </div>
}
