package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** An agent output that leaks a secret must be held: BLOCKED, flagged in the audit trail, never stored or consumed. */
@SpringBootTest
@ActiveProfiles("test")
@Import(HeldOutputIT.Cfg.class)
@Requirement("001-FR-009")
@Requirement("002-FR-012")
class HeldOutputIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        AgentProvider leakyProvider() {
            MockAgentProvider delegate = new MockAgentProvider();
            return new AgentProvider() {
                public String providerName() { return "leaky-test"; }

                public Agent forStage(String stageId) {
                    Agent real = delegate.forStage(stageId);
                    if (!stageId.equals("ARCHITECTURE_DESIGN")) return real;
                    return new Agent() {
                        public String name() { return real.name(); }
                        public String stageId() { return real.stageId(); }
                        public AgentResult run(AgentInput in) {
                            AgentResult r = real.run(in);
                            return new AgentResult(r.agentName(), r.status(), r.summary() + " api_key = abcd1234efgh5678",
                                    r.structuredOutput(), r.assumptions(), r.risks(), r.openQuestions(),
                                    r.recommendedNextActions(), r.confidence(), r.confidenceNote(), r.referencesToInputs(), r.simulated());
                        }
                    };
                }
            };
        }
    }

    @Autowired RunService service;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;

    @Test
    void outputFailingEvaluationIsHeldFlaggedAndNotUsedDownstream() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(30), "run to stop on the held stage");

        RunDtos.RunDetail d = service.detail(id);
        var byId = d.nodes().stream().collect(Collectors.toMap(RunDtos.NodeView::id, n -> n));
        assertEquals("BLOCKED", byId.get("ARCHITECTURE_DESIGN").status());
        assertTrue(byId.get("ARCHITECTURE_DESIGN").errorSummary().contains("no_unsafe_content"));
        assertFalse(byId.get("ARCHITECTURE_DESIGN").errorSummary().contains("abcd1234efgh5678"), "the secret must not be echoed");
        assertEquals("SUCCEEDED", byId.get("IMPLEMENTATION_PLAN").status(), "independent branch is unaffected");
        assertEquals("PENDING", byId.get("IMPLEMENTATION").status(), "downstream of a held output must not run");
        assertTrue(artifacts.current(id, "ARCHITECTURE_DESIGN", "DESIGN").isEmpty(), "held output is never stored");

        List<AuditEvent> ev = audit.events(id, 0, 100).items();
        assertTrue(ev.stream().anyMatch(e -> e.getEventType().equals("OUTPUT_FLAGGED")));
        assertTrue(ev.stream().noneMatch(e -> e.getDetails() != null && e.getDetails().contains("abcd1234efgh5678")));
    }
}
