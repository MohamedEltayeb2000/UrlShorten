package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.*;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Failing tests block release readiness unless a human approves an explicit, audited override. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FailingTestsReleaseIT.Cfg.class)
@Requirement("001-FR-013")
@Requirement("001-FR-011")
class FailingTestsReleaseIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        AgentProvider failingTestsProvider() {
            MockAgentProvider delegate = new MockAgentProvider();
            return new AgentProvider() {
                public String providerName() { return "failing-tests-test"; }

                public Agent forStage(String stageId) {
                    Agent real = delegate.forStage(stageId);
                    if (!stageId.equals("TESTING")) return real;
                    return new Agent() {
                        public String name() { return real.name(); }
                        public String stageId() { return real.stageId(); }
                        public AgentResult run(AgentInput in) {
                            AgentResult r = real.run(in);
                            Map<String, Object> out = new HashMap<>(r.structuredOutput());
                            out.put("expectedResults", Map.of("passed", 10, "failed", 2));
                            return new AgentResult(r.agentName(), r.status(), r.summary(), out, r.assumptions(), r.risks(),
                                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                                    r.referencesToInputs(), r.simulated());
                        }
                    };
                }
            };
        }
    }

    @Autowired RunService service;
    @Autowired ApprovalService approvals;
    @Autowired AuditService audit;
    @Autowired PolicyDecisionRepository policyDecisions;

    private void waitWaiting(long id) throws Exception {
        await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(30), "approval gate");
    }

    @Test
    void releaseWithFailingTestsWaitsForAnAuditedOverrideApproval() throws Exception {
        Actor alice = new Actor("alice", ActorType.HUMAN);
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        waitWaiting(id);
        approvals.decide(id, new ApprovalService.DecisionRequest("IMPLEMENTATION", "APPROVE", "patch reviewed", null), alice);
        waitWaiting(id);

        var pending = approvals.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow();
        assertEquals("RELEASE_READINESS", pending.nodeId());
        assertTrue(pending.policyReason().startsWith("failing-tests-block-release"), pending.policyReason());
        assertTrue(policyDecisions.findByRunIdOrderByIdAsc(id).stream()
                .anyMatch(p -> p.getRule().equals("failing-tests-block-release") && p.getOutcome() == PolicyOutcome.DENY));

        approvals.decide(id, new ApprovalService.DecisionRequest("RELEASE_READINESS", "APPROVE", "accepted risk: flaky suite, ticket filed", null), alice);
        await(() -> service.detail(id).status().equals("SUCCEEDED"), Duration.ofSeconds(30), "run to finish");

        List<AuditEvent> ev = audit.events(id, 0, 100).items();
        assertTrue(ev.stream().anyMatch(e -> e.getEventType().equals("RELEASE_OVERRIDE_APPROVED") && e.getActorId().equals("alice")),
                "the override is audited with the approver's identity");
        assertTrue(policyDecisions.findByRunIdOrderByIdAsc(id).stream()
                .anyMatch(p -> p.getRule().equals("failing-tests-block-release") && p.getReason().contains("override approved by alice")));
    }

    @Test
    void rejectingTheOverrideKeepsReleaseBlocked() throws Exception {
        Actor alice = new Actor("alice", ActorType.HUMAN);
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        waitWaiting(id);
        approvals.decide(id, new ApprovalService.DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null), alice);
        waitWaiting(id);
        approvals.decide(id, new ApprovalService.DecisionRequest("RELEASE_READINESS", "REJECT", "fix the failing tests first", null), alice);
        RunDtos.RunDetail d = service.detail(id);
        assertEquals("BLOCKED", d.nodes().stream().filter(n -> n.id().equals("RELEASE_READINESS")).findFirst().orElseThrow().status());
        assertNotEquals("SUCCEEDED", d.status());
    }
}
