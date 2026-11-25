package com.example.shortener.metrics;

import com.example.shortener.audit.AuditRepository;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Approval;
import com.example.shortener.governance.ApprovalRepository;
import com.example.shortener.metrics.MetricsDtos.*;
import com.example.shortener.orchestrator.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes metrics on demand from the audit trail, node rows and transition history (see {@link MetricsDtos}). */
@Service
@Transactional(readOnly = true)
public class MetricsService {
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final ApprovalRepository approvals;
    private final AuditRepository audit;
    private final MetricsQueries queries;

    public MetricsService(AgentRunRepository runs, RunNodeRepository nodes, ApprovalRepository approvals,
                          AuditRepository audit, MetricsQueries queries) {
        this.runs = runs;
        this.nodes = nodes;
        this.approvals = approvals;
        this.audit = audit;
        this.queries = queries;
    }

    public RunMetrics forRun(long runId) {
        AgentRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
        List<RunNode> ns = nodes.findByRunIdOrderById(runId);
        List<StageLatency> latencies = ns.stream()
                .map(n -> new StageLatency(n.getNodeId(), n.getName(), n.getAttemptCount(), durationMs(n.getStartedAt(), n.getCompletedAt())))
                .toList();
        List<Approval> as = approvals.findByRunIdOrderByIdAsc(runId);
        Approvals ap = new Approvals(count(as, Approval.Status.APPROVED), count(as, Approval.Status.REJECTED), count(as, Approval.Status.PENDING));
        long retries = audit.countByRunIdAndEventType(runId, "STAGE_RETRY") + audit.countByRunIdAndEventType(runId, "NODE_RETRY_REQUESTED");
        return new RunMetrics(runId, run.getStatus().name(), durationMs(run.getStartedAt(), run.getCompletedAt()),
                ns.size(), countNodes(ns, NodeStatus.SUCCEEDED),
                retries, audit.countByRunIdAndEventType(runId, "ARTIFACT_ROLLBACK"), audit.countByRunIdAndEventType(runId, "FALLBACK_USED"),
                countNodes(ns, NodeStatus.FAILED), countNodes(ns, NodeStatus.BLOCKED), countNodes(ns, NodeStatus.WAITING_FOR_APPROVAL),
                mttr(queries.recoveryTransitionsOfRun(runId)), ap, latencies);
    }

    public OverallMetrics overall() {
        List<AgentRun> all = queries.allRuns();
        long succeeded = all.stream().filter(r -> r.getStatus() == RunStatus.SUCCEEDED).count();
        long failed = all.stream().filter(r -> r.getStatus() == RunStatus.FAILED).count();
        long inProgress = all.stream().filter(r -> r.getStatus() != RunStatus.SUCCEEDED
                && r.getStatus() != RunStatus.FAILED && r.getStatus() != RunStatus.CANCELLED).count();
        Double rate = succeeded + failed == 0 ? null : (double) succeeded / (succeeded + failed);
        List<Long> e2e = all.stream().map(r -> durationMs(r.getStartedAt(), r.getCompletedAt())).filter(d -> d != null).toList();
        Long avg = e2e.isEmpty() ? null : Math.round(e2e.stream().mapToLong(Long::longValue).average().orElse(0));

        List<RunNode> ns = queries.allNodes();
        List<Long> stage = new ArrayList<>(ns.stream().map(n -> durationMs(n.getStartedAt(), n.getCompletedAt())).filter(d -> d != null).toList());
        stage.sort(null);
        Long p95 = stage.isEmpty() ? null : stage.get((int) Math.ceil(0.95 * stage.size()) - 1);

        return new OverallMetrics(all.size(), succeeded, failed, inProgress, rate,
                audit.countByEventType("STAGE_RETRY") + audit.countByEventType("NODE_RETRY_REQUESTED"),
                audit.countByEventType("ARTIFACT_ROLLBACK"), audit.countByEventType("FALLBACK_USED"),
                ns.stream().filter(n -> n.getStatus() == NodeStatus.FAILED).count(),
                ns.stream().filter(n -> n.getStatus() == NodeStatus.BLOCKED).count(),
                avg, p95, mttr(queries.allRecoveryTransitions()));
    }

    /** Mean incident duration; transitions must be ordered by node then id. An incident is open from the first RETRYING/FAILED/BLOCKED until SUCCEEDED. */
    static Long mttr(List<NodeTransition> transitions) {
        long total = 0;
        int incidents = 0;
        Long currentNode = null;
        Instant openedAt = null;
        for (NodeTransition t : transitions) {
            if (!t.getRunNodeId().equals(currentNode)) {
                currentNode = t.getRunNodeId();
                openedAt = null;
            }
            if (t.getToStatus() == NodeStatus.SUCCEEDED) {
                if (openedAt != null) {
                    total += Duration.between(openedAt, t.getOccurredAt()).toMillis();
                    incidents++;
                    openedAt = null;
                }
            } else if (openedAt == null) {
                openedAt = t.getOccurredAt();
            }
        }
        return incidents == 0 ? null : Math.round((double) total / incidents);
    }

    private static Long durationMs(Instant from, Instant to) {
        return from == null || to == null ? null : Duration.between(from, to).toMillis();
    }

    private static int count(List<Approval> as, Approval.Status s) {
        return (int) as.stream().filter(a -> a.getStatus() == s).count();
    }

    private static int countNodes(List<RunNode> ns, NodeStatus s) {
        return (int) ns.stream().filter(n -> n.getStatus() == s).count();
    }
}
