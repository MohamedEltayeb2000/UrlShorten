package com.example.shortener.metrics;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.metrics.MetricsDtos.OverallMetrics;
import com.example.shortener.metrics.MetricsDtos.RunMetrics;
import com.example.shortener.orchestrator.*;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Metrics must equal what the audit trail, node rows and attempts record. */
@SpringBootTest(properties = "shortener.orchestrator.retry.base-ms=10")
@ActiveProfiles("test")
@Import(MetricsServiceTest.Cfg.class)
@Requirement("001-FR-020")
class MetricsServiceTest {

    static final Set<Long> FLAKY_RUNS = ConcurrentHashMap.newKeySet();
    static final Set<Long> FAILING_RUNS = ConcurrentHashMap.newKeySet();
    static final ConcurrentHashMap<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        StageHandler scriptedMetricsHandler() {
            return ex -> {
                int n = CALLS.computeIfAbsent(ex.runId() + ":" + ex.nodeId(), k -> new AtomicInteger()).incrementAndGet();
                if (ex.nodeId().equals("REQUIREMENTS_ANALYSIS") && FLAKY_RUNS.contains(ex.runId()) && n <= 2) {
                    Thread.sleep(5);
                    throw new TransientFailureException("flaky " + n);
                }
                if (ex.nodeId().equals("TASK_DECOMPOSITION") && FAILING_RUNS.contains(ex.runId())) {
                    throw new ValidationFailureException("input is invalid");
                }
            };
        }
    }

    @Autowired RunService runService;
    @Autowired RunControlService control;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired RunNodeRepository nodes;
    @Autowired StageAttemptRepository attempts;
    @Autowired MetricsService metrics;

    private long runToEnd(String name, boolean flaky, boolean failing) throws Exception {
        long id = runService.create(name, null).id();
        if (flaky) FLAKY_RUNS.add(id);
        if (failing) FAILING_RUNS.add(id);
        runService.start(id);
        await(() -> terminal(runService.detail(id).status()), Duration.ofSeconds(30), "run to finish");
        return id;
    }

    private static long auditCount(List<AuditEvent> events, String type) {
        return events.stream().filter(e -> e.getEventType().equals(type)).count();
    }

    private List<AuditEvent> events(long runId) {
        return audit.events(runId, 0, 100).items();
    }

    @Test
    void cleanRunReportsFullSuccessWithoutRetriesOrMttr() throws Exception {
        long id = runToEnd("Metrics clean run", false, false);
        RunMetrics m = metrics.forRun(id);

        assertEquals("SUCCEEDED", m.status());
        assertEquals(8, m.stagesTotal());
        assertEquals(8, m.stagesSucceeded());
        assertEquals(0, m.retries());
        assertEquals(0, m.failedStages());
        assertEquals(0, m.blockedStages());
        assertEquals(0, m.waitingStages());
        assertNull(m.mttrMs());
        assertNotNull(m.endToEndMs());
        assertTrue(m.endToEndMs() > 0);
        assertEquals(8, m.stageLatencies().size());
        assertTrue(m.stageLatencies().stream().allMatch(s -> s.durationMs() != null && s.attempts() == 1));
        assertEquals(auditCount(events(id), "STAGE_RETRY"), m.retries());
    }

    @Test
    void transientFailuresAreCountedAsRetriesAndProduceMttr() throws Exception {
        long id = runToEnd("Metrics flaky run", true, false);
        RunMetrics m = metrics.forRun(id);
        List<AuditEvent> events = events(id);

        assertEquals("SUCCEEDED", m.status());
        assertEquals(2, auditCount(events, "STAGE_RETRY"));
        assertEquals(2, m.retries());

        RunNode ra = nodes.findByRunIdAndNodeId(id, "REQUIREMENTS_ANALYSIS").orElseThrow();
        assertEquals(3, ra.getAttemptCount());
        assertEquals(3, attempts.findByRunNodeIdOrderByAttemptAsc(ra.getId()).size());
        var latency = m.stageLatencies().stream().filter(s -> s.nodeId().equals("REQUIREMENTS_ANALYSIS")).findFirst().orElseThrow();
        assertEquals(3, latency.attempts());
        assertEquals(java.time.Duration.between(ra.getStartedAt(), ra.getCompletedAt()).toMillis(), latency.durationMs());

        assertNotNull(m.mttrMs());
        assertTrue(m.mttrMs() > 0);
        assertEquals(8, m.stagesSucceeded());
    }

    @Test
    void nonTransientFailureFailsTheRunAndIsCounted() throws Exception {
        long id = runToEnd("Metrics failing run", false, true);
        RunMetrics m = metrics.forRun(id);

        assertEquals("FAILED", m.status());
        assertEquals(1, m.failedStages());
        assertEquals(1, nodes.findByRunIdOrderById(id).stream().filter(n -> n.getStatus() == NodeStatus.FAILED).count());
        assertEquals(0, m.retries());
        assertEquals(auditCount(events(id), "STAGE_RETRY"), m.retries());
        assertTrue(m.stagesSucceeded() < m.stagesTotal());
        assertNull(m.mttrMs());
    }

    @Test
    void rollbackIsCountedFromTheAuditTrail() throws Exception {
        long id = runToEnd("Metrics rollback run", false, false);
        artifacts.create(id, "REQUIREMENTS_ANALYSIS", "SPEC", "first", "agent:a", List.of(), List.of());
        artifacts.create(id, "REQUIREMENTS_ANALYSIS", "SPEC", "second", "agent:a", List.of(), List.of());
        control.rollback(id, "REQUIREMENTS_ANALYSIS", "SPEC", 1, new Actor("tester", ActorType.HUMAN));

        RunMetrics m = metrics.forRun(id);
        assertEquals(1, auditCount(events(id), "ARTIFACT_ROLLBACK"));
        assertEquals(1, m.rollbacks());
        assertEquals(0, m.fallbacks());
    }

    @Test
    void overallMetricsGrowByExactlyTheRunsCreated() throws Exception {
        OverallMetrics before = metrics.overall();
        long ok = runToEnd("Overall ok", false, false);
        long flaky = runToEnd("Overall flaky", true, false);
        long bad = runToEnd("Overall failing", false, true);
        OverallMetrics after = metrics.overall();

        assertEquals(3, after.runsTotal() - before.runsTotal());
        assertEquals(2, after.runsSucceeded() - before.runsSucceeded());
        assertEquals(1, after.runsFailed() - before.runsFailed());
        assertEquals(2, after.retries() - before.retries());
        assertEquals(metrics.forRun(ok).retries() + metrics.forRun(flaky).retries() + metrics.forRun(bad).retries(),
                after.retries() - before.retries());
        assertNotNull(after.successRate());
        assertTrue(after.successRate() >= 0.0 && after.successRate() <= 1.0);
        assertNotNull(after.avgEndToEndMs());
        assertNotNull(after.p95StageLatencyMs());
        assertNotNull(after.mttrMs());
    }

    @Test
    void unknownRunIsNotFound() {
        var ex = assertThrows(com.example.shortener.common.ApiException.class, () -> metrics.forRun(9_999_999L));
        assertEquals("RUN_NOT_FOUND", ex.code());
    }
}
