package com.example.shortener.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Seeded transient failures: bounded retries, then the stage succeeds on the provider. */
@SpringBootTest(properties = {"shortener.orchestrator.inject-failures=ARCHITECTURE_DESIGN:2", "shortener.orchestrator.retry.base-ms=20"})
@ActiveProfiles("test")
@Requirement("001-FR-015")
class RetryTransientThenSuccessIT extends RetryTestSupport {
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
