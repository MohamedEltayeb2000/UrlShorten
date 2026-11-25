package com.example.shortener.scenarios;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-019")
@Requirement("001-FR-018")
class AmbiguousScenarioIT extends ScenarioSupport {

    @Test
    void pausesWithQuestionsAndAssumptionsAndProceedsOnlyAfterAHumanDecision() throws Exception {
        // 1. without a decision the run stops at the clarification and nothing downstream is produced
        Outcome waiting = run("AMBIGUOUS", false);
        assertEquals("WAITING_FOR_APPROVAL", waiting.status());
        assertTrue(waiting.prompts().contains("CLARIFICATION:REQUIREMENTS_ANALYSIS"));
        assertEquals(1, artifacts.list(waiting.runId()).size());
        var pending = approvals.pending().stream().filter(p -> p.runId() == waiting.runId()).findFirst().orElseThrow();
        assertTrue(pending.openQuestions().size() >= 3, "open questions are surfaced");
        assertTrue(pending.assumptions().size() >= 3, "assumptions are surfaced");
        String analysis = artifacts.current(waiting.runId(), "REQUIREMENTS_ANALYSIS", "REQUIREMENT").orElseThrow().getContent();
        assertTrue(analysis.contains("safeAssumptions"), "proceeds only within explicit safe assumptions");
        assertTrue(analysis.contains("HIGH:") || analysis.contains("MEDIUM:"), "risks are classified");

        // 2. the human decision resumes it; the audit trail shows the order
        approvals.decide(waiting.runId(), new com.example.shortener.governance.ApprovalService.DecisionRequest(
                "REQUIREMENTS_ANALYSIS", "APPROVE", "proceed with the safe assumptions", pending.artifactVersion()), REVIEWER);
        await(() -> service.detail(waiting.runId()).nodes().stream()
                .anyMatch(n -> n.id().equals("TASK_DECOMPOSITION") && n.status().equals("SUCCEEDED")), Duration.ofSeconds(30), "resume");
        List<String> ev = eventTypes(waiting.runId());
        assertTrue(ev.indexOf("CLARIFICATION_NEEDED") < ev.indexOf("APPROVAL_APPROVED"));

        // 3. a full run with the decision ends successfully
        Outcome done = run("AMBIGUOUS", true);
        assertEquals("SUCCEEDED", done.status());
        assertEquals("CLARIFICATION:REQUIREMENTS_ANALYSIS", done.prompts().get(0));
    }
}
