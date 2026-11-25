package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(AutoReplanWhileRunningIT.Cfg.class)
@Requirement("001-FR-032")
class AutoReplanWhileRunningIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler whileRunningHandler() {
            return new RecordingStageHandler();
        }
    }

    @Autowired RunService service;
    @Autowired ChangeDetectionService changes;
    @Autowired RecordingStageHandler handler;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;
    @Autowired RunNodeRepository nodes;

    @BeforeEach
    void reset() {
        handler.reset();
        handler.setDefaultDelayMs(50);
    }

    @Test
    void editWhileADownstreamStageIsRunningPausesWaitsThenReplansAndTheRunCompletes() throws Exception {
        handler.delay("IMPLEMENTATION", 1200);
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> nodes.findByRunIdAndNodeId(id, "IMPLEMENTATION").orElseThrow().getStatus() == NodeStatus.RUNNING,
                Duration.ofSeconds(20), "IMPLEMENTATION to run");
        // RecordingStageHandler writes no artifacts; give the edited stage one
        artifacts.create(id, "ARCHITECTURE_DESIGN", "DESIGN", "design v1", "agent:ArchitectAgent", List.of(), List.of());

        long t0 = System.currentTimeMillis();
        var v2 = changes.edit(id, new ChangeDetectionService.EditRequest("ARCHITECTURE_DESIGN", "DESIGN", "design v2", "late change"),
                new Actor("alice", ActorType.HUMAN));
        assertEquals(2, v2.getVersion());
        assertTrue(System.currentTimeMillis() - t0 >= 300, "the edit waited for the running stage");

        List<String> events = audit.events(id, 0, 200).items().stream().map(AuditEvent::getEventType).toList();
        assertTrue(events.contains("RUN_PAUSED"));
        assertTrue(events.contains("AUTO_REPLAN"));
        assertEquals(2, service.detail(id).planVersion());

        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(30), "run to finish");
        assertEquals("SUCCEEDED", service.detail(id).status());
        assertEquals(2, handler.executions("IMPLEMENTATION"), "the downstream stage re-ran after the re-plan");
        assertEquals(1, handler.executions("ARCHITECTURE_DESIGN"), "the edited stage did not");
    }
}
