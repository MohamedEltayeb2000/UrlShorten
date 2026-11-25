import { getOverallMetrics } from '../api/metrics'
import { ErrorAlert, Tile, dur, pct, useLoaded } from './metricsUi'

export default function OverallMetricsSection() {
  const { data: m, error } = useLoaded(getOverallMetrics, 'overall')
  return (
    <section aria-labelledby="overall-heading">
      <h3 id="overall-heading">Overall</h3>
      {error && <ErrorAlert message={error} />}
      {!error && !m && <p>Loading metrics…</p>}
      {m && m.runsTotal === 0 && <p>No runs yet, so there are no metrics to show</p>}
      {m && m.runsTotal > 0 && (
        <div className="tiles">
          <Tile label="Total runs" value={m.runsTotal} />
          <Tile label="Succeeded" value={m.runsSucceeded} />
          <Tile label="Failed" value={m.runsFailed} />
          <Tile label="In progress" value={m.runsInProgress} />
          <Tile label="Success rate" value={pct(m.successRate)} />
          <Tile label="Retries" value={m.retries} />
          <Tile label="Rollbacks" value={m.rollbacks} />
          <Tile label="Fallbacks" value={m.fallbacks} />
          <Tile label="Failed stages" value={m.failedStages} />
          <Tile label="Blocked stages" value={m.blockedStages} />
          <Tile label="Average end-to-end" value={dur(m.avgEndToEndMs)} />
          <Tile label="p95 stage latency" value={dur(m.p95StageLatencyMs)} />
          <Tile label="MTTR" value={dur(m.mttrMs)} />
        </div>
      )}
    </section>
  )
}
