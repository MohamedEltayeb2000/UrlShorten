package com.example.shortener.summary;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.orchestrator.RunDtos;
import com.example.shortener.orchestrator.RunService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-022")
class EngineeringSummaryTest {
    static final Actor HUMAN = new Actor("alice", ActorType.HUMAN);
    static final List<String> HEADINGS = List.of("Overview", "Plan and rationale", "Artifacts", "Decisions and approvals",
            "Assumptions", "Risks and trade-offs", "Validation", "Limitations");

    @Autowired RunService runs;
    @Autowired ApprovalService approvals;
    @Autowired EngineeringSummaryService summary;

    private void awaitStatus(long id, String status) throws Exception {
        await(() -> runs.detail(id).status().equals(status), Duration.ofSeconds(30), "run to reach " + status);
    }

    private long completedGreenfield() throws Exception {
        long id = runs.create("Build a URL shortener API with link creation, redirects, expiration, and click analytics.", "GREENFIELD").id();
        runs.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approvals.decide(id, new ApprovalService.DecisionRequest("IMPLEMENTATION", "APPROVE", "patch looks fine", null), HUMAN);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approvals.decide(id, new ApprovalService.DecisionRequest("RELEASE_READINESS", "APPROVE", "ready to release", null), HUMAN);
        awaitStatus(id, "SUCCEEDED");
        return id;
    }

    private static String section(String md, String heading) {
        int start = md.indexOf("## " + heading + "\n");
        assertTrue(start >= 0, "missing heading " + heading);
        int end = md.indexOf("\n## ", start + 1);
        return end < 0 ? md.substring(start) : md.substring(start, end);
    }

    @Test
    void completedGreenfieldRunHasAllSectionsInOrder() throws Exception {
        long id = completedGreenfield();
        String md = summary.generate(id);

        assertTrue(md.startsWith("# Engineering summary"));
        int last = -1;
        for (String h : HEADINGS) {
            int at = md.indexOf("\n## " + h + "\n");
            assertTrue(at > last, "heading out of order or missing: " + h);
            last = at;
        }
        String artifacts = section(md, "Artifacts");
        long rows = artifacts.lines().filter(l -> l.startsWith("| ") && !l.startsWith("| Stage")).count();
        assertEquals(8, rows);
        assertTrue(artifacts.contains("never applied"));

        String decisions = section(md, "Decisions and approvals");
        assertTrue(decisions.contains("alice"));
        assertTrue(decisions.contains("patch looks fine") && decisions.contains("ready to release"));

        assertTrue(section(md, "Assumptions").lines().anyMatch(l -> l.startsWith("- ") && !l.contains("none recorded")));
        assertTrue(section(md, "Risks and trade-offs").lines().anyMatch(l -> l.startsWith("- ") && !l.contains("none recorded")));
        String validation = section(md, "Validation");
        assertFalse(validation.contains("no test report available"));
        assertTrue(validation.contains("passed"));
        assertTrue(validation.contains("Audit events recorded:"));

        assertEquals(md, summary.generate(id), "the summary must be deterministic");
    }

    @Test
    void ambiguousRunListsOpenQuestionsAndTheWaitingStage() throws Exception {
        long id = runs.create("Make URL links safer and improve analytics.", "AMBIGUOUS").id();
        runs.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        String md = summary.generate(id);

        String assumptions = section(md, "Assumptions");
        assertTrue(assumptions.contains("Open questions:"));
        assertTrue(assumptions.contains("unresolved"));
        assertTrue(section(md, "Limitations").contains("Stage REQUIREMENTS_ANALYSIS is still waiting for a decision"));
    }

    @Test
    void unknownRunIsNotFound() {
        ApiException e = assertThrows(ApiException.class, () -> summary.generate(987654321L));
        assertEquals("RUN_NOT_FOUND", e.code());
    }

    @Test
    void markdownCharactersCannotBreakTablesAndSecretsAreRedacted() throws Exception {
        RunDtos.RunDetail hostile = runs.create("Build a | shortener\n| a | b |\n<script>alert(1)</script>", "GREENFIELD");
        runs.start(hostile.id());
        awaitStatus(hostile.id(), "WAITING_FOR_APPROVAL");
        String md = summary.generate(hostile.id());
        assertFalse(md.contains("<script>"));
        List<String> rows = section(md, "Artifacts").lines().filter(l -> l.startsWith("|")).toList();
        assertTrue(rows.size() > 2);
        for (String row : rows) {
            assertEquals(8, row.split("(?<!\\\\)\\|", -1).length - 1, "unexpected cell count in: " + row);
        }

        long secret = runs.create("Shorten links with password = hunter2hunter2 in the config", "GREENFIELD").id();
        String md2 = summary.generate(secret);
        assertFalse(md2.contains("hunter2hunter2"));
        assertTrue(section(md2, "Overview").contains("[redacted by policy]"));
    }
}
