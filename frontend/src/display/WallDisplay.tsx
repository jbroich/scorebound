import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type DisplayConfiguration, type ScoreTransaction, type Standings, type TransactionPage } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { useLocale } from '../i18n/useLocale'
import { ReconciliationGate } from '../live/ReconciliationGate'
import { subscribeToScoreboard } from '../live/scoreboardEvents'
import { Brand } from '../ui/Brand'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'

type ScoreEffect = { teamId: string; direction: 'credit' | 'debit'; token: string }

export function WallDisplay() {
  const { session, logout } = useAuth()
  const { locale, formatDateTime, formatNumber } = useLocale()
  const [configuration, setConfiguration] = useState<DisplayConfiguration | null>(null)
  const [boardIndex, setBoardIndex] = useState(0)
  const [standings, setStandings] = useState<Standings | null>(null)
  const [transactions, setTransactions] = useState<TransactionPage | null>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [error, setError] = useState(false)
  const [effect, setEffect] = useState<ScoreEffect | null>(null)
  const [audioReady, setAudioReady] = useState(false)
  const [now, setNow] = useState(new Date())
  const reconciliation = useRef(new ReconciliationGate())
  const activityToken = useRef<string | null>(null)
  const audioContext = useRef<AudioContext | null>(null)
  const wakeLock = useRef<WakeLockSentinel | null>(null)

  const boards = useMemo(() => configuration?.scoreboards ?? [], [configuration])
  const activeBoard = useMemo(() => {
    if (!configuration || !boards.length) return null
    if (configuration.mode === 'Fixed') {
      return boards.find((board) => board.id === configuration.fixedScoreboardId) ?? boards[0]
    }
    return boards[boardIndex % boards.length]
  }, [boardIndex, boards, configuration])

  const loadConfiguration = useCallback(async () => {
    try {
      const next = await api.displayConfiguration()
      setConfiguration((current) => {
        if (current && JSON.stringify(current) === JSON.stringify(next)) return current
        return next
      })
    } catch {
      setError(true)
    }
  }, [])

  useEffect(() => {
    void loadConfiguration()
    const timer = window.setInterval(() => { void loadConfiguration() }, 5_000)
    return () => window.clearInterval(timer)
  }, [loadConfiguration])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 30_000)
    return () => {
      window.clearInterval(timer)
      void wakeLock.current?.release()
    }
  }, [])

  useEffect(() => {
    if (configuration?.mode !== 'Rotation' || boards.length < 2) return undefined
    const timer = window.setInterval(() => setBoardIndex((index) => (index + 1) % boards.length), configuration.rotationSeconds * 1000)
    return () => window.clearInterval(timer)
  }, [boards.length, configuration?.mode, configuration?.rotationSeconds])

  const play = useCallback((direction: 'credit' | 'debit') => {
    if (!audioReady || !audioContext.current) return
    const context = audioContext.current
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = direction === 'credit' ? 'sine' : 'triangle'
    oscillator.frequency.setValueAtTime(direction === 'credit' ? 660 : 210, context.currentTime)
    if (direction === 'credit') oscillator.frequency.exponentialRampToValueAtTime(990, context.currentTime + .18)
    gain.gain.setValueAtTime(.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(.12, context.currentTime + .02)
    gain.gain.exponentialRampToValueAtTime(.0001, context.currentTime + .28)
    oscillator.connect(gain).connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + .3)
  }, [audioReady])

  const detectActivity = useCallback((page: TransactionPage) => {
    const latest = page.content[0]
    if (!latest) return
    const token = `${latest.id}:${latest.cancellation?.createdAt ?? 'active'}`
    if (activityToken.current && activityToken.current !== token) {
      const direction = latest.cancellation
        ? (latest.kind === 'Credit' ? 'debit' : 'credit')
        : latest.kind.toLowerCase() as 'credit' | 'debit'
      setEffect({ teamId: latest.teamId, direction, token })
      play(direction)
      window.setTimeout(() => setEffect((current) => current?.token === token ? null : current), 1100)
    }
    activityToken.current = token
  }, [play])

  const refresh = useCallback(async (scoreboardId: string) => {
    try {
      const result = await reconciliation.current.run(() => Promise.all([
        api.standings(scoreboardId), api.transactions(scoreboardId),
      ]))
      if (!result.current) return
      setStandings(result.value[0])
      setTransactions(result.value[1])
      detectActivity(result.value[1])
      setError(false)
    } catch {
      setStandings(null)
      setTransactions(null)
      setError(true)
    }
  }, [detectActivity])

  useEffect(() => {
    activityToken.current = null
    setStandings(null)
    setTransactions(null)
    if (!activeBoard) return undefined
    void refresh(activeBoard.id)
    return subscribeToScoreboard(activeBoard.id, () => { void refresh(activeBoard.id) })
  }, [activeBoard, refresh])

  async function saveConfiguration(next: DisplayConfiguration) {
    try {
      const saved = await api.updateDisplayConfiguration({
        mode: next.mode,
        fixedScoreboardId: next.fixedScoreboardId,
        rotationSeconds: next.rotationSeconds,
        soundEnabled: next.soundEnabled,
      }, session?.csrfToken ?? null)
      setConfiguration(saved)
      setSettingsOpen(false)
    } catch {
      setError(true)
    }
  }

  async function enableAudio() {
    const context = audioContext.current ?? new AudioContext()
    audioContext.current = context
    await context.resume()
    setAudioReady(true)
    if (configuration && !configuration.soundEnabled) {
      await saveConfiguration({ ...configuration, soundEnabled: true })
    }
  }

  async function enterKioskMode() {
    try {
      if (!document.fullscreenElement) await document.documentElement.requestFullscreen()
      await screen.orientation.lock('landscape')
      if ('wakeLock' in navigator) wakeLock.current = await navigator.wakeLock.request('screen')
    } catch {
      // Fullscreen, orientation lock, and wake lock are best-effort browser capabilities.
    }
  }

  const recent = transactions?.content.slice(0, 4) ?? []
  const winnerCount = standings?.standings.filter((standing) => standing.winner).length ?? 0

  return <main className="wall-display">
    <div className="wall-aurora" aria-hidden="true" />
    <header className="wall-header">
      <Brand />
      <div className="wall-title"><p>{standings?.periodName ?? (locale === 'de' ? 'Kein aktiver Zeitraum' : 'No active period')}</p><h1>{activeBoard?.name ?? 'Scorebound'}</h1></div>
      <div className="wall-controls"><LanguageSwitcher compact />
        {configuration?.soundEnabled && !audioReady && <button className="wall-icon-button wall-sound-optin" type="button" onClick={enableAudio}>♪ {locale === 'de' ? 'Sound an' : 'Enable sound'}</button>}
        <button className="wall-icon-button" type="button" aria-label={locale === 'de' ? 'Vollbild und Querformat aktivieren' : 'Enable fullscreen landscape mode'} onClick={enterKioskMode}>⛶</button>
        <button className="wall-icon-button" type="button" aria-label={locale === 'de' ? 'Anzeige konfigurieren' : 'Configure display'} onClick={() => setSettingsOpen(true)}>⚙</button>
      </div>
    </header>

    {error && <div className="wall-notice" role="status">{locale === 'de' ? 'Verbindung wird wiederhergestellt …' : 'Reconnecting …'}</div>}
    {!boards.length && <section className="wall-empty"><span>◇</span><h2>{locale === 'de' ? 'Noch kein Scoreboard zugeordnet' : 'No scoreboard assigned yet'}</h2><p>{locale === 'de' ? 'Ein Admin kann diese Anzeige einem Scoreboard zuordnen.' : 'An admin can assign this display to a scoreboard.'}</p></section>}

    {!!standings?.standings.length && <section className={`glass-grid glass-grid--${Math.min(standings.standings.length, 6)}`} aria-label={locale === 'de' ? 'Punktestände' : 'Standings'}>
      {standings.standings.map((standing) => {
        const ratio = Math.max(0, Math.min(1, standing.score / Math.max(standings.visualCeiling, 1)))
        const ballCount = standing.score > 0 ? Math.max(1, Math.round(ratio * 48)) : 0
        const activeEffect = effect?.teamId === standing.teamId ? effect.direction : null
        return <article className={`glass-team${standing.winner ? ' glass-team--winner' : ''}${activeEffect ? ` glass-team--${activeEffect}` : ''}`} key={standing.teamId} style={{ '--team': standing.color } as React.CSSProperties}>
          {standing.winner && <div className="winner-crown" aria-label={locale === 'de' ? 'Gewinner' : 'Winner'}>✦</div>}
          <div className="glass-vessel" aria-hidden="true"><div className="glass-shine" /><div className="ball-field" style={{ height: `${Math.max(ratio * 82, ballCount ? 8 : 0)}%` }}>
            {Array.from({ length: ballCount }, (_, index) => <i key={index} style={{ '--ball-index': index } as React.CSSProperties} />)}
          </div></div>
          <div className="glass-team__identity"><div><span className="glass-rank">#{standing.rank}</span><h2>{standing.teamName}</h2><small>{standing.shortName}</small></div><strong>{formatNumber(standing.score)}</strong></div>
        </article>
      })}
    </section>}

    <footer className="wall-footer">
      <div className="activity-strip"><span className="activity-label">{locale === 'de' ? 'Gerade eben' : 'Recent'}</span>{recent.map((item) => <Activity key={item.id} item={item} locale={locale} />)}{!recent.length && <span className="activity-empty">{locale === 'de' ? 'Die Runde wartet auf Punkte.' : 'Waiting for the first score.'}</span>}</div>
      <div className="wall-meta"><span className={`live-dot${error ? ' live-dot--error' : ''}`} />{configuration?.mode === 'Rotation' && boards.length > 1 && <span>{boardIndex % boards.length + 1}/{boards.length}</span>}<time>{formatDateTime(now)}</time></div>
    </footer>

    {winnerCount > 0 && standings?.status === 'Closed' && <div className="winner-celebration" aria-live="polite"><span>✦</span>{winnerCount > 1 ? (locale === 'de' ? `${winnerCount} Siegerteams!` : `${winnerCount} winning teams!`) : (locale === 'de' ? 'Wir haben ein Siegerteam!' : 'We have a winner!')}<span>✦</span></div>}
    {settingsOpen && configuration && <DisplaySettings configuration={configuration} locale={locale} close={() => setSettingsOpen(false)} save={saveConfiguration} logout={logout} />}
  </main>
}

