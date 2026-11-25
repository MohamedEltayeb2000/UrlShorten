package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.governance.PolicyDecisionRecord;
import com.example.shortener.governance.PolicyDecisionRepository;
import com.example.shortener.governance.PolicyOutcome;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** The policy rules for commands, database changes and deployments fire on what the implementer proposes. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FiringPolicyIT.Cfg.class)
@Requirement("001-FR-033")
class FiringPolicyIT {
    static volatile List<String> extraCommands = List.of();
    static volatile List<Map<String, Object>> extraMigrations = List.of();

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        AgentProvider proposingProvider() {
            MockAgentProvider delegate = new MockAgentProvider();
            return new AgentProvider() {
                public String providerName() { return "proposing-test"; }

                public Agent forStage(String stageId) {
                    Agent real = delegate.forStage(stageId);
                    if (!stageId.equals("IMPLEMENTATION")) return real;
                    return new Agent() {
                        public String name() { return real.name(); }
                        public String stageId() { return real.stageId(); }
                        public AgentResult run(AgentInput in) {
                            AgentResult r = real.run(in);
                            Map<String, Object> so = new LinkedHashMap<>(r.structuredOutput());
                            List<Object> cmds = new ArrayList<>((List<?>) so.get("proposedCommands"));
                            cmds.addAll(extraCommands);
                            List<Object> migs = new ArrayList<>((List<?>) so.get("proposedMigrations"));
                            migs.addAll(extraMigrations);
                            so.put("proposedCommands", cmds);
                            so.put("proposedMigrations", migs);
                            return new AgentResult(r.agentName(), r.status(), r.summary(), so, r.assumptions(), r.risks(),
                                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                                    r.referencesToInputs(), r.simulated());
                        }
                    };
                }
            };
        }
    }

    @Autowired RunService service;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired PolicyDecisionRepository policyDecisions;
    @Autowired ApprovalService approvals;

    @BeforeEach
    void reset() {
        extraCommands = List.of();
        extraMigrations = List.of();
    }

    private long startRun() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> service.detail(id).nodes().stream().anyMatch(n -> n.id().equals("IMPLEMENTATION")
                && (n.status().equals("WAITING_FOR_APPROVAL") || n.status().equals("BLOCKED"))), Duration.ofSeconds(30), "implementation to settle");
        return id;
    }

    private String implStatus(long id) {
        return service.detail(id).nodes().stream().filter(n -> n.id().equals("IMPLEMENTATION")).findFirst().orElseThrow().status();
    }

    private void assertHeld(long id, String rule) {
        assertEquals("BLOCKED", implStatus(id));
        assertTrue(audit.events(id, 0, 100).items().stream().anyMatch(e -> e.getEventType().equals("POLICY_DENIED")
                && e.getDetails().contains(rule)));
        assertTrue(policyDecisions.findByRunIdOrderByIdAsc(id).stream()
                .anyMatch(p -> p.getRule().equals(rule) && p.getOutcome() == PolicyOutcome.DENY));
        assertTrue(artifacts.current(id, "IMPLEMENTATION", "CODE_PROPOSAL").isEmpty(), "held output is never stored");
    }

    @Test
    void aDestructiveCommandIsDeniedAndHeld() throws Exception {
        extraCommands = List.of("rm -rf /var/data");
        assertHeld(startRun(), "no-destructive-commands-without-approval");
    }

    @Test
    void aProductionDeploymentCommandIsDeniedAndHeld() throws Exception {
        extraCommands = List.of("kubectl apply -f prod.yaml");
        assertHeld(startRun(), "no-production-deployment");
    }

    @Test
    void aMigrationOnRealDataWaitsForApprovalAndSaysWhy() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "V9__reshape_links");
        m.put("sql", "alter table link add column owner varchar(64)");
        m.put("touchesNonTestData", Boolean.TRUE);
        m.put("destructive", Boolean.FALSE);
        extraMigrations = List.of(m);
        long id = startRun();
        assertEquals("WAITING_FOR_APPROVAL", implStatus(id));
        var pending = approvals.pending().stream().filter(p -> p.runId() == id).findFirst().orElseThrow();
        assertTrue(pending.policyReason().contains("destructive-db-on-real-data"), pending.policyReason());
        assertTrue(pending.policyReason().contains("generated-output-is-proposal"), "the patch approval stays");
        assertEquals("HIGH", pending.riskClass());
        assertTrue(artifacts.current(id, "IMPLEMENTATION", "CODE_PROPOSAL").isPresent());
    }

    @Test
    void theDefaultMockRunRecordsOnlyAllowAndRequireApproval() throws Exception {
        long id = startRun();
        assertEquals("WAITING_FOR_APPROVAL", implStatus(id));
        List<PolicyDecisionRecord> recs = policyDecisions.findByRunIdOrderByIdAsc(id);
        assertTrue(recs.stream().anyMatch(p -> p.getRule().equals("commands-require-approval")));
        assertTrue(recs.stream().anyMatch(p -> p.getRule().equals("destructive-db-on-test-data") && p.getOutcome() == PolicyOutcome.ALLOW));
        assertTrue(recs.stream().noneMatch(p -> p.getOutcome() == PolicyOutcome.DENY));
        assertTrue(audit.events(id, 0, 100).items().stream().map(AuditEvent::getEventType).noneMatch("POLICY_DENIED"::equals));
    }
}
