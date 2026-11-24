import { useEffect, useState } from 'react'
import { getSummary, Summary } from '../api/analytics'

/** Small analytics overview: totals, top links and the most recent clicks. `refreshKey` re-fetches. */
export default function AnalyticsPanel({ refreshKey }: { refreshKey: number }) {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)
    getSummary()
      .then((s) => !cancelled && setSummary(s))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return (
    <section aria-labelledby="analytics-heading" className="panel">
      <h2 id="analytics-heading">Analytics</h2>
      {error && <p className="error">Analytics unavailable: {error}</p>}
      {!error && summary === null && <p>Loading analytics…</p>}
      {summary && (
        <>
          <p>
            <strong>{summary.totalClicks}</strong> total clicks across <strong>{summary.totalLinks}</strong> links
          </p>
          <h3>Top links</h3>
          {summary.topLinks.length === 0 ? (
            <p>No clicks yet.</p>
          ) : (
            <ol>
              {summary.topLinks.map((t) => (
                <li key={t.code}>
                  {t.code}: {t.clicks} clicks
                </li>
              ))}
            </ol>
          )}
          <h3>Recent clicks</h3>
          {summary.recentEvents.items.length === 0 ? (
            <p>No recent clicks.</p>
          ) : (
            <ul>
              {summary.recentEvents.items.map((e, i) => (
                <li key={i}>
                  {e.code} at {new Date(e.occurredAt).toLocaleString()}
                  {e.referrer ? ` from ${e.referrer}` : ''}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
