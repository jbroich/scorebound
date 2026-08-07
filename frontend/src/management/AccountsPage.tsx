import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { api, type Account, type Member, type Role, type ScoreboardSummary } from '../api/client'
import { useLocale } from '../i18n/useLocale'
import { Feedback } from '../ui/Feedback'
import { uiText } from '../ui/text'

const ALL_ROLES: Role[] = ['Admin', 'Scorer', 'Member', 'Display']

export function AccountsPage({ csrf, currentAccountId }: { csrf: string | null; currentAccountId: string }) {
  const { locale } = useLocale(); const text = uiText(locale)
  const [accounts, setAccounts] = useState<Account[]>([]); const [boards, setBoards] = useState<ScoreboardSummary[]>([]); const [members, setMembers] = useState<Member[]>([])
  const [username, setUsername] = useState(''); const [roles, setRoles] = useState<Role[]>(['Member']); const [preferredLocale, setPreferredLocale] = useState<'en' | 'de'>('en')
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null); const [error, setError] = useState<string | null>(null)
  const refresh = useCallback(async () => { try { const [nextAccounts, nextBoards, memberPage] = await Promise.all([api.accounts(), api.scoreboards(true), api.members(true)]); setAccounts(nextAccounts); setBoards(nextBoards); setMembers(memberPage.content) } catch { setError(text.error) } }, [text.error])
  useEffect(() => { void refresh() }, [refresh])
  function toggleCreateRole(role: Role) { setRoles((current) => current.includes(role) ? current.filter((item) => item !== role) : [...current, role]) }
  async function create(event: FormEvent) { event.preventDefault(); if (!roles.length) return; try { const created = await api.createAccount({ username, roles, preferredLocale }, csrf); setTemporaryPassword(created.temporaryPassword); setUsername(''); await refresh() } catch { setError(text.error) } }
  async function patch(account: Account, body: Partial<Pick<Account, 'roles' | 'enabled' | 'memberId' | 'preferredLocale'>>) { try { await api.updateAccount(account.id, body, csrf); await refresh() } catch { setError(text.error) } }
  async function toggleRole(account: Account, role: Role) { const next = account.roles.includes(role) ? account.roles.filter((item) => item !== role) : [...account.roles, role]; if (!next.length) return; await patch(account, { roles: next }) }
  async function resetPassword(account: Account) { try { const result = await api.issueTemporaryPassword(account.id, csrf); setTemporaryPassword(result.temporaryPassword) } catch { setError(text.error) } }
  async function assign(account: Account, boardId: string, checked: boolean) { try { await api.assignScorer(account.id, boardId, checked, csrf); await refresh() } catch { setError(text.error) } }

  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">{text.admin}</p><h1>{text.accounts}</h1><p>{text.accountHint}</p></div></header>
    <Feedback error={error} />{temporaryPassword && <div className="secret-callout" role="status"><span>{text.temporaryPassword}</span><code>{temporaryPassword}</code><button className="button button--ghost" onClick={() => navigator.clipboard.writeText(temporaryPassword)} type="button">Copy</button></div>}
    <section className="split-layout"><form className="panel form-panel" onSubmit={create}><div className="panel__header"><h2>{text.create}</h2></div>
      <label className="field"><span>{text.username}</span><input required pattern="[A-Za-z0-9._-]{3,64}" value={username} onChange={(e) => setUsername(e.target.value)} /></label>
      <fieldset className="role-grid"><legend>{text.role}</legend>{ALL_ROLES.map((role) => <label className="check-card" key={role}><input type="checkbox" checked={roles.includes(role)} onChange={() => toggleCreateRole(role)} /><span>{role}</span></label>)}</fieldset>
      <label className="field"><span>{text.language}</span><select value={preferredLocale} onChange={(e) => setPreferredLocale(e.target.value as 'en' | 'de')}><option value="en">English</option><option value="de">Deutsch</option></select></label>
      <button className="button button--primary" type="submit">{text.create}</button></form>
      <section className="panel"><div className="panel__header"><h2>{text.accounts}</h2><span className="count-badge">{accounts.length}</span></div><div className="resource-list">{accounts.map((account) => <details className="account-card" key={account.id}>
        <summary><span className="person-avatar">{account.username.slice(0, 2).toUpperCase()}</span><span><strong>{account.username}</strong><small>{account.roles.join(' · ')}</small></span><span className={`status ${account.enabled ? 'status--active' : 'status--closed'}`}>{account.enabled ? text.active : text.inactive}</span></summary>
        <div className="account-card__body"><fieldset className="role-grid"><legend>{text.role}</legend>{ALL_ROLES.map((role) => <label className="check-card" key={role}><input type="checkbox" checked={account.roles.includes(role)} disabled={account.id === currentAccountId} onChange={() => toggleRole(account, role)} /><span>{role}</span></label>)}</fieldset>
          <label className="field"><span>{text.member}</span><select disabled={account.id === currentAccountId} value={account.memberId ?? ''} onChange={(e) => patch(account, { memberId: e.target.value || null })}><option value="">—</option>{members.map((member) => <option key={member.id} value={member.id}>{member.displayName}</option>)}</select></label>
          <label className="field"><span>{text.language}</span><select disabled={account.id === currentAccountId} value={account.preferredLocale ?? 'en'} onChange={(e) => patch(account, { preferredLocale: e.target.value as 'en' | 'de' })}><option value="en">English</option><option value="de">Deutsch</option></select></label>
          {account.roles.includes('Scorer') && <fieldset className="check-grid"><legend>{text.scorerAssignments}</legend>{boards.map((board) => <label className="check-card" key={board.id}><input type="checkbox" checked={account.scorerAssignments.includes(board.id)} onChange={(e) => assign(account, board.id, e.target.checked)} /><span>{board.name}</span></label>)}</fieldset>}
          <div className="button-row"><button className="button button--ghost" disabled={account.id === currentAccountId} type="button" onClick={() => resetPassword(account)}>{text.password}</button><button className="button button--ghost" type="button" disabled={account.id === currentAccountId} onClick={() => patch(account, { enabled: !account.enabled })}>{account.enabled ? text.inactive : text.activate}</button></div>
        </div>
      </details>)}</div></section></section>
  </div>
}
