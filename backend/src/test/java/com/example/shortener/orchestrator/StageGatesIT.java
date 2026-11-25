package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Real runs with the agent handler: every stage records entry and exit gates; failed gates stop the stage as specified. */
@SpringBootTest
@ActiveProfiles("test")
@Import(StageGatesIT.Cfg.class)
@Requirement("001-FR-031")
class StageGatesIT {
    static volatile CountDownLatch planLatch;
    static volatile String leakStage;
    static volatile String skipStoreStage;

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        AgentProvider testProvider() {
            MockAgentProvider delegate = new MockAgentProvider();
            return new AgentProvider() {
                public String providerName() { return "gates-test"; }

                public Agent forStage(String stageId) {
                    Agent real = delegate.forStage(stageId);
                    return new Agent() {
                        public String name() { return real.name(); }
                        public String stageId() { return real.stageId(); }
                        public AgentResult run(AgentInput in) {
                            CountDownLatch l = planLatch;
                            if (l != null && stageId.equals("IMPLEMENTATION_PLAN")) {
                                try { l.await(20, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            }
                            AgentResult r = real.run(in);
                            if (!stageId.equals(leakStage)) return r;
                            return new AgentResult(r.agentName(), r.status(), r.summary() + " api_key = abcd1234efgh5678",
                                    r.structuredOutput(), r.assumptions(), r.risks(), r.openQuestions(),
                                    r.recommendedNextActions(), r.confidence(), r.confidenceNote(), r.referencesToInputs(), r.simulated());
                        }
                    };
                }
            };
        }

        /** Delegates to the real agent handler, except that one stage returns normally without storing anything. */
        @Bean
        @Primary
        StageHandler storingNothingHandler(AgentStageHandler real) {
            return new StageHandler() {
                public void execute(StageExecution ex) throws Exception {
                    if (ex.nodeId().equals(skipStoreStage)) return;
                    real.execute(ex);
                }
                public boolean producesArtifacts() { return true; }
                public boolean supportsFallback() { return true; }
                public void executeFallback(StageExecution ex) throws Exception { real.executeFallback(ex); }
                public String providerName() { return real.providerName(); }
            };
        }
    }

    @Autowired RunService service;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired ApprovalService approvalService;

    @AfterEach
    void clear() {
        planLatch = null;
        leakStage = null;
        skipStoreStage = null;
    }

    private static RunDtos.NodeView node(RunDtos.RunDetail d, String id) {
        return d.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    private List<AuditEvent> gateEvents(long runId, String type) {
        return audit.eventsOfTypes(runId, List.of(type));
    }

    @Test
    void everyStageRecordsPassedEntryAndExitGatesAndTheRunDetailShowsThem() throws Exception {
        long id = service.create("Build a URL shortener API with link creation, redirects, expiration, and click analytics.", "GREENFIELD").id();
        service.start(id);
        Actor reviewer = new Actor("reviewer", ActorType.HUMAN);
        for (int round = 0; round < 6; round++) {
            await(() -> {
                String s = service.detail(id).status();
                return terminal(s) || s.equals("WAITING_FOR_APPROVAL");
            }, Duration.ofSeconds(30), "run to finish or wait");
            var pending = approvalService.pending().stream().filter(p -> p.runId() == id && p.kind().equals("HIGH_IMPACT_ACTION")).toList();
            if (pending.isEmpty()) break;
            for (var p : pending) {
                approvalService.decide(id, com.example.shortener.support.ChangeRecords.req(p.nodeId(), "APPROVE", "reviewed in test", p.artifactVersion()), reviewer);
            }
        }
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(30), "run to finish");
        RunDtos.RunDetail d = service.detail(id);
        assertEquals("SUCCEEDED", d.status());

        assertEquals(8, gateEvents(id, "GATE_ENTRY_PASSED").size());
        assertEquals(8, gateEvents(id, "GATE_EXIT_PASSED").size());
        assertTrue(gateEvents(id, "GATE_ENTRY_FAILED").isEmpty());
        assertTrue(gateEvents(id, "GATE_EXIT_FAILED").isEmpty());
        assertTrue(gateEvents(id, "GATE_ENTRY_PASSED").stream().anyMatch(e -> e.getDetails().startsWith("RELEASE_READINESS :: ")));

        assertEquals(8, d.nodes().size());
        for (RunDtos.NodeView n : d.nodes()) {
            assertNotNull(n.gates(), n.id());
            assertEquals(Boolean.TRUE, n.gates().entry().passed(), n.id() + " entry");
            assertEquals(Boolean.TRUE, n.gates().exit().passed(), n.id() + " exit");
            assertFalse(n.gates().entry().checks().isEmpty());
            assertTrue(n.gates().exit().checks().stream().allMatch(c -> c.endsWith(": passed")));
        }
    }

    @Test
    void stalePrerequisiteArtifactFailsTheEntryGateAndTheStageNeverRuns() throws Exception {
        planLatch = new CountDownLatch(1);
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> node(service.detail(id), "ARCHITECTURE_DESIGN").status().equals("SUCCEEDED"), Duration.ofSeconds(20), "design done");
        Artifact design = artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow();
        artifacts.markStale(List.of(design.getId()));
        planLatch.countDown();

        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(30), "run to end");
        RunDtos.RunDetail d = service.detail(id);
        RunDtos.NodeView impl = node(d, "IMPLEMENTATION");
        assertEquals("SKIPPED", impl.status());
        assertEquals(0, impl.attempts(), "an entry-gate failure keeps the stage out of RUNNING");
        assertTrue(impl.errorSummary().contains("Entry gate failed"));
        assertTrue(impl.errorSummary().contains("prerequisite artifacts are current"));
        assertEquals(Boolean.FALSE, impl.gates().entry().passed());
        assertNull(impl.gates().exit().passed());
        assertTrue(gateEvents(id, "GATE_ENTRY_FAILED").stream().anyMatch(e -> e.getDetails().startsWith("IMPLEMENTATION :: ")));
        assertEquals("FAILED", d.status(), "a failed gate is not a silent success");
        assertTrue(artifacts.current(id, "IMPLEMENTATION", "CODE_PROPOSAL").isEmpty());
    }

    @Test
    void handlerThatStoresNothingFailsTheExitGate() throws Exception {
        skipStoreStage = "DOCUMENTATION";
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> node(service.detail(id), "DOCUMENTATION").status().equals("FAILED"), Duration.ofSeconds(30), "documentation to fail");

        RunDtos.NodeView doc = node(service.detail(id), "DOCUMENTATION");
        assertTrue(doc.errorSummary().contains("output stored as an artifact"), doc.errorSummary());
        assertEquals(Boolean.TRUE, doc.gates().entry().passed());
        assertEquals(Boolean.FALSE, doc.gates().exit().passed());
        assertTrue(doc.gates().exit().checks().contains("output stored as an artifact: failed"));
        assertTrue(gateEvents(id, "GATE_EXIT_FAILED").stream().anyMatch(e -> e.getDetails().startsWith("DOCUMENTATION :: ")));
        assertTrue(artifacts.current(id, "DOCUMENTATION", "DOCUMENTATION").isEmpty());
    }

    @Test
    void outputFailingEvaluationFailsTheExitGateWithoutLeakingTheSecret() throws Exception {
        leakStage = "DOCUMENTATION";
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> node(service.detail(id), "DOCUMENTATION").status().equals("BLOCKED"), Duration.ofSeconds(30), "documentation held");

        RunDtos.NodeView doc = node(service.detail(id), "DOCUMENTATION");
        assertEquals(Boolean.FALSE, doc.gates().exit().passed());
        assertTrue(doc.gates().exit().checks().contains("output passed evaluation: failed"), doc.gates().exit().checks().toString());
        assertTrue(audit.eventsOfTypes(id, List.of("GATE_EXIT_FAILED", "GATE_ENTRY_PASSED")).stream()
                .noneMatch(e -> e.getDetails().contains("abcd1234efgh5678")));
    }
}
