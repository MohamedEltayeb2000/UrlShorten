import { useEffect, useRef, useState } from 'react'
import { PendingApproval, listPendingApprovals } from '../api/approvals'
import ApprovalCard from '../components/ApprovalCard'

export default function ApprovalsPage() {
  const [items, setItems] = useState<PendingApproval[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const errRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    let cancelled = false
    listPendingApprovals()
      .then((a) => !cancelled && setItems(a))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (error) errRef.current?.focus()
  }, [error])

  return (
    <section aria-labelledby="approvals-heading">
      <h2 id="approvals-heading">Approvals</h2>
      {error && (
        <p role="alert" tabIndex={-1} ref={errRef} className="error">
          {error}
        </p>
      )}
      {!error && items === null && <p>Loading approvals…</p>}
      {items && items.length === 0 && <p>Nothing is waiting for approval</p>}
      {items && items.length > 0 && (
        <ul className="approval-list" aria-label="Pending approvals">
          {items.map((i) => (
            <ApprovalCard key={i.approvalId} item={i} />
          ))}
        </ul>
      )}
    </section>
  )
}
