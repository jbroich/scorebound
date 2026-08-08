export type ReconciliationResult<T> =
  | { current: true; value: T }
  | { current: false }

export class ReconciliationGate {
  private requestSequence = 0

  async run<T>(load: () => Promise<T>): Promise<ReconciliationResult<T>> {
    const request = ++this.requestSequence
    try {
      const value = await load()
      return request === this.requestSequence ? { current: true, value } : { current: false }
    } catch (error) {
      if (request !== this.requestSequence) return { current: false }
      throw error
    }
  }
}
