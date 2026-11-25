import { useState } from 'react'
import type { RunNode } from '../api/runs'

interface Props {
  node: RunNode
  busy: boolean
  onRetry: (nodeId: string) => void
  onReplan: (nodeId: string, requirement?: string) => void
}

/** Per-stage controls: Retry (FAILED/BLOCKED only) and an expandable re-plan form. */
export default function StageActions({ node, busy, onRetry, onReplan }: Props) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const id = `replan-${node.id}`
  const canRetry = node.status === 'FAILED' || node.status === 'BLOCKED'
  return (
    <div className="stage-actions">
      <div className="actions">
        {canRetry && (
          <button type="button" disabled={busy} onClick={() => onRetry(node.id)} aria-label={`Retry ${node.name}`}>
            Retry
          </button>
        )}
        <button type="button" aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>
          Re-plan from this stage
        </button>
      </div>
      {open && (
        <form
          id={id}
          className="replan"
          onSubmit={(e) => {
            e.preventDefault()
            onReplan(node.id, text.trim() || undefined)
            setOpen(false)
            setText('')
          }}
        >
          <label htmlFor={`${id}-req`}>Changed requirement (optional) for {node.name}</label>
          <textarea id={`${id}-req`} rows={3} value={text} onChange={(e) => setText(e.target.value)} />
          <p className="note">
            Re-planning marks downstream stages stale and re-runs only the affected ones.
          </p>
          <button type="submit" disabled={busy}>
            Re-plan {node.name}
          </button>
        </form>
      )}
    </div>
  )
}
