package com.example.shortener.orchestrator;

import com.example.shortener.support.ChangeRecords;
import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.artifacts.ArtifactStatus;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.*;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Approval gates, rejection, agent self-approval ban, retry, re-plan with stale propagation, rollback - all with the real agents. */
@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-011")
@Requirement("001-FR-012")
@Requirement("001-FR-013")
@Requirement("001-FR-016")
@Requirement("001-FR-017")
@Requirement("001-FR-019")
class GovernanceFlowIT {
    static final Actor HUMAN = new Actor("alice", ActorType.HUMAN);

    @Autowired RunService service;
    @Autowired RunControlService control;
    @Autowired ApprovalService approvalService;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired PolicyDecisionRepository policyDecisions;
    @Autowired RunNodeRepository nodes;

    private long newRun(String scenario) {
        return service.create("Build a URL shortener API with link creation, redirects, expiration, and click analytics.", scenario).id();
    }

    private RunDtos.RunDetail awaitStatus(long id, String... statuses) throws Exception {
        List<String> wanted = List.of(statuses);
        await(() -> wanted.contains(service.detail(id).status()), Duration.ofSeconds(30), "run to reach " + wanted);
        return service.detail(id);
    }

    private static String status(RunDtos.RunDetail d, String node) {
        return d.nodes().stream().filter(n -> n.id().equals(node)).findFirst().orElseThrow().status();
    }

    private ApprovalService.DecisionRequest approve(String node) {
        return ChangeRecords.req(node, "APPROVE", "looks good", null);
    }

