package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-008")
@Requirement("001-FR-009")
class AgentScenarioTest {

    @Test
    void ambiguousRequirementNeedsClarification() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline("AMBIGUOUS", "Make links better");
        AgentResult analyst = r.get("REQUIREMENTS_ANALYSIS");
        assertEquals(AgentStatus.NEEDS_CLARIFICATION, analyst.status());
        assertTrue(analyst.openQuestions().size() >= 3);
        assertTrue(analyst.assumptions().size() >= 3);
        assertTrue(analyst.risks().stream().anyMatch(s -> s.startsWith("HIGH:")));
        assertTrue(analyst.risks().stream().anyMatch(s -> s.startsWith("MEDIUM:")));
        List<?> safe = (List<?>) analyst.structuredOutput().get("safeAssumptions");
        assertTrue(safe.size() >= 3);
        r.forEach((stage, res) -> {
            if (!stage.equals("REQUIREMENTS_ANALYSIS")) {
                assertEquals(AgentStatus.SUCCESS, res.status(), stage);
                assertTrue(res.assumptions().containsAll(safe), stage + " carries assumptions forward");
            }
        });
    }

    @Test
    void brownfieldUsesTheSeededFixture() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline("BROWNFIELD", "Make POST /links retries safe");
        for (String stage : List.of("REQUIREMENTS_ANALYSIS", "ARCHITECTURE_DESIGN", "IMPLEMENTATION")) {
            Map<String, Object> out = r.get(stage).structuredOutput();
            List<?> files = (List<?>) out.get("impactedFiles");
            assertTrue(files.stream().anyMatch(f -> f.toString().endsWith("LinkService.java")), stage);
            assertTrue(files.stream().anyMatch(f -> f.toString().endsWith("LinkController.java")), stage);
            assertTrue(files.stream().anyMatch(f -> f.toString().endsWith("schema.sql")), stage);
            assertFalse(((List<?>) out.get("compatibilityRisks")).isEmpty(), stage);
            assertFalse(((List<?>) out.get("dataFlow")).isEmpty(), stage);
        }
        String proposal = r.get("IMPLEMENTATION").structuredOutput().get("proposal").toString();
        assertTrue(proposal.contains("Idempotency-Key"));
        assertTrue(proposal.toLowerCase().contains("fingerprint"));
        assertTrue(proposal.contains("UNIQUE"));
        assertEquals(Boolean.FALSE, r.get("IMPLEMENTATION").structuredOutput().get("applied"));
    }

    @Test
    void greenfieldAndNullScenarioSucceed() {
        for (String scenario : new String[] {"GREENFIELD", null}) {
            AgentTestSupport.pipeline(scenario, AgentTestSupport.LINKS_REQUIREMENT)
                    .forEach((stage, res) -> assertEquals(AgentStatus.SUCCESS, res.status(), stage));
        }
    }
}
