package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.artifacts.ArtifactStatus;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** End to end with the real agent handler: mock agents, evaluation, versioned artifacts with lineage, audit trail. */
@SpringBootTest(properties = "shortener.orchestrator.max-parallel=4")
@ActiveProfiles("test")
@Requirement("001-FR-005")
@Requirement("001-FR-008")
@Requirement("001-FR-009")
@Requirement("001-FR-020")
class AgentRunIT {
    @Autowired RunService service;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired RunNodeRepository nodes;
    @Autowired StageAttemptRepository attempts;

    private RunDtos.RunDetail runToEnd(String requirement, String scenario) throws Exception {
        long id = service.create(requirement, scenario).id();
        service.start(id);
        await(() -> {
            String s = service.detail(id).status();
            return terminal(s) || s.equals("WAITING_FOR_APPROVAL");
        }, Duration.ofSeconds(30), "run to finish or wait");
        return service.detail(id);
    }

    private List<String> events(long runId) {
        return audit.events(runId, 0, 100).items().stream().map(AuditEvent::getEventType).toList();
    }

    @Test
    void greenfieldRunStoresOneCurrentArtifactPerStageWithLineageAndAudit() throws Exception {
        RunDtos.RunDetail d = runToEnd("Build a URL shortener API with link creation, redirects, expiration, and click analytics.", "GREENFIELD");
        assertEquals("SUCCEEDED", d.status());

        List<Artifact> all = artifacts.list(d.id());
        assertEquals(8, all.size());
        assertTrue(all.stream().allMatch(a -> a.getStatus() == ArtifactStatus.CURRENT && a.getVersion() == 1));
        Map<String, Artifact> byNode = all.stream().collect(Collectors.toMap(Artifact::getNodeId, a -> a));
        assertEquals("CODE_PROPOSAL", byNode.get("IMPLEMENTATION").getArtifactType());
        assertTrue(byNode.get("IMPLEMENTATION").getContent().contains("\"applied\":false"), "generated code is a proposal, never applied");
        assertTrue(byNode.get("ARCHITECTURE_DESIGN").getContent().contains("[SIMULATED]"));

        // lineage: the join depends on both prerequisites' artifacts
        String parents = byNode.get("IMPLEMENTATION").getParentArtifactIds();
        assertTrue(parents.contains(String.valueOf(byNode.get("ARCHITECTURE_DESIGN").getId())));
        assertTrue(parents.contains(String.valueOf(byNode.get("IMPLEMENTATION_PLAN").getId())));
        assertTrue(byNode.get("IMPLEMENTATION").getInputRefs().contains("ARCHITECTURE_DESIGN:v1"));

        List<String> ev = events(d.id());
        assertTrue(ev.contains("RUN_CREATED") && ev.contains("RUN_STARTED") && ev.contains("RUN_SUCCEEDED"));
        assertEquals(8, ev.stream().filter("ARTIFACT_CREATED"::equals).count());
        assertEquals(8, ev.stream().filter("NODE_SUCCEEDED"::equals).count());

        for (RunNode n : nodes.findByRunIdOrderById(d.id())) {
            var a = attempts.findByRunNodeIdOrderByAttemptAsc(n.getId());
            assertEquals(1, a.size(), n.getNodeId());
            assertEquals("mock", a.get(0).getProviderUsed());
            assertEquals("SUCCEEDED", a.get(0).getStatus());
        }
    }

    @Test
    void brownfieldRunProposesIdempotencyGroundedInTheFixture() throws Exception {
        RunDtos.RunDetail d = runToEnd("Add idempotent link creation and prevent duplicate links when clients retry requests.", "BROWNFIELD");
        assertEquals("SUCCEEDED", d.status());
        String proposal = artifacts.current(d.id(), "IMPLEMENTATION", "CODE_PROPOSAL").orElseThrow().getContent();
        assertTrue(proposal.toLowerCase().contains("idempoten"));
        assertTrue(proposal.contains("LinkService"), "proposal must reference the seeded fixture files");
    }

    @Test
    void ambiguousRunPausesForAHumanDecisionAndDownstreamStagesDoNotRun() throws Exception {
        RunDtos.RunDetail d = runToEnd("Make URL links safer and improve analytics.", "AMBIGUOUS");
        assertEquals("WAITING_FOR_APPROVAL", d.status());
        var byId = d.nodes().stream().collect(Collectors.toMap(RunDtos.NodeView::id, n -> n));
        assertEquals("WAITING_FOR_APPROVAL", byId.get("REQUIREMENTS_ANALYSIS").status());
        assertEquals("PENDING", byId.get("TASK_DECOMPOSITION").status());

        String analysis = artifacts.current(d.id(), "REQUIREMENTS_ANALYSIS", "REQUIREMENT").orElseThrow().getContent();
        assertTrue(analysis.contains("NEEDS_CLARIFICATION") && analysis.contains("openQuestions"));
        assertTrue(events(d.id()).contains("CLARIFICATION_NEEDED"));
        assertEquals(1, artifacts.list(d.id()).size(), "nothing downstream may be produced before the decision");
    }
}
