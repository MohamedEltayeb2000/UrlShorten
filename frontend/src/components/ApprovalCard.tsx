import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApprovalView, PendingApproval, decide } from '../api/approvals'
import { ApiError } from '../api/http'

const FORBIDDEN = 'Not allowed: agents and the artifact author cannot approve their own output. Switch "Acting as" to a different reviewer.'

/** One pending approval: context, acting-as, comment, Approve / Reject, and the recorded result. */
export default function ApprovalCard({ item }: { item: PendingApproval }) {
  const key = `ap-${item.approvalId}`
  const [user, setUser] = useState('demo-user')
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<ApprovalView | null>(null)
  const errRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (error) errRef.current?.focus()
  }, [error])

  async function submit(decision: 'APPROVE' | 'REJECT') {
    if (decision === 'REJECT' && !comment.trim()) {
      setError('A comment is required when rejecting.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const body = { nodeId: item.nodeId, decision, comment: comment.trim(), artifactVersion: item.artifactVersion }
      setResult(await decide(item.runId, body, user))
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'APPROVAL_FORBIDDEN' ? `${FORBIDDEN} (${e.message})` : (e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="approval-card panel">
      <article aria-labelledby={`${key}-h`}>
        <h3 id={`${key}-h`}>
          Run #{item.runId} - {item.nodeName}
        </h3>
        <p>
          <span className="status status-waiting_for_approval">{item.kind}</span> {item.reason}
        </p>
        {item.artifactType && (
          <p>
            Artifact: {item.artifactType}
            {item.artifactVersion !== undefined && ` v${item.artifactVersion}`}
          </p>
        )}
        {item.summary && <p>Summary: {item.summary}</p>}
        {item.openQuestions && item.openQuestions.length > 0 && (
          <>
            <h4>Open questions</h4>
            <ul>{item.openQuestions.map((q) => <li key={q}>{q}</li>)}</ul>
          </>
        )}
        {item.assumptions && item.assumptions.length > 0 && (
          <>
            <h4>Assumptions</h4>
            <ul>{item.assumptions.map((a) => <li key={a}>{a}</li>)}</ul>
          </>
        )}
        {item.policyReason && <p>Policy: {item.policyReason}</p>}
        <p>
          <Link to={`/runs/${item.runId}/artifacts`}>View artifacts for run {item.runId}</Link>
        </p>
        {result ? (
          <p role="status" className={`decision decision-${result.status.toLowerCase()}`}>
            {result.status === 'REJECTED'
              ? 'Blocked - feedback recorded'
              : `Approved by ${result.actorId ?? user}`}
            {result.comment ? `: ${result.comment}` : ''}
          </p>
        ) : (
          <>
            <label htmlFor={`${key}-user`}>Acting as</label>
            <input id={`${key}-user`} type="text" value={user} onChange={(e) => setUser(e.target.value)} />
            <label htmlFor={`${key}-comment`}>Comment (required when rejecting)</label>
            <textarea
              id={`${key}-comment`}
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              aria-describedby={error ? `${key}-err` : undefined}
            />
            {error && (
              <p id={`${key}-err`} role="alert" tabIndex={-1} ref={errRef} className="error">
                {error}
              </p>
            )}
            <div className="actions">
              <button type="button" disabled={busy} onClick={() => void submit('APPROVE')}>
                Approve
              </button>
              <button type="button" disabled={busy} onClick={() => void submit('REJECT')}>
                Reject
              </button>
            </div>
          </>
        )}
      </article>
    </li>
  )
}
