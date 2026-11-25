package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.governance.ApprovalService.DecisionRequest;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** HIGH-risk approvals need a change ticket and a risk justification; the change record lands in the audit trail. */
@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-033")
class ChangeControlIT {
    static final Actor HUMAN = new Actor("alice", ActorType.HUMAN);

    @Autowired RunService service;
    @Autowired ApprovalService approvals;
    @Autowired AuditService audit;

    private long waitingRun(String scenario) throws Exception {
        long id = service.create("Build a URL shortener API", scenario).id();
        service.start(id);
        await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(30), "approval gate");
        return id;
    }

    private String nodeStatus(long id, String node) {
        return service.detail(id).nodes().stream().filter(n -> n.id().equals(node)).findFirst().orElseThrow().status();
    }

    private void assertRefused(long id, DecisionRequest req) {
        ApiException e = assertThrows(ApiException.class, () -> approvals.decide(id, req, HUMAN));
        assertEquals("CHANGE_RECORD_REQUIRED", e.code());
        assertEquals("WAITING_FOR_APPROVAL", nodeStatus(id, "IMPLEMENTATION"), "the stage still waits");
    }

    @Test
    void aHighRiskApprovalShowsTheProposalAndNeedsAValidChangeRecord() throws Exception {
        long id = waitingRun("GREENFIELD");
        var pending = approvals.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow();
        assertEquals("HIGH", pending.riskClass());
        assertTrue(pending.proposedCommands().contains("mvn -q test"));
        assertTrue(pending.proposedMigrations().contains("touchesNonTestData"));

        assertRefused(id, new DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null));
        assertRefused(id, new DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null, "not a ticket", "a long enough justification"));
        assertRefused(id, new DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null, "CHG-1042", "too short"));
        assertRefused(id, new DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null, "CHG-1042", null));

        ApprovalService.ApprovalView v = approvals.decide(id, new DecisionRequest("IMPLEMENTATION", "APPROVE", "ok", null,
                "CHG-1042", "Reviewed the diff; commands and migrations are test-only"), HUMAN);
        assertEquals("APPROVED", v.status());
        assertEquals("CHG-1042", v.changeTicket());
        assertEquals("HIGH", v.riskClass());
        assertTrue(v.riskJustification().startsWith("Reviewed the diff"));

        List<AuditEvent> ev = audit.events(id, 0, 100).items();
        AuditEvent record = ev.stream().filter(e -> e.getEventType().equals("CHANGE_RECORD")).findFirst().orElseThrow();
        assertTrue(record.getDetails().contains("CHG-1042"));
        assertTrue(record.getDetails().contains("HIGH"));
        assertEquals("CHG-1042", approvals.forRun(id).get(0).changeTicket());
    }

    @Test
    void rejectingNeedsNoChangeRecord() throws Exception {
        long id = waitingRun("GREENFIELD");
        ApprovalService.ApprovalView v = approvals.decide(id, new DecisionRequest("IMPLEMENTATION", "REJECT", "not now", null), HUMAN);
        assertEquals("REJECTED", v.status());
        assertNull(v.changeTicket());
    }

    @Test
    void aClarificationApprovalIsMediumRiskAndNeedsNoTicket() throws Exception {
        long id = waitingRun("AMBIGUOUS");
        var pending = approvals.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow();
        assertEquals("CLARIFICATION", pending.kind());
        assertEquals("MEDIUM", pending.riskClass());
        ApprovalService.ApprovalView v = approvals.decide(id,
                new DecisionRequest("REQUIREMENTS_ANALYSIS", "APPROVE", "proceed with the safe assumptions", pending.artifactVersion()), HUMAN);
        assertEquals("APPROVED", v.status());
    }
}
