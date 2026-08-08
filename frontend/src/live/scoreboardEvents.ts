const EVENT_NAMES = [
  'snapshot',
  'score-created',
  'score-cancelled',
  'period-changed',
  'participation-changed',
] as const

export function subscribeToScoreboard(scoreboardId: string, reconcile: () => void) {
  const source = new EventSource(`/api/v1/scoreboards/${scoreboardId}/events`, {
    withCredentials: true,
  })
  EVENT_NAMES.forEach((eventName) => source.addEventListener(eventName, reconcile))
  return () => source.close()
}
