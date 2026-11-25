package com.example.shortener.perf;

import com.example.shortener.support.ChangeRecords;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.orchestrator.RunService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** All three demo scenarios, including their human approvals, finish well inside the budget (default stage delay 0 in tests). */
@SpringBootTest
@ActiveProfiles("test")
@Tag("perf")
@Requirement("002-FR-014")
class ScenarioDemoPerfIT {
    @Autowired RunService service;
    @Autowired ApprovalService approvals;

    @Test
    void threeScenariosFinishWithinTheDemoBudget() throws Exception {
        long budgetSeconds = new ObjectMapper().readTree(RepoFiles.read("docs/perf/budgets.json")).get("scenario_demo_seconds").asLong();
        Actor reviewer = new Actor("reviewer", ActorType.HUMAN);
        Instant t0 = Instant.now();
        for (String scenario : new String[] {"GREENFIELD", "BROWNFIELD", "AMBIGUOUS"}) {
            long id = service.create(null, scenario).id();
            service.start(id);
            for (int round = 0; round < 8; round++) {
                waitIdle(id);
                String status = service.detail(id).status();
                if (status.equals("SUCCEEDED") || status.equals("FAILED")) break;
                for (var p : approvals.pending().stream().filter(x -> x.runId() == id).toList()) {
                    approvals.decide(id, ChangeRecords.req(p.nodeId(), "APPROVE", "perf run", p.artifactVersion()), reviewer);
                }
            }
            assertEquals("SUCCEEDED", service.detail(id).status(), scenario);
        }
        long seconds = Duration.between(t0, Instant.now()).toSeconds();
        RepoFiles.recordPerf("three scenarios end to end (s)", budgetSeconds, seconds, seconds <= budgetSeconds);
        assertTrue(seconds <= budgetSeconds, "scenarios took " + seconds + " s, budget " + budgetSeconds + " s");
    }

    private void waitIdle(long id) throws InterruptedException {
        for (int i = 0; i < 1200; i++) {
            String s = service.detail(id).status();
            if (s.equals("SUCCEEDED") || s.equals("FAILED") || s.equals("WAITING_FOR_APPROVAL")) return;
            Thread.sleep(25);
        }
        fail("run " + id + " did not settle");
    }
}
