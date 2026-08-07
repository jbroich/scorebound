export type Role = 'Admin' | 'Scorer' | 'Member' | 'Display'

export type Session = {
  accountId: string
  username: string
  memberId: string | null
  roles: Role[]
  effectiveRoles: Role[]
  mustChangePassword: boolean
  preferredLocale: 'en' | 'de' | null
  mode: 'Normal' | 'Display'
  csrfToken: string | null
}

export type Team = {
  id: string
  name: string
  shortName: string
  color: string
  active: boolean
  imageUrl: string | null
}

export type Membership = {
  id: string
  teamId: string
  teamName: string
  validFrom: string
  validUntil: string | null
}

export type Member = {
  id: string
  displayName: string
  firstName: string | null
  lastName: string | null
  active: boolean
  activeTeamId: string | null
  memberships: Membership[]
}

export type MemberPage = {
  content: Member[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PeriodSummary = {
  id: string
  scoreboardId: string
  name: string
  startsAt: string
  endsAt: string
  status: 'Scheduled' | 'Active' | 'Closed'
  closedAt: string | null
  visualCeiling: number
}

export type SelectedTeam = Team & { position: number }

export type ScoreboardSummary = {
  id: string
  name: string
  description: string | null
  active: boolean
}

export type Scoreboard = ScoreboardSummary & {
  selectedTeams: SelectedTeam[]
  activePeriod: PeriodSummary | null
}

export type Standing = {
  teamId: string
  teamName: string
  shortName: string
  color: string
  score: number
  rank: number
  winner: boolean
}

export type Standings = {
  periodId: string
  periodName: string
  status: PeriodSummary['status']
  startsAt: string
  endsAt: string
  visualCeiling: number
  standings: Standing[]
}

export type Cancellation = {
  reason: string
  createdAt: string
  createdBy: string
  createdByUsername: string | null
}

export type ScoreTransaction = {
  id: string
  periodId: string
  teamId: string
  teamName: string
  memberId: string | null
  memberName: string | null
  kind: 'Credit' | 'Debit'
  amount: number
  reason: string
  createdAt: string
  createdBy: string
  createdByUsername: string | null
  cancellation: Cancellation | null
  resultingTeamScore: number | null
}

export type TransactionPage = {
  periodId: string
  content: ScoreTransaction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Account = {
  id: string
  username: string
  memberId: string | null
  roles: Role[]
  enabled: boolean
  mustChangePassword: boolean
  preferredLocale: 'en' | 'de' | null
  scorerAssignments: string[]
}

export type TemporaryAccount = Account & { temporaryPassword: string }

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string) {
    super(code)
    this.status = status
    this.code = code
  }
}

async function request<T>(path: string, init?: RequestInit, csrfToken?: string | null): Promise<T> {
  const headers = new Headers(init?.headers)
  if (init?.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (csrfToken) {
    headers.set('X-CSRF-TOKEN', csrfToken)
  }
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: 'same-origin',
    headers,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string } | null
    throw new ApiError(response.status, problem?.code ?? 'request_failed')
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

function json(method: string, body?: unknown): RequestInit {
  return { method, body: body === undefined ? undefined : JSON.stringify(body) }
}

export const api = {
  currentSession: () => request<Session>('/session'),
  login: (username: string, password: string, mode: 'Normal' | 'Display' = 'Normal') =>
    request<Session>('/sessions', json('POST', { username, password, mode })),
  logout: (csrf: string | null) => request<void>('/session', { method: 'DELETE' }, csrf),
  changePassword: (currentPassword: string, newPassword: string, csrf: string | null) =>
    request<void>('/session/password', json('PUT', { currentPassword, newPassword }), csrf),

  teams: (includeInactive = false) =>
    request<Team[]>(`/teams${includeInactive ? '?includeInactive=true' : ''}`),
  createTeam: (body: Pick<Team, 'name' | 'shortName' | 'color'>, csrf: string | null) =>
    request<Team>('/teams', json('POST', body), csrf),
  updateTeam: (id: string, body: Partial<Pick<Team, 'name' | 'shortName' | 'color' | 'active'>>, csrf: string | null) =>
    request<Team>(`/teams/${id}`, json('PATCH', body), csrf),

  members: (includeInactive = false) =>
    request<MemberPage>(`/members?size=100${includeInactive ? '&includeInactive=true' : ''}`),
  createMember: (body: { displayName: string; firstName?: string; lastName?: string; teamId: string }, csrf: string | null) =>
    request<Member>('/members', json('POST', body), csrf),
  updateMember: (id: string, body: Partial<Pick<Member, 'displayName' | 'firstName' | 'lastName' | 'active'>>, csrf: string | null) =>
    request<Member>(`/members/${id}`, json('PATCH', body), csrf),
  changeMemberTeam: (id: string, teamId: string, csrf: string | null) =>
    request<Member>(`/members/${id}/team-changes`, json('POST', { teamId }), csrf),

  scoreboards: (includeInactive = false) =>
    request<ScoreboardSummary[]>(`/scoreboards${includeInactive ? '?includeInactive=true' : ''}`),
  scoreboard: (id: string) => request<Scoreboard>(`/scoreboards/${id}`),
  createScoreboard: (body: { name: string; description?: string }, csrf: string | null) =>
    request<Scoreboard>('/scoreboards', json('POST', body), csrf),
  updateScoreboard: (id: string, body: Partial<Pick<Scoreboard, 'name' | 'description' | 'active'>>, csrf: string | null) =>
    request<Scoreboard>(`/scoreboards/${id}`, json('PATCH', body), csrf),
  selectTeam: (scoreboardId: string, teamId: string, selected: boolean, csrf: string | null) =>
    request<void>(`/scoreboards/${scoreboardId}/teams/${teamId}`, { method: selected ? 'PUT' : 'DELETE' }, csrf),
  periods: (scoreboardId: string) => request<PeriodSummary[]>(`/scoreboards/${scoreboardId}/periods`),
  createPeriod: (scoreboardId: string, body: { name: string; startsAt: string; endsAt: string }, csrf: string | null) =>
    request<PeriodSummary>(`/scoreboards/${scoreboardId}/periods`, json('POST', body), csrf),
  transitionPeriod: (scoreboardId: string, periodId: string, action: 'activate' | 'close' | 'reopen', csrf: string | null) =>
    request<PeriodSummary>(`/scoreboards/${scoreboardId}/periods/${periodId}/${action}`, { method: 'POST' }, csrf),

  standings: (scoreboardId: string) => request<Standings>(`/scoreboards/${scoreboardId}/standings`),
  transactions: (scoreboardId: string) => request<TransactionPage>(`/scoreboards/${scoreboardId}/transactions?size=50`),
  score: (scoreboardId: string, body: { teamId?: string; memberId?: string; kind: 'Credit' | 'Debit'; amount: number; reason: string }, csrf: string | null) =>
    request<ScoreTransaction>(`/scoreboards/${scoreboardId}/transactions`, {
      ...json('POST', body),
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }, csrf),
  cancel: (scoreboardId: string, transactionId: string, reason: string, csrf: string | null) =>
    request<ScoreTransaction>(`/scoreboards/${scoreboardId}/transactions/${transactionId}/cancellation`, json('POST', { reason }), csrf),

  accounts: () => request<Account[]>('/accounts'),
  createAccount: (body: { username: string; roles: Role[]; preferredLocale?: 'en' | 'de' }, csrf: string | null) =>
    request<TemporaryAccount>('/accounts', json('POST', body), csrf),
  updateAccount: (id: string, body: Partial<Pick<Account, 'roles' | 'enabled' | 'memberId' | 'preferredLocale'>>, csrf: string | null) =>
    request<Account>(`/accounts/${id}`, json('PATCH', body), csrf),
  issueTemporaryPassword: (id: string, csrf: string | null) =>
    request<{ accountId: string; temporaryPassword: string }>(`/accounts/${id}/temporary-password`, { method: 'POST' }, csrf),
  assignScorer: (accountId: string, scoreboardId: string, assigned: boolean, csrf: string | null) =>
    request<void>(`/accounts/${accountId}/scorer-assignments/${scoreboardId}`, { method: assigned ? 'PUT' : 'DELETE' }, csrf),
}
