import { Link } from 'react-router-dom'
import type { RunDetail } from '../api/runs'
import StageList from './StageList'

interface Props {
  run: RunDetail
  busy: boolean
  onStart: () => void
  onPause: () => void
  onRetry?: (nodeId: string) => void
  onReplan?: (nodeId: string, requirement?: string) => void
}

export default function RunDetailPanel({ run, busy, onStart, onPause, onRetry, onReplan }: Props) {
  const canStart = run.status === 'CREATED' || run.status === 'PAUSED'
  const canPause = run.status === 'RUNNING'
  return (
    <section aria-labelledby="run-detail-heading" className="run-detail">
      <h3 id="run-detail-heading">Run #{run.id}</h3>
      <p>{run.requirement}</p>
      <p>
        Status: <span className={`status status-${run.status.toLowerCase()}`}>{run.status}</span> · Plan version:{' '}
        {run.planVersion}
      </p>
      <div className="actions">
        <button type="button" onClick={onStart} disabled={!canStart || busy}>
          Start
        </button>
        <button type="button" onClick={onPause} disabled={!canPause || busy}>
          Pause
        </button>
      </div>
      <StageList nodes={run.nodes} busy={busy} onRetry={onRetry} onReplan={onReplan} />
    </section>
  )
}
