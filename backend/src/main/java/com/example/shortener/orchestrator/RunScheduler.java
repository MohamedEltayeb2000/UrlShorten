package com.example.shortener.orchestrator;

import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Approval;
import com.example.shortener.governance.ApprovalRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Dependency-driven scheduler. A node becomes READY only when every prerequisite SUCCEEDED, ready nodes run concurrently
 * up to {@code max-parallel}, and every state change is persisted before and after execution so progress is observable
 * and recoverable. Transient failures are retried with bounded attempts and backoff, then fall back to a deterministic
 * simulated provider; validation and policy failures are never retried. A stage that needs a human decision waits in
 * WAITING_FOR_APPROVAL. Pausing stops new scheduling (running stages finish); a restart marks interrupted stages BLOCKED
 * and pauses the run until an operator resumes it.
 */
@Service
public class RunScheduler {
    private static final Logger log = LoggerFactory.getLogger(RunScheduler.class);
    static final String INTERRUPTED = "INTERRUPTED";
    private static final Set<NodeStatus> FAILED_LIKE = EnumSet.of(NodeStatus.FAILED, NodeStatus.CANCELLED, NodeStatus.SKIPPED);

    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final RunTransitions transitions;
    private final StageHandler handler;
    private final StageAttemptRepository attempts;
    private final AuditService audit;
    private final ApprovalRepository approvals;
    private final RetryPolicy retryPolicy;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();

