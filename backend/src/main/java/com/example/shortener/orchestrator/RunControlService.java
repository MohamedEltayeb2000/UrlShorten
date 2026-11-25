package com.example.shortener.orchestrator;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.Approval;
import com.example.shortener.governance.ApprovalRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Operator actions on a run: retry a stage, re-plan from a changed input, roll back an artifact, and apply an approval
 * decision to a waiting stage. Every action runs under the run's scheduling lock and is written to the audit trail.
 */
@Service
public class RunControlService {
    private final RunScheduler scheduler;
    private final RunService runService;
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final RunTransitions transitions;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final ApprovalRepository approvals;
    private final Workflows workflows;
    private final ChangeDetectionService changeDetection;

    public RunControlService(RunScheduler scheduler, RunService runService, AgentRunRepository runs, RunNodeRepository nodes,
                             RunTransitions transitions, ArtifactService artifacts, AuditService audit,
                             ApprovalRepository approvals, Workflows workflows,
                             @org.springframework.context.annotation.Lazy ChangeDetectionService changeDetection) {
        this.changeDetection = changeDetection;
        this.scheduler = scheduler;
        this.runService = runService;
        this.runs = runs;
        this.nodes = nodes;
        this.transitions = transitions;
        this.artifacts = artifacts;
        this.audit = audit;
        this.approvals = approvals;
        this.workflows = workflows;
    }

    /** Re-queues a FAILED or BLOCKED stage (and the stages that were skipped because of it). */
    public RunDtos.RunDetail retry(long runId, String nodeId, Actor actor) {
        scheduler.locked(runId, () -> {
            AgentRun run = run(runId);
            RunNode n = node(runId, nodeId);
            if (n.getStatus() != NodeStatus.FAILED && n.getStatus() != NodeStatus.BLOCKED) {
                throw new ApiException(HttpStatus.CONFLICT, "NODE_NOT_RETRYABLE", "Stage " + nodeId + " is " + n.getStatus() + " and cannot be retried");
            }
            if (run.getStatus() == RunStatus.RUNNING && nodes.findByRunIdOrderById(runId).stream()
                    .anyMatch(x -> x.getStatus() == NodeStatus.RUNNING || x.getStatus() == NodeStatus.RETRYING)) {
                // allowed: other stages may keep running while this one is re-queued
            }
            requeue(n, "manual retry by " + actor.id());
            for (RunNode other : nodes.findByRunIdOrderById(runId)) {
                if (other.getStatus() == NodeStatus.SKIPPED) requeue(other, "re-queued after retry of " + nodeId);
            }
            supersedePending(runId, Set.of(nodeId), "stage was retried");
            audit.append(runId, actor.type().name(), actor.id(), "NODE_RETRY_REQUESTED", nodeId, null);
            resume(run);
            scheduler.kickLocked(runId);
            return null;
        });
        return runService.detail(runId);
    }

