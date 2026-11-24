import { useEffect, useRef, useState } from 'react'

/** Calls `fn` every `intervalMs` while `enabled`; stops on disable/unmount. */
export function usePolling<T>(fn: () => Promise<T>, enabled: boolean, intervalMs = 2000) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState(false)
  const fnRef = useRef(fn)
  fnRef.current = fn

  useEffect(() => {
    if (!enabled) return
    let active = true
    const tick = async () => {
      setLoading(true)
      try {
        const result = await fnRef.current()
        if (active) {
          setData(result)
          setError(null)
        }
      } catch (e) {
        if (active) setError(e as Error)
      } finally {
        if (active) setLoading(false)
      }
    }
    const timer = setInterval(() => void tick(), intervalMs)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [enabled, intervalMs])

  return { data, error, loading }
}