    public RunScheduler(AgentRunRepository runs, RunNodeRepository nodes, RunTransitions transitions, StageHandler handler,
                        StageAttemptRepository attempts, AuditService audit, ApprovalRepository approvals, RetryPolicy retryPolicy,
                        @Value("${shortener.orchestrator.max-parallel:4}") int maxParallel) {
        this.runs = runs;
        this.nodes = nodes;
        this.transitions = transitions;
        this.handler = handler;
        this.attempts = attempts;
        this.audit = audit;
        this.approvals = approvals;
        this.retryPolicy = retryPolicy;
        this.permits = new Semaphore(Math.max(1, maxParallel));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private Object lock(long runId) {
        return locks.computeIfAbsent(runId, k -> new Object());
    }

    /** Runs an action while holding the run's scheduling lock (used by run control operations). */
    public <T> T locked(long runId, Supplier<T> action) {
        synchronized (lock(runId)) {
            return action.get();
        }
    }

    /** Re-evaluates the run and starts whatever became ready. Call while holding the run lock (see {@link #locked}). */
    public void kickLocked(long runId) {
        for (RunNode n : nodes.findByRunIdOrderById(runId)) {
            if (n.getStatus() == NodeStatus.READY && load(runId).getStatus() == RunStatus.RUNNING) submit(runId, n.getId());
        }
        scheduleLocked(runId);
    }

    /** Starts a CREATED run or resumes a PAUSED one (idempotent while RUNNING). */
    public void start(long runId) {
        synchronized (lock(runId)) {
            AgentRun run = load(runId);
            if (run.getStatus() == RunStatus.RUNNING) return;
            if (run.getStatus() != RunStatus.CREATED && run.getStatus() != RunStatus.PAUSED) {
                throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_STARTABLE", "Run is " + run.getStatus() + " and cannot be started");
            }
            for (RunNode n : nodes.findByRunIdOrderById(runId)) {
                if (n.getStatus() == NodeStatus.BLOCKED && INTERRUPTED.equals(n.getErrorCode())) {
                    transitions.move(n.getId(), NodeStatus.READY, "resumed by operator after interruption", x -> {
                        x.setErrorCode(null);
                        x.setErrorSummary(null);
                    });
                }
            }
            run.setStatus(RunStatus.RUNNING);
            if (run.getStartedAt() == null) run.setStartedAt(Instant.now());
            runs.save(run);
            audit.append(runId, "SYSTEM", "scheduler", "RUN_STARTED", "run started or resumed", null);
            kickLocked(runId);
        }
    }

    /** Safe-stop: no new stages are scheduled; stages already running finish. */
    public void pause(long runId) {
        synchronized (lock(runId)) {
            AgentRun run = load(runId);
            if (run.getStatus() == RunStatus.PAUSED) return;
            if (run.getStatus() != RunStatus.RUNNING) {
                throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_PAUSABLE", "Run is " + run.getStatus() + " and cannot be paused");
            }
            run.setStatus(RunStatus.PAUSED);
            runs.save(run);
            audit.append(runId, "SYSTEM", "scheduler", "RUN_PAUSED", "safe-stop requested: no new stages will start", null);
        }
    }

    /** After a restart no stage is really running: mark interrupted stages BLOCKED and pause the run for an operator. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        for (AgentRun run : runs.findAll()) {
            if (run.getStatus() != RunStatus.RUNNING) continue;
            synchronized (lock(run.getId())) {
                for (RunNode n : nodes.findByRunIdOrderById(run.getId())) {
                    if (n.getStatus() == NodeStatus.RUNNING || n.getStatus() == NodeStatus.RETRYING) {
                        transitions.move(n.getId(), NodeStatus.BLOCKED, "interrupted by restart: operator action required", x -> {
                            x.setErrorCode(INTERRUPTED);
                            x.setErrorSummary("Execution was interrupted by an application restart");
                        });
                    }
                }
                run.setStatus(RunStatus.PAUSED);
                runs.save(run);
                log.warn("Run {} was RUNNING at startup; paused for operator action", run.getId());
            }
        }
    }

    // ---- internals (call with the run lock held) ------------------------------------------------------------

    private void scheduleLocked(long runId) {
        AgentRun run = load(runId);
        if (run.getStatus() != RunStatus.RUNNING) return;
        boolean changed;
        do {
            changed = false;
            List<RunNode> all = nodes.findByRunIdOrderById(runId);
            Map<String, NodeStatus> status = all.stream().collect(Collectors.toMap(RunNode::getNodeId, RunNode::getStatus));
            for (RunNode n : all) {
                if (n.getStatus() != NodeStatus.PENDING) continue;
                List<String> deps = n.dependsOnIds();
                if (deps.stream().anyMatch(d -> FAILED_LIKE.contains(status.get(d)))) {
                    transitions.move(n.getId(), NodeStatus.SKIPPED, "a prerequisite did not succeed", null);
                    changed = true;
                } else if (deps.stream().allMatch(d -> status.get(d) == NodeStatus.SUCCEEDED)) {
                    transitions.move(n.getId(), NodeStatus.READY, "all prerequisites succeeded", null);
                    submit(runId, n.getId());
                    changed = true;
                }
            }
        } while (changed);

        List<RunNode> after = nodes.findByRunIdOrderById(runId);
        if (after.stream().allMatch(n -> n.getStatus().isTerminal())) {
            boolean failed = after.stream().anyMatch(n -> n.getStatus() == NodeStatus.FAILED);
            run.setStatus(failed ? RunStatus.FAILED : RunStatus.SUCCEEDED);
            run.setCompletedAt(Instant.now());
            runs.save(run);
            audit.append(runId, "SYSTEM", "scheduler", "RUN_" + run.getStatus().name(), "all stages reached a terminal state", null);
        } else if (after.stream().noneMatch(n -> n.getStatus() == NodeStatus.RUNNING || n.getStatus() == NodeStatus.READY
                || n.getStatus() == NodeStatus.RETRYING)
                && after.stream().anyMatch(n -> n.getStatus() == NodeStatus.WAITING_FOR_APPROVAL || n.getStatus() == NodeStatus.BLOCKED)) {
            run.setStatus(RunStatus.WAITING_FOR_APPROVAL);
            runs.save(run);
            audit.append(runId, "SYSTEM", "scheduler", "RUN_WAITING_FOR_APPROVAL", "a human decision is required to continue", null);
        }
    }

    private void submit(long runId, long nodeDbId) {
        if (!queued.add(nodeDbId)) return;
        executor.submit(() -> runNode(runId, nodeDbId));
    }

    private record Outcome(Exception failure, AwaitingDecisionException awaiting, HeldForReviewException held) {
        static Outcome ok() { return new Outcome(null, null, null); }
        boolean ok2() { return failure == null && awaiting == null && held == null; }
    }

    private Outcome invoke(StageExecution ex, boolean fallback) {
        try {
            if (fallback) handler.executeFallback(ex);
            else handler.execute(ex);
            return Outcome.ok();
        } catch (AwaitingDecisionException e) {
            return new Outcome(null, e, null);
        } catch (HeldForReviewException e) {
            return new Outcome(null, null, e);
        } catch (Exception e) {
            return new Outcome(e, null, null);
        }
    }

    private void runNode(long runId, long nodeDbId) {
        boolean ran = false;
        try {
            permits.acquire();
            try {
                String nodeId;
                int attempt;
                synchronized (lock(runId)) {
                    if (load(runId).getStatus() != RunStatus.RUNNING) return; // paused: stays READY until resumed
                    RunNode started = transitions.move(nodeDbId, NodeStatus.RUNNING, "started", n -> {
                        n.setAttemptCount(n.getAttemptCount() + 1);
                        n.setStartedAt(Instant.now());
                    });
                    nodeId = started.getNodeId();
                    attempt = started.getAttemptCount();
                }
                ran = true;
                StageAttempt record = attempts.save(new StageAttempt(nodeDbId, attempt, "RUNNING", Instant.now()));
                Outcome out = invoke(execution(runId, nodeId, attempt), false);

                // Bounded retry of transient failures with backoff; validation/policy failures never retry.
                while (out.failure() != null && retryPolicy.shouldRetry(attempt, out.failure())) {
                    long waitMs = retryPolicy.backoff(attempt).toMillis();
                    finish(record, "RETRYING", summarize(out.failure()), false);
                    synchronized (lock(runId)) {
                        transitions.move(nodeDbId, NodeStatus.RETRYING,
                                "transient failure on attempt " + attempt + "/" + retryPolicy.maxAttempts() + ", retrying in " + waitMs + " ms", null);
                    }
                    audit.append(runId, "SYSTEM", "scheduler", "STAGE_RETRY", nodeId + ": " + summarize(out.failure()) + " (waiting " + waitMs + " ms)", null);
                    Thread.sleep(waitMs);
                    synchronized (lock(runId)) {
                        RunNode again = transitions.move(nodeDbId, NodeStatus.RUNNING, "retry", n -> n.setAttemptCount(n.getAttemptCount() + 1));
                        attempt = again.getAttemptCount();
                    }
                    record = attempts.save(new StageAttempt(nodeDbId, attempt, "RUNNING", Instant.now()));
                    out = invoke(execution(runId, nodeId, attempt), false);
                }

                // Provider kept failing transiently: use the deterministic simulated fallback (clearly labeled).
                boolean fallbackUsed = false;
                if (out.failure() != null && isTransient(out.failure()) && handler.supportsFallback()) {
                    audit.append(runId, "SYSTEM", "scheduler", "FALLBACK_USED", nodeId + ": retries exhausted, using the simulated fallback", null);
                    fallbackUsed = true;
                    out = invoke(execution(runId, nodeId, attempt), true);
                }
                apply(runId, nodeDbId, nodeId, out, record, fallbackUsed);
            } finally {
                permits.release();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Scheduler error for node {} of run {}", nodeDbId, runId, e);
        } finally {
            queued.remove(nodeDbId);
        }
        if (ran) {
            synchronized (lock(runId)) {
                scheduleLocked(runId);
            }
        }
    }

    private StageExecution execution(long runId, String nodeId, int attempt) {
        return new StageExecution(runId, nodeId, attempt,
                () -> runs.findById(runId).map(r -> r.getStatus() != RunStatus.RUNNING).orElse(true));
    }

    private void apply(long runId, long nodeDbId, String nodeId, Outcome out, StageAttempt record, boolean fallbackUsed) {
        if (out.ok2()) {
            synchronized (lock(runId)) {
                transitions.move(nodeDbId, NodeStatus.SUCCEEDED, fallbackUsed ? "completed with simulated fallback" : "completed",
                        n -> n.setCompletedAt(Instant.now()));
            }
            finish(record, "SUCCEEDED", null, fallbackUsed);
        } else if (out.awaiting() != null) {
            AwaitingDecisionException e = out.awaiting();
            synchronized (lock(runId)) {
                transitions.move(nodeDbId, NodeStatus.WAITING_FOR_APPROVAL, "waiting for a human decision: " + e.getMessage(), null);
                approvals.save(new Approval(runId, nodeId, e.kind(), e.policyReason() == null ? e.getMessage() : e.policyReason(),
                        e.artifactVersion()));
            }
            audit.append(runId, "SYSTEM", "scheduler", "APPROVAL_REQUESTED", nodeId + ": " + e.kind() + " - " + e.getMessage(), null);
            finish(record, "WAITING_FOR_APPROVAL", null, fallbackUsed);
        } else if (out.held() != null) {
            HeldForReviewException e = out.held();
            synchronized (lock(runId)) {
                transitions.move(nodeDbId, NodeStatus.BLOCKED, "output held for review", n -> {
                    n.setErrorCode("HELD");
                    n.setErrorSummary(summarize(e));
                });
            }
            finish(record, "HELD", summarize(e), fallbackUsed);
        } else {
            Exception e = out.failure();
            synchronized (lock(runId)) {
                transitions.move(nodeDbId, NodeStatus.FAILED, "failed: " + e.getClass().getSimpleName(), n -> {
                    n.setCompletedAt(Instant.now());
                    n.setErrorCode(e.getClass().getSimpleName());
                    n.setErrorSummary(summarize(e));
                });
            }
            finish(record, "FAILED", summarize(e), fallbackUsed);
        }
    }

    private static boolean isTransient(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) if (c instanceof TransientFailureException) return true;
        return false;
    }

    private void finish(StageAttempt record, String status, String error, boolean fallbackUsed) {
        String provider = fallbackUsed ? handler.providerName() + "-fallback" : handler.providerName();
        record.finish(status, Instant.now(), provider, fallbackUsed, error);
        attempts.save(record);
    }

    private AgentRun load(long runId) {
        return runs.findById(runId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
    }

    private static String summarize(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage().replaceAll("\\p{Cntrl}", " ");
        String s = e.getClass().getSimpleName() + (m.isBlank() ? "" : ": " + m);
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
