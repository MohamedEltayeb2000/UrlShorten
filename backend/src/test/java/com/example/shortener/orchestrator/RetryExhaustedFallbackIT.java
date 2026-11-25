package com.example.shortener.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Retries exhausted: the deterministic simulated fallback completes the stage and is clearly labeled. */
@SpringBootTest(properties = {"shortener.orchestrator.inject-failures=ARCHITECTURE_DESIGN:99", "shortener.orchestrator.retry.base-ms=20"})
@ActiveProfiles("test")
@Requirement("001-FR-015")
@Requirement("001-FR-010")
class RetryExhaustedFallbackIT extends RetryTestSupport {
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
