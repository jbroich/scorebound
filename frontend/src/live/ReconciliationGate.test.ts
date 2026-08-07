import { describe, expect, it } from 'vitest'
import { ReconciliationGate } from './ReconciliationGate'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((next) => { resolve = next })
  return { promise, resolve }
}

describe('ReconciliationGate', () => {
  it('rejects a response that arrives after a newer response', async () => {
    const gate = new ReconciliationGate()
    const older = deferred<string>()
    const newer = deferred<string>()

    const olderResult = gate.run(() => older.promise)
    const newerResult = gate.run(() => newer.promise)
    newer.resolve('authoritative-newer-state')
    older.resolve('stale-state')

    await expect(newerResult).resolves.toEqual({ current: true, value: 'authoritative-newer-state' })
    await expect(olderResult).resolves.toEqual({ current: false })
  })

  it('suppresses a stale request failure', async () => {
    const gate = new ReconciliationGate()
    let rejectOlder!: (reason: Error) => void
    const older = new Promise<string>((_resolve, reject) => { rejectOlder = reject })

    const olderResult = gate.run(() => older)
    const newerResult = gate.run(async () => 'current-state')
    rejectOlder(new Error('old network failure'))

    await expect(newerResult).resolves.toEqual({ current: true, value: 'current-state' })
    await expect(olderResult).resolves.toEqual({ current: false })
  })
})
