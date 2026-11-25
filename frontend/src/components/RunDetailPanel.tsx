import { useState } from 'react'
import { Link } from 'react-router-dom'
import { downloadSummary } from '../api/metrics'
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
  const [exportError, setExportError] = useState<string | null>(null)
  async function exportSummary() {
    setExportError(null)
    try {
      await downloadSummary(run.id)
    } catch (e) {
      setExportError((e as Error).message)
    }
  }
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
        <Link to={`/metrics?run=${run.id}`}>View metrics</Link>
        <button type="button" onClick={() => void exportSummary()}>
          Download engineering summary
        </button>
      </div>
      {exportError && (
        <p role="alert" className="error">
          {exportError}
        </p>
      )}
      <StageList nodes={run.nodes} busy={busy} onRetry={onRetry} onReplan={onReplan} />
    </section>
  )
}
