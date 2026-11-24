import { useEffect, useRef, useState } from 'react'
import { AuditEventView, listEvents } from '../api/artifacts'
import type { Page } from '../api/links'

const SIZE = 10

/** Chronological audit events for a run with Previous/Next paging. */
export default function AuditTimeline({ runId }: { runId: string }) {
  const [pageNo, setPageNo] = useState(0)
  const [data, setData] = useState<Page<AuditEventView> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const errRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)
    listEvents(runId, pageNo, SIZE)
      .then((d) => !cancelled && setData(d))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [runId, pageNo])

  useEffect(() => {
    if (error) errRef.current?.focus()
  }, [error])

  return (
    <section aria-labelledby="timeline-heading" className="panel">
      <h3 id="timeline-heading">Audit timeline</h3>
      {error && (
        <p role="alert" tabIndex={-1} ref={errRef} className="error">
          {error}
        </p>
      )}
      {!error && data === null && <p>Loading events…</p>}
      {data && data.items.length === 0 && <p>No events yet</p>}
      {data && data.items.length > 0 && (
        <ol className="timeline">
          {data.items.map((e) => (
            <li key={e.id}>
              <time dateTime={e.occurredAt}>{new Date(e.occurredAt).toLocaleString()}</time> {e.actorType}:{e.actorId}{' '}
              <strong>{e.eventType}</strong>
              {e.details ? ` - ${e.details}` : ''}
              {e.correlationId ? ` (correlation ${e.correlationId})` : ''}
            </li>
          ))}
        </ol>
      )}
      {data && (
        <nav aria-label="Audit timeline pages">
          <button type="button" disabled={pageNo <= 0} onClick={() => setPageNo((p) => p - 1)}>
            Previous
          </button>{' '}
          <span aria-live="polite">
            Page {data.page + 1} of {Math.max(data.totalPages, 1)}
          </span>{' '}
          <button type="button" disabled={pageNo + 1 >= data.totalPages} onClick={() => setPageNo((p) => p + 1)}>
            Next
          </button>
        </nav>
      )}
    </section>
  )
}
