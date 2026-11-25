package com.example.shortener.scenarios;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-018")
class GreenfieldScenarioIT extends ScenarioSupport {

    @Test
    void reachesReleaseReadinessThroughBothApprovalsAndIsRepeatable() throws Exception {
        Outcome first = run("GREENFIELD", true);
        assertEquals("SUCCEEDED", first.status());
        assertEquals(List.of("HIGH_IMPACT_ACTION:IMPLEMENTATION", "HIGH_IMPACT_ACTION:RELEASE_READINESS"), first.prompts());
        assertEquals(8, artifacts.list(first.runId()).size());
        assertTrue(first.stages().stream().allMatch(s -> s.contains("=SUCCEEDED/")), first.stages().toString());

        // the same scenario three times gives the same stages, outcomes and approval prompts
        Outcome second = run("GREENFIELD", true);
        Outcome third = run("GREENFIELD", true);
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.fingerprint(), third.fingerprint());
    }
}
