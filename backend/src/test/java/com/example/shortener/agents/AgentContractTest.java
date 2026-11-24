package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-008")
@Requirement("001-FR-009")
class AgentContractTest {

    private static final Map<String, List<String>> REQUIRED_KEYS = Map.of(
            "REQUIREMENTS_ANALYSIS", List.of("normalizedRequirement", "goals", "constraints"),
            "TASK_DECOMPOSITION", List.of("tasks"),
            "ARCHITECTURE_DESIGN", List.of("components", "decisions", "dataFlow"),
            "IMPLEMENTATION_PLAN", List.of("steps", "filesToChange"),
            "IMPLEMENTATION", List.of("proposal", "applied"),
            "TESTING", List.of("testPlan", "expectedResults"),
            "DOCUMENTATION", List.of("documents"),
            "RELEASE_READINESS", List.of("checklist", "ready", "blockers"));

    @Test
    void everyAgentReturnsTheFullContract() {
        Map<String, AgentResult> results = AgentTestSupport.pipeline("GREENFIELD", AgentTestSupport.LINKS_REQUIREMENT);
        assertEquals(8, results.size());
        MockAgentProvider provider = new MockAgentProvider();
        results.forEach((stage, r) -> {
            assertEquals(provider.forStage(stage).name(), r.agentName());
            assertEquals(AgentStatus.SUCCESS, r.status());
            assertTrue(r.simulated(), stage);
            assertTrue(r.summary().startsWith("[SIMULATED] "), stage);
            assertTrue(r.confidence() >= 0 && r.confidence() <= 1, stage);
            assertFalse(r.confidenceNote().isBlank(), stage);
            assertFalse(r.assumptions().isEmpty(), stage);
            assertFalse(r.risks().isEmpty(), stage);
            assertFalse(r.recommendedNextActions().isEmpty(), stage);
            for (String key : REQUIRED_KEYS.get(stage)) {
                assertTrue(r.structuredOutput().containsKey(key), stage + " missing " + key);
            }
        });
    }

    @Test
    void stageSpecificShapes() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline("GREENFIELD", AgentTestSupport.LINKS_REQUIREMENT);
        assertEquals(Boolean.FALSE, r.get("IMPLEMENTATION").structuredOutput().get("applied"));
        Map<?, ?> proposal = (Map<?, ?>) r.get("IMPLEMENTATION").structuredOutput().get("proposal");
        assertTrue(proposal.get("diff") instanceof String);
        assertTrue(proposal.get("files") instanceof List<?>);
        Map<?, ?> expected = (Map<?, ?>) r.get("TESTING").structuredOutput().get("expectedResults");
        assertEquals(12, expected.get("passed"));
        assertEquals(0, expected.get("failed"));
        assertEquals(Boolean.TRUE, r.get("RELEASE_READINESS").structuredOutput().get("ready"));
        List<?> tasks = (List<?>) r.get("TASK_DECOMPOSITION").structuredOutput().get("tasks");
        assertTrue(((Map<?, ?>) tasks.get(0)).containsKey("dependsOn"));
    }

    @Test
    void sameInputGivesEqualResults() {
        for (String scenario : new String[] {"GREENFIELD", "AMBIGUOUS", "BROWNFIELD"}) {
            assertEquals(AgentTestSupport.pipeline(scenario, AgentTestSupport.LINKS_REQUIREMENT),
                    AgentTestSupport.pipeline(scenario, AgentTestSupport.LINKS_REQUIREMENT), scenario);
        }
    }

    @Test
    void referencesAreDerivedFromUpstreamOutputs() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline(null, "Add a feature flag service");
        assertEquals(List.of(), r.get("REQUIREMENTS_ANALYSIS").referencesToInputs());
        assertEquals(List.of("REQUIREMENTS_ANALYSIS:v1"), r.get("TASK_DECOMPOSITION").referencesToInputs());
        assertEquals(List.of("ARCHITECTURE_DESIGN:v1", "IMPLEMENTATION_PLAN:v1", "REQUIREMENTS_ANALYSIS:v1",
                "TASK_DECOMPOSITION:v1"), r.get("IMPLEMENTATION").referencesToInputs());
    }

    @Test
    void downstreamAgentsUseUpstreamContent() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline("GREENFIELD", "Add a feature flag service");
        String goals = r.get("REQUIREMENTS_ANALYSIS").structuredOutput().get("goals").toString();
        String tasks = r.get("TASK_DECOMPOSITION").structuredOutput().get("tasks").toString();
        assertTrue(tasks.contains("Add a feature flag service"));
        assertTrue(goals.contains("Add a feature flag service"));
        assertTrue(r.get("ARCHITECTURE_DESIGN").structuredOutput().get("components").toString().contains("T1"));
    }
}