    @Test
    void greenfieldNeedsApprovalForThePatchAndForReleaseThenSucceeds() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);

        RunDtos.RunDetail d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("WAITING_FOR_APPROVAL", status(d, "IMPLEMENTATION"));
        assertEquals("PENDING", status(d, "TESTING"), "nothing downstream may run before the patch is approved");
        assertEquals(1, approvalService.pending().stream().filter(p -> p.runId() == id).count());
        assertEquals("HIGH_IMPACT_ACTION", approvalService.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow().kind());

        ApprovalService.ApprovalView v = approvalService.decide(id, approve("IMPLEMENTATION"), HUMAN);
        assertEquals("APPROVED", v.status());
        assertEquals("alice", v.actorId());

        d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("WAITING_FOR_APPROVAL", status(d, "RELEASE_READINESS"), "release readiness is the second gate");
        approvalService.decide(id, approve("RELEASE_READINESS"), HUMAN);

        d = awaitStatus(id, "SUCCEEDED");
        assertTrue(d.nodes().stream().allMatch(n -> n.status().equals("SUCCEEDED")));
        List<String> events = audit.events(id, 0, 100).items().stream().map(AuditEvent::getEventType).toList();
        assertEquals(2, events.stream().filter("APPROVAL_APPROVED"::equals).count());
        assertEquals(2, events.stream().filter("APPROVAL_REQUESTED"::equals).count());
        // the policy decisions were recorded
        assertTrue(policyDecisions.findByRunIdOrderByIdAsc(id).stream().anyMatch(p -> p.getRule().equals("generated-output-is-proposal")));
        // nothing was applied
        assertTrue(artifacts.current(id, "IMPLEMENTATION", "CODE_PROPOSAL").orElseThrow().getContent().contains("\"applied\":false"));
    }

    @Test
    void rejectionBlocksTheStageRecordsFeedbackAndDownstreamNeverRuns() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");

        ApiException noComment = assertThrows(ApiException.class, () -> approvalService.decide(id,
                ChangeRecords.req("IMPLEMENTATION", "REJECT", "  ", null), HUMAN));
        assertEquals("COMMENT_REQUIRED", noComment.code());

        ApprovalService.ApprovalView v = approvalService.decide(id,
                ChangeRecords.req("IMPLEMENTATION", "REJECT", "needs input validation first", null), HUMAN);
        assertEquals("REJECTED", v.status());
        assertEquals("needs input validation first", v.comment());

        RunDtos.RunDetail d = service.detail(id);
        assertEquals("BLOCKED", status(d, "IMPLEMENTATION"));
        assertTrue(d.nodes().stream().filter(n -> n.id().equals("IMPLEMENTATION")).findFirst().orElseThrow()
                .errorSummary().contains("needs input validation first"));
        assertEquals("PENDING", status(d, "TESTING"));
        assertTrue(audit.events(id, 0, 100).items().stream().anyMatch(e -> e.getEventType().equals("APPROVAL_REJECTED")));
        // deciding twice is refused
        assertEquals("NO_PENDING_APPROVAL", assertThrows(ApiException.class, () -> approvalService.decide(id, approve("IMPLEMENTATION"), HUMAN)).code());
    }

    @Test
    void anAgentCannotApproveItsOwnWork() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        Actor agent = new Actor("agent:ImplementerAgent", ActorType.AGENT);
        ApiException e = assertThrows(ApiException.class, () -> approvalService.decide(id, approve("IMPLEMENTATION"), agent));
        assertEquals("APPROVAL_FORBIDDEN", e.code());
        assertEquals("WAITING_FOR_APPROVAL", status(service.detail(id), "IMPLEMENTATION"), "still waiting for a human");
    }

    @Test
    void ambiguousRunResumesOnlyAfterAHumanDecision() throws Exception {
        long id = newRun("AMBIGUOUS");
        service.start(id);
        RunDtos.RunDetail d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("WAITING_FOR_APPROVAL", status(d, "REQUIREMENTS_ANALYSIS"));
        var pending = approvalService.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow();
        assertEquals("CLARIFICATION", pending.kind());
        assertTrue(pending.openQuestions().size() >= 3, "the questions are shown to the reviewer");
        assertFalse(pending.assumptions().isEmpty());

        approvalService.decide(id, ChangeRecords.req("REQUIREMENTS_ANALYSIS", "APPROVE",
                "proceed with the stated safe assumptions", pending.artifactVersion()), HUMAN);
        d = awaitStatus(id, "WAITING_FOR_APPROVAL", "SUCCEEDED");
        assertEquals("SUCCEEDED", status(d, "REQUIREMENTS_ANALYSIS"));
        assertEquals("SUCCEEDED", status(d, "TASK_DECOMPOSITION"), "resumed after the decision");
    }

    @Test
    void replanMarksOnlyDownstreamStaleRerunsOnlyThoseAndKeepsOldVersions() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approvalService.decide(id, approve("IMPLEMENTATION"), HUMAN);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approvalService.decide(id, approve("RELEASE_READINESS"), HUMAN);
        awaitStatus(id, "SUCCEEDED");
        Artifact archBefore = artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow();
        Artifact planBefore = artifacts.current(id, "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN").orElseThrow();

        RunDtos.RunDetail d = control.replan(id, "IMPLEMENTATION_PLAN", "Build a URL shortener API with custom aliases only.", HUMAN);
        assertEquals(2, d.planVersion());

        // IMPLEMENTATION_PLAN and its dependents re-run; ARCHITECTURE_DESIGN and DOCUMENTATION are untouched
        RunDtos.RunDetail again = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("SUCCEEDED", status(again, "IMPLEMENTATION_PLAN"));
        Artifact archAfter = artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow();
        assertEquals(archBefore.getId(), archAfter.getId(), "architecture was not re-run");
        assertEquals(1, archAfter.getVersion());
        Artifact planAfter = artifacts.current(id, "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN").orElseThrow();
        assertEquals(2, planAfter.getVersion());
        List<Artifact> planVersions = artifacts.list(id).stream()
                .filter(a -> a.getNodeId().equals("IMPLEMENTATION_PLAN")).toList();
        assertEquals(2, planVersions.size(), "history is preserved");
        assertEquals(ArtifactStatus.STALE, planVersions.stream().filter(a -> a.getVersion() == 1).findFirst().orElseThrow().getStatus());
        assertEquals(planBefore.getContent(), planVersions.stream().filter(a -> a.getVersion() == 1).findFirst().orElseThrow().getContent());
        assertTrue(audit.events(id, 0, 100).items().stream().anyMatch(e -> e.getEventType().equals("RUN_REPLANNED")));
        assertEquals("WAITING_FOR_APPROVAL", status(again, "IMPLEMENTATION"), "the high-impact gate applies again");
    }

    @Test
    void replanIsRefusedWhileAnAffectedStageIsRunning() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        // an unknown stage is a 404
        assertEquals("NODE_NOT_FOUND", assertThrows(ApiException.class, () -> control.replan(id, "NOPE", null, HUMAN)).code());
    }

    @Test
    void retryRequeuesAFailedStageAndTheStagesSkippedBecauseOfIt() throws Exception {
        long id = newRun("GREENFIELD");
        RunNode ra = nodes.findByRunIdAndNodeId(id, "REQUIREMENTS_ANALYSIS").orElseThrow();
        // simulate a failed first stage with everything downstream skipped
        ra.setStatus(NodeStatus.FAILED);
        nodes.save(ra);
        for (RunNode n : nodes.findByRunIdOrderById(id)) {
            if (!n.getNodeId().equals("REQUIREMENTS_ANALYSIS")) {
                n.setStatus(NodeStatus.SKIPPED);
                nodes.save(n);
            }
        }
        var run = service.detail(id);
        assertEquals("CREATED", run.status());
        service.start(id);
        // start on a CREATED run with a FAILED node: the scheduler leaves it; retry puts it back in play
        control.retry(id, "REQUIREMENTS_ANALYSIS", HUMAN);
        RunDtos.RunDetail d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("SUCCEEDED", status(d, "REQUIREMENTS_ANALYSIS"));
        assertEquals("SUCCEEDED", status(d, "TASK_DECOMPOSITION"));
        assertEquals("NODE_NOT_RETRYABLE", assertThrows(ApiException.class, () -> control.retry(id, "TASK_DECOMPOSITION", HUMAN)).code());
    }

    @Test
    void rollbackRestoresAnEarlierVersionWithoutDeletingHistory() throws Exception {
        long id = newRun("GREENFIELD");
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        // create a second design version, then restore the first
        Artifact v1 = artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow();
        artifacts.create(id, "ARCHITECTURE_DESIGN", "DESIGN", v1.getContent().replace("[SIMULATED]", "[SIMULATED] (edited)"),
                "alice", List.of(), List.of());
        assertEquals(2, artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow().getVersion());

        Artifact restored = control.rollback(id, "ARCHITECTURE_DESIGN", "DESIGN", 1, HUMAN);
        assertEquals(1, restored.getVersion());
        assertEquals(ArtifactStatus.CURRENT, restored.getStatus());
        Map<Integer, ArtifactStatus> byVersion = artifacts.list(id).stream().filter(a -> a.getNodeId().equals("ARCHITECTURE_DESIGN"))
                .collect(Collectors.toMap(Artifact::getVersion, Artifact::getStatus));
        assertEquals(Map.of(1, ArtifactStatus.CURRENT, 2, ArtifactStatus.SUPERSEDED), byVersion, "both versions kept");
        assertTrue(audit.events(id, 0, 100).items().stream().anyMatch(e -> e.getEventType().equals("ARTIFACT_ROLLBACK")));
        assertEquals("ARTIFACT_VERSION_NOT_FOUND", assertThrows(ApiException.class,
                () -> control.rollback(id, "ARCHITECTURE_DESIGN", "DESIGN", 9, HUMAN)).code());
    }
}
