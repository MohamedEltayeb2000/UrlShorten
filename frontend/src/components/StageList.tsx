import { Link } from 'react-router-dom'
import type { RunNode } from '../api/runs'
import StageActions from './StageActions'

interface Props {
  nodes: RunNode[]
  busy?: boolean
  onRetry?: (nodeId: string) => void
  onReplan?: (nodeId: string, requirement?: string) => void
}

export default function StageList({ nodes, busy = false, onRetry, onReplan }: Props) {
  if (nodes.length === 0) return <p>No stages in this run.</p>
  const levels = [...new Set(nodes.map((n) => n.level))].sort((a, b) => a - b)
  const names = new Map(nodes.map((n) => [n.id, n.name]))
  return (
    <ol className="stage-levels" aria-label="Stages">
      {levels.map((level) => {
        const group = nodes.filter((n) => n.level === level)
        return (
          <li key={level} className="stage-level">
            <h4>
              Level {level + 1}
              {group.length > 1 && <span className="parallel"> - Parallel stages</span>}
            </h4>
            <ul>
              {group.map((n) => (
                <li key={n.id} className="stage">
                  <strong>{n.name}</strong>{' '}
                  <span className={`status status-${n.status.toLowerCase()}`}>{n.status}</span>
                  {n.status === 'WAITING_FOR_APPROVAL' && (
                    <>
                      {' '}
                      <Link to="/approvals">Review in Approvals</Link>
                    </>
                  )}
                  {n.dependsOn.length > 0 && (
                    <div>after: {n.dependsOn.map((d) => names.get(d) ?? d).join(', ')}</div>
                  )}
                  <div>Attempts: {n.attempts}</div>
                  {n.errorSummary && <div className="error">Error: {n.errorSummary}</div>}
                  {onRetry && onReplan && <StageActions node={n} busy={busy} onRetry={onRetry} onReplan={onReplan} />}
                </li>
              ))}
            </ul>
          </li>
        )
      })}
    </ol>
  )
}
