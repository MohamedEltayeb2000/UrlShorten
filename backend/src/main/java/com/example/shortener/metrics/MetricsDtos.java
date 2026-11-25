package com.example.shortener.metrics;

import java.util.List;

/**
 * Metric views (001-FR-020). Every number is derived from persisted audit events, node timestamps,
 * node transitions and approvals; nothing is stored separately.
 */
public final class MetricsDtos {
    private MetricsDtos() {}

    /**
     * Metrics of one run.
     * <ul>
     *   <li>{@code endToEndMs}: run completedAt - startedAt; null until the run completes.</li>
     *   <li>{@code retries}: audit events STAGE_RETRY (automatic) + NODE_RETRY_REQUESTED (manual).</li>
     *   <li>{@code rollbacks}: audit events ARTIFACT_ROLLBACK; {@code fallbacks}: audit events FALLBACK_USED.</li>
     *   <li>{@code failedStages} / {@code blockedStages} / {@code waitingStages}: nodes currently FAILED / BLOCKED / WAITING_FOR_APPROVAL.</li>
     *   <li>{@code mttrMs}: mean time to recovery. An incident starts when a node transitions into RETRYING, FAILED
     *       or BLOCKED and ends at that node's next transition into SUCCEEDED; the mean incident duration over the
     *       persisted transition history, null when no incident recovered.</li>
     * </ul>
     */
    public record RunMetrics(long runId, String status, Long endToEndMs, int stagesTotal, int stagesSucceeded,
                             long retries, long rollbacks, long fallbacks, int failedStages, int blockedStages,
                             int waitingStages, Long mttrMs, Approvals approvals, List<StageLatency> stageLatencies) {}

    public record Approvals(int approved, int rejected, int pending) {}

    /** {@code durationMs} = node completedAt - startedAt (null when either is missing); {@code attempts} = node attemptCount. */
    public record StageLatency(String nodeId, String name, int attempts, Long durationMs) {}

    /**
     * Metrics over all runs. {@code successRate} = runsSucceeded / (runsSucceeded + runsFailed), null when no run
     * finished; {@code runsInProgress} = runs not SUCCEEDED/FAILED/CANCELLED; {@code avgEndToEndMs} over completed runs;
     * {@code p95StageLatencyMs} = nearest-rank 95th percentile of all stage durations; {@code mttrMs} as in {@link RunMetrics}.
     */
    public record OverallMetrics(long runsTotal, long runsSucceeded, long runsFailed, long runsInProgress,
                                 Double successRate, long retries, long rollbacks, long fallbacks, long failedStages,
                                 long blockedStages, Long avgEndToEndMs, Long p95StageLatencyMs, Long mttrMs) {}
}