function Activity({ item, locale }: { item: ScoreTransaction; locale: 'en' | 'de' }) {
  const sign = item.kind === 'Credit' ? '+' : '−'
  return <span className={`activity-item${item.cancellation ? ' activity-item--cancelled' : ''}`}><b>{sign}{item.amount}</b><span>{item.memberName ?? item.teamName}</span><small>{item.cancellation ? (locale === 'de' ? 'storniert' : 'cancelled') : item.reason}</small></span>
}

function DisplaySettings({ configuration, locale, close, save, logout }: { configuration: DisplayConfiguration; locale: 'en' | 'de'; close: () => void; save: (next: DisplayConfiguration) => Promise<void>; logout: () => Promise<void> }) {
  const [draft, setDraft] = useState(configuration)
  return <div className="wall-settings-backdrop"><section className="wall-settings" role="dialog" aria-modal="true" aria-labelledby="display-settings-title"><div className="panel__header"><div><p className="eyebrow">Display</p><h2 id="display-settings-title">{locale === 'de' ? 'Anzeige konfigurieren' : 'Display settings'}</h2></div><button className="wall-icon-button" onClick={close} type="button">×</button></div>
    <div className="segmented-control segmented-control--wide"><button className="segmented-control__button" aria-pressed={draft.mode === 'Fixed'} onClick={() => setDraft({ ...draft, mode: 'Fixed' })} type="button">Fixed</button><button className="segmented-control__button" aria-pressed={draft.mode === 'Rotation'} onClick={() => setDraft({ ...draft, mode: 'Rotation' })} type="button">Rotation</button></div>
    <label className="field"><span>Scoreboard</span><select value={draft.fixedScoreboardId ?? ''} onChange={(event) => setDraft({ ...draft, fixedScoreboardId: event.target.value })}>{draft.scoreboards.map((board) => <option key={board.id} value={board.id}>{board.name}</option>)}</select></label>
    {draft.mode === 'Rotation' && <label className="field"><span>{locale === 'de' ? 'Wechsel in Sekunden' : 'Rotate every seconds'}</span><input type="number" min="10" max="300" value={draft.rotationSeconds} onChange={(event) => setDraft({ ...draft, rotationSeconds: Number(event.target.value) })} /></label>}
    <label className="check-card"><input type="checkbox" checked={draft.soundEnabled} onChange={(event) => setDraft({ ...draft, soundEnabled: event.target.checked })} /><span>{locale === 'de' ? 'Soundeffekte erlauben' : 'Allow sound effects'}</span></label>
    <div className="button-row"><button className="button button--ghost" onClick={() => void logout()} type="button">Logout</button><button className="button button--ghost" onClick={close} type="button">{locale === 'de' ? 'Abbrechen' : 'Cancel'}</button><button className="button button--primary" onClick={() => void save(draft)} type="button">{locale === 'de' ? 'Speichern' : 'Save'}</button></div>
  </section></div>
}
