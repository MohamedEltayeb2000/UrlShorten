import { useSearchParams } from 'react-router-dom'
import { getRunMetrics } from '../api/metrics'
import { listRuns } from '../api/runs'
import RunMetricsView from './RunMetricsView'
import { ErrorAlert, useLoaded } from './metricsUi'

export default function RunMetricsSection() {
  const [params, setParams] = useSearchParams()
  const runs = useLoaded(() => listRuns().then((p) => p.items), 'runs')
  const runId = Number(params.get('run')) || null
  const metrics = useLoaded(() => (runId ? getRunMetrics(runId) : Promise.resolve(null)), runId)

  return (
    <section aria-labelledby="run-metrics-heading">
      <h3 id="run-metrics-heading">Run metrics</h3>
      {runs.error && <ErrorAlert message={runs.error} />}
      {!runs.error && !runs.data && <p>Loading runs…</p>}
      {runs.data && runs.data.length === 0 && <p>No runs yet</p>}
      {runs.data && runs.data.length > 0 && (
        <>
          <label htmlFor="run-select">Run</label>
          <select
            id="run-select"
            value={runId ?? ''}
            onChange={(e) => setParams(e.target.value ? { run: e.target.value } : {})}
          >
            <option value="">Select a run</option>
            {runs.data.map((r) => (
              <option key={r.id} value={r.id}>
                #{r.id} {r.requirement.slice(0, 60)} ({r.status})
              </option>
            ))}
          </select>
        </>
      )}
      {runId && metrics.error && <ErrorAlert message={metrics.error} />}
      {runId && !metrics.error && !metrics.data && <p>Loading run metrics…</p>}
      {metrics.data && <RunMetricsView m={metrics.data} />}
    </section>
  )
}
