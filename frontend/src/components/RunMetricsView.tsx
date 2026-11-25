import { useState } from 'react'
import { RunMetrics, downloadSummary } from '../api/metrics'
import { ErrorAlert, Tile, dur } from './metricsUi'

export default function RunMetricsView({ m }: { m: RunMetrics }) {
  const [error, setError] = useState<string | null>(null)
  const max = Math.max(1, ...m.stageLatencies.map((s) => s.durationMs ?? 0))
  async function download() {
    setError(null)
    try {
      await downloadSummary(m.runId)
    } catch (e) {
      setError((e as Error).message)
    }
  }
  return (
    <div className="run-metrics">
      <p>
        Run #{m.runId} status: <span className={`status status-${m.status.toLowerCase()}`}>{m.status}</span>
      </p>
      <div className="tiles">
        <Tile label="End-to-end" value={dur(m.endToEndMs)} />
        <Tile label="Stages succeeded" value={`${m.stagesSucceeded} of ${m.stagesTotal}`} />
        <Tile label="Run retries" value={m.retries} />
        <Tile label="Run rollbacks" value={m.rollbacks} />
        <Tile label="Run fallbacks" value={m.fallbacks} />
        <Tile label="Run failed stages" value={m.failedStages} />
        <Tile label="Run blocked stages" value={m.blockedStages} />
        <Tile label="Waiting stages" value={m.waitingStages} />
        <Tile label="Run MTTR" value={dur(m.mttrMs)} />
        <Tile label="Approvals approved" value={m.approvals.approved} />
        <Tile label="Approvals rejected" value={m.approvals.rejected} />
        <Tile label="Approvals pending" value={m.approvals.pending} />
      </div>
      <table className="latency">
        <caption>Stage latency</caption>
        <thead>
          <tr>
            <th scope="col">Stage</th>
            <th scope="col">Attempts</th>
            <th scope="col">Duration</th>
          </tr>
        </thead>
        <tbody>
          {m.stageLatencies.map((s) => (
            <tr key={s.nodeId}>
              <th scope="row">{s.name}</th>
              <td>{s.attempts}</td>
              <td>
                {dur(s.durationMs)}{' '}
                <span aria-hidden="true" className="bar">
                  {'■'.repeat(Math.round(((s.durationMs ?? 0) / max) * 10))}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button type="button" onClick={() => void download()}>
        Download engineering summary
      </button>
      {error && <ErrorAlert message={error} />}
    </div>
  )
}
