import { Link } from 'react-router-dom'
import type { Gate, RunNode } from '../api/runs'
import StageActions from './StageActions'

interface Props {
  nodes: RunNode[]
  busy?: boolean
  onRetry?: (nodeId: string) => void
  onReplan?: (nodeId: string, requirement?: string) => void
}

// The outcome is always written as text (never color alone); the checks are available as a tooltip.
function GateLine({ label, gate }: { label: string; gate: Gate }) {
  const outcome = gate.passed === undefined || gate.passed === null ? 'not evaluated' : gate.passed ? 'passed' : 'failed'
  return (
    <span title={gate.checks.join('; ')}>
      {label}: {outcome}
    </span>
  )
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
                  {n.gates && (
                    <ul className="gates" aria-label={`Gates for ${n.name}`}>
                      <li>
                        <GateLine label="Entry gate" gate={n.gates.entry} />
                      </li>
                      <li>
                        <GateLine label="Exit gate" gate={n.gates.exit} />
                      </li>
                    </ul>
                  )}
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
