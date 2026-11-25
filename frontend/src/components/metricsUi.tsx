import { useEffect, useRef, useState } from 'react'

export const pct = (v: number | null | undefined) => (v == null ? 'n/a' : `${Math.round(v * 100)}%`)

export function dur(ms: number | null | undefined) {
  if (ms == null) return 'n/a'
  if (ms < 1000) return `${Math.round(ms)} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`
  return `${(ms / 60000).toFixed(1)} min`
}

export function ErrorAlert({ message }: { message: string }) {
  const ref = useRef<HTMLParagraphElement>(null)
  useEffect(() => ref.current?.focus(), [message])
  return (
    <p role="alert" tabIndex={-1} ref={ref} className="error">
      {message}
    </p>
  )
}

export interface Loaded<T> {
  data: T | null
  error: string | null
}

/** Load once per key; stale results are ignored. */
export function useLoaded<T>(load: () => Promise<T>, key: unknown): Loaded<T> {
  const [state, setState] = useState<Loaded<T> & { key: unknown }>({ data: null, error: null, key })
  useEffect(() => {
    let cancelled = false
    load()
      .then((data) => !cancelled && setState({ data, error: null, key }))
      .catch((e: Error) => !cancelled && setState({ data: null, error: e.message, key }))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return state.key === key ? state : { data: null, error: null }
}

export function Tile({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="tile" role="group" aria-label={label}>
      <span className="tile-label">{label}</span>
      <strong className="tile-value">{value}</strong>
    </div>
  )
}
