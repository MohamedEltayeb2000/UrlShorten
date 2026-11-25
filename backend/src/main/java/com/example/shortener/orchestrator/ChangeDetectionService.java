package com.example.shortener.orchestrator;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.artifacts.ArtifactView;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ForbiddenApprovalException;
import com.example.shortener.governance.PolicyEngine;
import com.example.shortener.governance.PolicyOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Detects that an upstream output changed (a human edit or a rollback) and re-plans the downstream stages on its own
 * (001-FR-032). The changed stage keeps its new CURRENT artifact; everything downstream is marked STALE and re-run, so the
 * high-impact approval gates apply again. Running downstream stages are allowed to finish first (the run is paused).
 */
@Service
public class ChangeDetectionService {
    static final int MAX_CONTENT = 100_000;
    static final Duration WAIT = Duration.ofSeconds(15);

    public record EditRequest(String nodeId, String artifactType, String content, String comment) {}

    private final RunScheduler scheduler;
    private final RunControlService control;
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final Workflows workflows;
    private final PolicyEngine policy;

    public ChangeDetectionService(RunScheduler scheduler, RunControlService control, AgentRunRepository runs,
                                  RunNodeRepository nodes, ArtifactService artifacts, AuditService audit,
                                  Workflows workflows, PolicyEngine policy) {
        this.scheduler = scheduler;
        this.control = control;
        this.runs = runs;
        this.nodes = nodes;
        this.artifacts = artifacts;
        this.audit = audit;
        this.workflows = workflows;
        this.policy = policy;
    }

    /** Human edit of a stage's CURRENT artifact: stores a new version, then re-plans downstream stages. */
    public Artifact edit(long runId, EditRequest req, Actor actor) {
        if (actor.type() == ActorType.AGENT) throw new ForbiddenApprovalException("Agents cannot edit artifacts");
        if (req == null || blank(req.nodeId()) || blank(req.artifactType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EDIT", "nodeId and artifactType are required");
        }
        if (blank(req.comment())) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMENT_REQUIRED", "A comment explaining the edit is required");
        if (req.content() == null || req.content().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EDIT", "content is required");
        }
        if (req.content().length() > MAX_CONTENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_TOO_LONG", "content exceeds " + MAX_CONTENT + " characters");
        }
        if (policy.evaluateText(req.content()).stream().anyMatch(d -> d.outcome() == PolicyOutcome.DENY)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POLICY_DENIED", "The edited content violates policy and was not stored");
        }
        Artifact created = scheduler.locked(runId, () -> {
            AgentRun run = runs.findById(runId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
            if (workflows.get(run.getWorkflowName()).nodes().stream().noneMatch(n -> n.id().equals(req.nodeId()))) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "Unknown stage: " + req.nodeId());
            }
            Artifact prev = artifacts.current(runId, req.nodeId(), req.artifactType()).orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT, "NO_CURRENT_ARTIFACT", "Stage " + req.nodeId() + " has no current " + req.artifactType() + " artifact to edit"));
            ArtifactView pv = ArtifactView.of(prev);
            Artifact next = artifacts.create(runId, req.nodeId(), req.artifactType(), req.content(), actor.id(), pv.parentIds(), pv.inputRefs());
            audit.append(runId, actor.type().name(), actor.id(), "ARTIFACT_EDITED",
                    req.artifactType() + " of " + req.nodeId() + " edited to v" + next.getVersion() + ": " + req.comment().trim(), null);
            return next;
        });
        autoReplan(runId, req.nodeId(), "edit of " + req.artifactType() + " to v" + created.getVersion(), actor);
        return created;
    }

    /** Re-plans everything downstream of {@code changedNodeId} (not the node itself). No-op for a leaf stage. */
    public void autoReplan(long runId, String changedNodeId, String reason, Actor actor) {
        AgentRun first = runs.findById(runId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
        Set<String> closure = closureOf(first, changedNodeId);
        if (closure.isEmpty()) return;

        boolean paused = scheduler.locked(runId, () -> {
            AgentRun run = runs.findById(runId).orElseThrow();
            if (run.getStatus() == RunStatus.RUNNING && running(runId, closure)) {
                run.setStatus(RunStatus.PAUSED);
                runs.save(run);
                audit.append(runId, "SYSTEM", "scheduler", "RUN_PAUSED", "auto re-plan: waiting for running stages to finish", null);
                return true;
            }
            return false;
        });
        if (paused) {
            Instant end = Instant.now().plus(WAIT);
            try {
                while (Instant.now().isBefore(end) && scheduler.locked(runId, () -> running(runId, closure))) Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        scheduler.locked(runId, () -> {
            AgentRun run = runs.findById(runId).orElseThrow();
            if (running(runId, closure)) {
                audit.append(runId, "SYSTEM", "scheduler", "REPLAN_TIMEOUT",
                        "auto re-plan from " + changedNodeId + " timed out waiting for running stages; run left paused", null);
                throw new ApiException(HttpStatus.CONFLICT, "REPLAN_TIMEOUT",
                        "Downstream stages did not finish in time; the run is paused and was not re-planned");
            }
            control.applyReplan(run, nodes.findByRunIdOrderById(runId), closure, changedNodeId, null, actor, "AUTO_REPLAN",
                    "auto re-planned after " + reason + " by " + actor.id());
            return null;
        });
    }

    private Set<String> closureOf(AgentRun run, String nodeId) {
        Set<String> closure;
        try {
            closure = new java.util.LinkedHashSet<>(DownstreamClosure.of(workflows.get(run.getWorkflowName()), nodeId));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "Unknown stage: " + nodeId);
        }
        closure.remove(nodeId);
        return closure;
    }

    private boolean running(long runId, Set<String> closure) {
        return nodes.findByRunIdOrderById(runId).stream().anyMatch(n -> closure.contains(n.getNodeId())
                && (n.getStatus() == NodeStatus.RUNNING || n.getStatus() == NodeStatus.RETRYING));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