    /**
     * Re-plans from a changed input: the stage and everything downstream is marked stale and re-queued (a new plan version);
     * stages outside that set keep their results and are not re-run.
     */
    public RunDtos.RunDetail replan(long runId, String nodeId, String newRequirement, Actor actor) {
        scheduler.locked(runId, () -> {
            AgentRun run = run(runId);
            WorkflowDefinition def = workflows.get(run.getWorkflowName());
            Set<String> closure;
            try {
                closure = DownstreamClosure.of(def, nodeId);
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "Unknown stage: " + nodeId);
            }
            List<RunNode> all = nodes.findByRunIdOrderById(runId);
            for (RunNode n : all) {
                boolean active = n.getStatus() == NodeStatus.RUNNING || n.getStatus() == NodeStatus.RETRYING || n.getStatus() == NodeStatus.READY;
                if (closure.contains(n.getNodeId()) && active) {
                    throw new ApiException(HttpStatus.CONFLICT, "REPLAN_REQUIRES_PAUSE",
                            "Stage " + n.getNodeId() + " is " + n.getStatus() + "; pause the run and let running stages finish first");
                }
            }
            applyReplan(run, all, closure, nodeId, newRequirement, actor, "RUN_REPLANNED",
                    "re-planned from " + nodeId + " by " + actor.id());
            return null;
        });
        return runService.detail(runId);
    }

    /**
     * Shared re-plan internals (run lock held): marks the closure's CURRENT artifacts stale, re-queues its nodes,
     * supersedes pending approvals, bumps the plan version, audits under {@code auditType} and resumes scheduling.
     */
    void applyReplan(AgentRun run, List<RunNode> all, Set<String> closure, String origin, String newRequirement,
                     Actor actor, String auditType, String requeueReason) {
        long runId = run.getId();
        if (newRequirement != null && !newRequirement.isBlank()) {
            String req = RunService.validateRequirement(newRequirement);
            run.setRequirement(req, req.replaceAll("\\s+", " "));
        }
        List<Long> staleIds = new ArrayList<>();
        for (String id : closure) {
            String type = AgentStageHandler.ARTIFACT_TYPES.get(id);
            if (type != null) artifacts.current(runId, id, type).ifPresent(a -> staleIds.add(a.getId()));
        }
        artifacts.markStale(staleIds);
        for (RunNode n : all) {
            if (closure.contains(n.getNodeId()) && n.getStatus() != NodeStatus.PENDING) requeue(n, requeueReason);
        }
        supersedePending(runId, closure, "stage was re-planned");
        run.setPlanVersion(run.getPlanVersion() + 1);
        audit.append(runId, actor.type().name(), actor.id(), auditType,
                "plan v" + run.getPlanVersion() + " from " + origin + "; affected: " + closure + "; stale artifacts: " + staleIds.size(), null);
        if (run.getStatus() != RunStatus.CREATED) resume(run); else runs.save(run);
        if (run.getStatus() == RunStatus.RUNNING) scheduler.kickLocked(runId);
    }

    /**
     * Restores a prior version of an artifact; nothing is deleted. When the restored version changes the stage's CURRENT
     * artifact, downstream stages are re-planned automatically (001-FR-032).
     */
    public Artifact rollback(long runId, String nodeId, String type, int version, Actor actor) {
        boolean[] changed = {false};
        Artifact restored = scheduler.locked(runId, () -> {
            run(runId);
            Artifact before = artifacts.current(runId, nodeId, type).orElse(null);
            Artifact r = artifacts.restore(runId, nodeId, type, version, actor.id());
            changed[0] = before == null || !before.getId().equals(r.getId());
            audit.append(runId, actor.type().name(), actor.id(), "ARTIFACT_ROLLBACK",
                    type + " of " + nodeId + " restored to v" + version, null);
            return r;
        });
        if (changed[0]) changeDetection.autoReplan(runId, nodeId, "rollback of " + type + " to v" + version, actor);
        return restored;
    }

    /** Applies an approval decision to a stage waiting for one. */
    public void resolveWaiting(long runId, String nodeId, boolean approved, String feedback, Actor actor) {
        scheduler.locked(runId, () -> {
            AgentRun run = run(runId);
            RunNode n = node(runId, nodeId);
            if (n.getStatus() != NodeStatus.WAITING_FOR_APPROVAL) {
                throw new ApiException(HttpStatus.CONFLICT, "NODE_NOT_WAITING", "Stage " + nodeId + " is " + n.getStatus() + ", not waiting for approval");
            }
            if (approved) {
                transitions.move(n.getId(), NodeStatus.SUCCEEDED, "approved by " + actor.id(), x -> x.setCompletedAt(Instant.now()));
                if (run.getStatus() == RunStatus.WAITING_FOR_APPROVAL) {
                    run.setStatus(RunStatus.RUNNING);
                    runs.save(run);
                }
                scheduler.kickLocked(runId);
            } else {
                transitions.move(n.getId(), NodeStatus.BLOCKED, "rejected by " + actor.id(), x -> {
                    x.setErrorCode("REJECTED");
                    x.setErrorSummary(("Rejected by " + actor.id() + ": " + feedback));
                });
            }
            return null;
        });
    }

    // ---- helpers (run lock held) --------------------------------------------------------------------------

    private void requeue(RunNode n, String reason) {
        transitions.move(n.getId(), NodeStatus.PENDING, reason, x -> {
            x.setErrorCode(null);
            x.setErrorSummary(null);
            x.setCompletedAt(null);
        });
    }

    private void resume(AgentRun run) {
        if (run.getStatus() == RunStatus.FAILED || run.getStatus() == RunStatus.SUCCEEDED
                || run.getStatus() == RunStatus.WAITING_FOR_APPROVAL || run.getStatus() == RunStatus.PAUSED) {
            run.setStatus(RunStatus.RUNNING);
            run.setCompletedAt(null);
        }
        runs.save(run);
    }

    private void supersedePending(long runId, Set<String> nodeIds, String reason) {
        for (Approval a : approvals.findByRunIdOrderByIdAsc(runId)) {
            if (a.getStatus() == Approval.Status.PENDING && nodeIds.contains(a.getNodeId())) {
                a.supersede(reason);
                approvals.save(a);
            }
        }
    }

    private AgentRun run(long runId) {
        return runs.findById(runId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
    }

    private RunNode node(long runId, String nodeId) {
        return nodes.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "Unknown stage: " + nodeId));
    }
}
