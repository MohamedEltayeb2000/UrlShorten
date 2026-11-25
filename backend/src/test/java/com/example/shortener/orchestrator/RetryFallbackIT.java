package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Seeded transient failures: bounded retry then success, or retries exhausted then the labeled simulated fallback. */
class RetryFallbackIT {

    abstract static class Base {
        @Autowired RunService service;
        @Autowired RunNodeRepository nodes;
        @Autowired StageAttemptRepository attempts;
        @Autowired AuditService audit;
        @Autowired ArtifactService artifacts;

        long runUntilWaiting() throws Exception {
            long id = service.create("Build a URL shortener API", "GREENFIELD").id();
            service.start(id);
            await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(40), "run to reach the approval gate");
            return id;
        }

        List<String> events(long id) {
            return audit.events(id, 0, 100).items().stream().map(AuditEvent::getEventType).toList();
        }
    }

    @SpringBootTest(properties = {"shortener.orchestrator.inject-failures=ARCHITECTURE_DESIGN:2", "shortener.orchestrator.retry.base-ms=20"})
    @ActiveProfiles("test")
    @Requirement("001-FR-015")
    static class TransientThenSuccess extends Base {
        @Test
        void transientFailuresAreRetriedWithinTheBoundThenTheStageSucceeds() throws Exception {
            long id = runUntilWaiting();
            RunNode arch = nodes.findByRunIdAndNodeId(id, "ARCHITECTURE_DESIGN").orElseThrow();
            assertEquals(NodeStatus.SUCCEEDED, arch.getStatus());
            assertEquals(3, arch.getAttemptCount());
            var rows = attempts.findByRunNodeIdOrderByAttemptAsc(arch.getId());
            assertEquals(List.of("RETRYING", "RETRYING", "SUCCEEDED"), rows.stream().map(StageAttempt::getStatus).toList());
            assertFalse(rows.get(2).isFallbackUsed());
            assertEquals(2, events(id).stream().filter("STAGE_RETRY"::equals).count());
            assertFalse(events(id).contains("FALLBACK_USED"));
            assertFalse(artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow().getContent().contains("\"FALLBACK\""));
        }
    }

    @SpringBootTest(properties = {"shortener.orchestrator.inject-failures=ARCHITECTURE_DESIGN:99", "shortener.orchestrator.retry.base-ms=20"})
    @ActiveProfiles("test")
    @Requirement("001-FR-015")
    @Requirement("001-FR-010")
    static class RetriesExhaustedThenFallback extends Base {
        @Test
        void afterTheBoundTheSimulatedFallbackCompletesTheStageAndIsLabeled() throws Exception {
            long id = runUntilWaiting();
            RunNode arch = nodes.findByRunIdAndNodeId(id, "ARCHITECTURE_DESIGN").orElseThrow();
            assertEquals(NodeStatus.SUCCEEDED, arch.getStatus());
            assertEquals(3, arch.getAttemptCount(), "bounded: never more than the configured attempts");
            var rows = attempts.findByRunNodeIdOrderByAttemptAsc(arch.getId());
            StageAttempt last = rows.get(rows.size() - 1);
            assertTrue(last.isFallbackUsed());
            assertEquals("mock-fallback", last.getProviderUsed());
            assertTrue(events(id).contains("FALLBACK_USED"));
            String content = artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow().getContent();
            assertTrue(content.contains("\"status\":\"FALLBACK\""), "output is labeled FALLBACK");
            assertTrue(content.contains("\"simulated\":true") && content.contains("[SIMULATED]"));
        }
    }
}
