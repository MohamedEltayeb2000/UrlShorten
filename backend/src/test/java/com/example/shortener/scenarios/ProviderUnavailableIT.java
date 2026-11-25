package com.example.shortener.scenarios;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Every stage's provider fails transiently forever: the simulated fallback must still complete the whole scenario. */
@SpringBootTest(properties = {
        "shortener.orchestrator.retry.base-ms=5",
        "shortener.orchestrator.retry.max-ms=10",
        "shortener.orchestrator.inject-failures=REQUIREMENTS_ANALYSIS:99,TASK_DECOMPOSITION:99,ARCHITECTURE_DESIGN:99,"
                + "IMPLEMENTATION_PLAN:99,IMPLEMENTATION:99,TESTING:99,DOCUMENTATION:99,RELEASE_READINESS:99"})
@ActiveProfiles("test")
@Requirement("001-FR-010")
class ProviderUnavailableIT extends ScenarioSupport {

    @Test
    void theSimulatedFallbackCompletesAFullRunAndEveryOutputIsLabeled() throws Exception {
        Outcome o = run("GREENFIELD", true);
        assertEquals("SUCCEEDED", o.status());
        assertEquals(8, artifacts.list(o.runId()).size());
        for (Artifact a : artifacts.list(o.runId())) {
            assertTrue(a.getContent().contains("\"status\":\"FALLBACK\""), a.getNodeId() + " must be labeled FALLBACK");
            assertTrue(a.getContent().contains("\"simulated\":true"), a.getNodeId());
        }
        assertEquals(8, eventTypes(o.runId()).stream().filter("FALLBACK_USED"::equals).count());
        assertTrue(o.stages().stream().allMatch(s -> s.endsWith("/3")), "bounded: three attempts per stage before the fallback");
    }
}
