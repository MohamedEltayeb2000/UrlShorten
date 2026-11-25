package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Editing or rolling back an upstream artifact re-plans the downstream stages automatically, with the real agents. */
@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-032")
class AutoReplanIT {
    static final Actor HUMAN = new Actor("alice", ActorType.HUMAN);
    static final String DESIGN = "ARCHITECTURE_DESIGN";

    @Autowired RunService service;
    @Autowired RunControlService control;
    @Autowired ChangeDetectionService changes;
    @Autowired ApprovalService approvalService;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;

    private RunDtos.RunDetail awaitStatus(long id, String... statuses) throws Exception {
        List<String> wanted = List.of(statuses);
        await(() -> wanted.contains(service.detail(id).status()), Duration.ofSeconds(30), "run to reach " + wanted);
        return service.detail(id);
    }

    private static String status(RunDtos.RunDetail d, String node) {
        return d.nodes().stream().filter(n -> n.id().equals(node)).findFirst().orElseThrow().status();
    }

    private void approve(long id, String node) {
        approvalService.decide(id, com.example.shortener.support.ChangeRecords.req(node, "APPROVE", "looks good", null), HUMAN);
    }

    private long completedRun() throws Exception {
        long id = service.create("Build a URL shortener API with link creation, redirects, expiration, and click analytics.", "GREENFIELD").id();
        service.start(id);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approve(id, "IMPLEMENTATION");
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approve(id, "RELEASE_READINESS");
        awaitStatus(id, "SUCCEEDED");
        return id;
    }

    private Artifact current(long id, String node, String type) {
        return artifacts.current(id, node, type).orElseThrow();
    }

    private String editedDesign(long id) {
        return current(id, DESIGN, "DESIGN").getContent().replace("[SIMULATED]", "[SIMULATED] (edited)");
    }

    private ChangeDetectionService.EditRequest edit(String node, String type, String content, String comment) {
        return new ChangeDetectionService.EditRequest(node, type, content, comment);
    }

    private List<String> events(long id) {
        return com.example.shortener.support.AuditTestSupport.types(audit, id);
    }

    @Test
    void editingTheDesignReplansDownstreamStagesAndReappliesTheGates() throws Exception {
        long id = completedRun();
        Artifact designV1 = current(id, DESIGN, "DESIGN");
        Artifact planV1 = current(id, "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN");
        Artifact reqV1 = current(id, "REQUIREMENTS_ANALYSIS", "REQUIREMENT");
        Artifact implV1 = current(id, "IMPLEMENTATION", "CODE_PROPOSAL");
        int planVersion = service.detail(id).planVersion();

        Artifact v2 = changes.edit(id, edit(DESIGN, "DESIGN", editedDesign(id), "tighten the design"), HUMAN);
        assertEquals(2, v2.getVersion());
        assertEquals("alice", v2.getCreatedBy());
        assertEquals(ArtifactStatus.CURRENT, current(id, DESIGN, "DESIGN").getStatus());
        assertEquals(v2.getId(), current(id, DESIGN, "DESIGN").getId());
        assertEquals(designV1.getParentArtifactIds(), v2.getParentArtifactIds());

        assertEquals(planVersion + 1, service.detail(id).planVersion());
        RunDtos.RunDetail d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("WAITING_FOR_APPROVAL", status(d, "IMPLEMENTATION"), "the high-impact gate applies again");
        assertEquals("SUCCEEDED", status(d, DESIGN), "the edited stage stays succeeded");
        assertEquals(ArtifactStatus.SUPERSEDED, artifacts.list(id).stream().filter(a -> a.getId().equals(designV1.getId())).findFirst().orElseThrow().getStatus());

        Artifact implV2 = current(id, "IMPLEMENTATION", "CODE_PROPOSAL");
        assertEquals(2, implV2.getVersion());
        assertTrue(implV2.getParentArtifactIds().contains(String.valueOf(v2.getId())), "downstream used the edited design");
        assertEquals(ArtifactStatus.STALE, artifacts.list(id).stream().filter(a -> a.getId().equals(implV1.getId())).findFirst().orElseThrow().getStatus());

        approve(id, "IMPLEMENTATION");
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approve(id, "RELEASE_READINESS");
        awaitStatus(id, "SUCCEEDED");
        for (String[] s : new String[][] {{"TESTING", "TEST_REPORT"}, {"DOCUMENTATION", "DOCUMENTATION"}, {"RELEASE_READINESS", "RELEASE_CHECKLIST"}}) {
            assertEquals(2, current(id, s[0], s[1]).getVersion(), s[0] + " re-ran");
        }
        assertEquals(planV1.getId(), current(id, "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN").getId(), "plan untouched");
        assertEquals(reqV1.getId(), current(id, "REQUIREMENTS_ANALYSIS", "REQUIREMENT").getId(), "requirements untouched");
        assertTrue(events(id).contains("AUTO_REPLAN"));
        assertTrue(events(id).contains("ARTIFACT_EDITED"));
    }

