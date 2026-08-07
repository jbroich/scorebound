export function Feedback({ error, success }: { error?: string | null; success?: string | null }) {
  if (!error && !success) return null
  return <div className={`feedback ${error ? 'feedback--error' : 'feedback--success'}`} role="status">
    {error ?? success}
  </div>
}
