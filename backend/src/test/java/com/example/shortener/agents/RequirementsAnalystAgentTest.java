package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-035")
class RequirementsAnalystAgentTest {

    private static AgentResult analyze(String scenario, String text) {
        return new MockAgentProvider().forStage("REQUIREMENTS_ANALYSIS")
                .run(AgentTestSupport.input("REQUIREMENTS_ANALYSIS", scenario, text, Map.of()));
    }

    @Test
    void vagueRequirementWithoutAmbiguousLabelAsksQuestionsNamingTheTerms() {
        AgentResult r = analyze("GREENFIELD", "Make the shortener faster and more secure and easy to use.");
        assertEquals(AgentStatus.NEEDS_CLARIFICATION, r.status());
        assertTrue(r.openQuestions().size() >= 3);
        String all = String.join("\n", r.openQuestions());
        assertTrue(all.contains("'faster'") && all.contains("'secure'") && all.contains("'easy'"), all);
        assertTrue(r.assumptions().size() >= 3);
        assertTrue(r.risks().stream().anyMatch(s -> s.startsWith("HIGH:")));
        assertTrue(r.risks().stream().anyMatch(s -> s.startsWith("MEDIUM:")));
        assertTrue(r.risks().stream().anyMatch(s -> s.startsWith("LOW:")));
        assertTrue(((List<?>) r.structuredOutput().get("safeAssumptions")).size() >= 3);
    }

    @Test
    void presetAmbiguousQuestionsMentionItsOwnVagueTerms() {
        AgentResult r = analyze("AMBIGUOUS", "Make URL links safer and improve analytics.");
        assertEquals(AgentStatus.NEEDS_CLARIFICATION, r.status());
        assertTrue(r.openQuestions().size() >= 3);
        assertTrue(r.openQuestions().stream().anyMatch(q -> q.contains("'safer'") && q.contains("malicious URLs")));
        assertTrue(r.openQuestions().stream().anyMatch(q -> q.contains("'improve'")));
        assertEquals(0.45, r.confidence(), 1e-9);
    }

    @Test
    void preciseRequirementWithAmbiguousLabelStillAsksQuestions() {
        AgentResult r = analyze("AMBIGUOUS",
                "Reject target URLs that are not http or https and count clicks per link, at most 100 per second.");
        assertEquals(AgentStatus.NEEDS_CLARIFICATION, r.status());
        assertTrue(r.openQuestions().size() >= 3, "falls back to the generic questions");
        assertTrue(((List<?>) r.structuredOutput().get("safeAssumptions")).size() >= 3);
    }

    @Test
    void presetsKeepTheirExpectedBehavior() {
        AgentResult g = analyze("GREENFIELD",
                "Build a URL shortener API with link creation, redirects, expiration, and click analytics.");
        assertEquals(AgentStatus.SUCCESS, g.status());
        assertTrue(g.openQuestions().isEmpty());
        AgentResult b = analyze("BROWNFIELD",
                "Add idempotent link creation and prevent duplicate links when clients retry requests.");
        assertEquals(AgentStatus.SUCCESS, b.status());
        assertFalse(((List<?>) b.structuredOutput().get("impactedFiles")).isEmpty());
    }

    @Test
    void goalsAndConstraintsComeFromTheText() {
        AgentResult r = analyze("GREENFIELD",
                "Allow users to create links; links must expire after 30 days and only https targets are accepted.");
        assertEquals(AgentStatus.SUCCESS, r.status());
        List<?> goals = (List<?>) r.structuredOutput().get("goals");
        List<?> constraints = (List<?>) r.structuredOutput().get("constraints");
        assertTrue(goals.stream().anyMatch(x -> x.toString().startsWith("Allow users")));
        assertTrue(constraints.stream().anyMatch(x -> x.toString().contains("30 days")));
        assertEquals(r.structuredOutput().get("normalizedRequirement"),
                "Allow users to create links; links must expire after 30 days and only https targets are accepted.");
    }

    @Test
    void plannerDerivesTasksAndAcceptanceCriteriaFromGoalsAndConstraints() {
        Map<String, AgentResult> r = AgentTestSupport.pipeline("GREENFIELD",
                "Allow users to create links; links must expire after 30 days and only https targets are accepted.");
        List<?> goals = (List<?>) r.get("REQUIREMENTS_ANALYSIS").structuredOutput().get("goals");
        List<?> tasks = (List<?>) r.get("TASK_DECOMPOSITION").structuredOutput().get("tasks");
        assertEquals(goals.size() + 1, tasks.size());
        Map<?, ?> verify = (Map<?, ?>) tasks.get(tasks.size() - 1);
        assertTrue(((List<?>) verify.get("acceptanceCriteria")).stream().anyMatch(x -> x.toString().contains("30 days")));
        assertTrue(((Map<?, ?>) tasks.get(0)).containsKey("dependsOn"));
    }
}