    @Test
    void rollingBackAnUpstreamArtifactTriggersTheSameReplan() throws Exception {
        long id = completedRun();
        Artifact v1 = current(id, DESIGN, "DESIGN");
        changes.edit(id, edit(DESIGN, "DESIGN", editedDesign(id), "edit first"), HUMAN);
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approve(id, "IMPLEMENTATION");
        awaitStatus(id, "WAITING_FOR_APPROVAL");
        approve(id, "RELEASE_READINESS");
        awaitStatus(id, "SUCCEEDED");
        int planVersion = service.detail(id).planVersion();

        Artifact restored = control.rollback(id, DESIGN, "DESIGN", 1, HUMAN);
        assertEquals(v1.getId(), restored.getId());
        assertEquals(planVersion + 1, service.detail(id).planVersion());
        RunDtos.RunDetail d = awaitStatus(id, "WAITING_FOR_APPROVAL");
        assertEquals("WAITING_FOR_APPROVAL", status(d, "IMPLEMENTATION"));
        assertEquals(3, current(id, "IMPLEMENTATION", "CODE_PROPOSAL").getVersion());
        assertTrue(current(id, "IMPLEMENTATION", "CODE_PROPOSAL").getParentArtifactIds().contains(String.valueOf(v1.getId())));
        assertEquals(2, events(id).stream().filter("AUTO_REPLAN"::equals).count());
    }

    @Test
    void editingALeafStageReplansNothing() throws Exception {
        long id = completedRun();
        int planVersion = service.detail(id).planVersion();
        Artifact v2 = changes.edit(id, edit("RELEASE_READINESS", "RELEASE_CHECKLIST",
                current(id, "RELEASE_READINESS", "RELEASE_CHECKLIST").getContent().replace("[SIMULATED]", "[SIMULATED] (edited)"), "note"), HUMAN);
        assertEquals(2, v2.getVersion());
        RunDtos.RunDetail d = service.detail(id);
        assertEquals(planVersion, d.planVersion());
        assertEquals("SUCCEEDED", d.status());
        assertFalse(events(id).contains("AUTO_REPLAN"));
        assertTrue(events(id).contains("ARTIFACT_EDITED"));
    }

    @Test
    void agentsCannotEdit() throws Exception {
        long id = completedRun();
        ApiException e = assertThrows(ApiException.class, () -> changes.edit(id,
                edit(DESIGN, "DESIGN", editedDesign(id), "x"), new Actor("agent:Implementer", ActorType.AGENT)));
        assertEquals(403, e.status().value());
        assertEquals(1, current(id, DESIGN, "DESIGN").getVersion());
    }

    @Test
    void aSecretIsRefusedWithoutEchoAndStoresNothing() throws Exception {
        long id = completedRun();
        String secret = "ghp_" + "a".repeat(36);
        ApiException e = assertThrows(ApiException.class, () -> changes.edit(id, edit(DESIGN, "DESIGN", "token " + secret, "x"), HUMAN));
        assertEquals("POLICY_DENIED", e.code());
        assertEquals(400, e.status().value());
        assertFalse(e.getMessage().contains(secret));
        assertEquals(1, artifacts.list(id).stream().filter(a -> a.getNodeId().equals(DESIGN)).count());
    }

    @Test
    void missingCurrentArtifactIs409AndEmptyCommentIs400() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        ApiException none = assertThrows(ApiException.class, () -> changes.edit(id, edit(DESIGN, "DESIGN", "x", "c"), HUMAN));
        assertEquals(409, none.status().value());
        ApiException noComment = assertThrows(ApiException.class, () -> changes.edit(id, edit(DESIGN, "DESIGN", "x", "  "), HUMAN));
        assertEquals(400, noComment.status().value());
        assertEquals("COMMENT_REQUIRED", noComment.code());
    }
}
