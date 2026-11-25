package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
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

/** Pausing while a stage runs: the stage stops cooperatively, returns to READY without failing, and re-runs once on resume. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SafeStopIT.Cfg.class)
@Requirement("001-FR-034")
class SafeStopIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler recordingStageHandler() {
            return new RecordingStageHandler();
        }
    }

    @Autowired RunService service;
    @Autowired RecordingStageHandler handler;
    @Autowired RunNodeRepository nodes;
    @Autowired StageAttemptRepository attempts;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;

    @BeforeEach
    void reset() {
        handler.reset();
        handler.setDefaultDelayMs(20);
    }

    private static String status(RunDtos.RunDetail d, String nodeId) {
        return d.nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow().status();
    }

    @Test
    void pausedRunningStageReturnsToReadyThenRunsOnceOnResume() throws Exception {
        handler.holdUntilCancelled("TASK_DECOMPOSITION");
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> status(service.detail(id), "TASK_DECOMPOSITION").equals("RUNNING"), Duration.ofSeconds(10), "stage running");

        service.pause(id);
        await(() -> status(service.detail(id), "TASK_DECOMPOSITION").equals("READY"), Duration.ofSeconds(10), "stage back to READY");

        RunDtos.RunDetail paused = service.detail(id);
        assertEquals("PAUSED", paused.status());
        assertTrue(paused.nodes().stream().noneMatch(n -> n.status().equals("FAILED")));
        assertTrue(artifacts.list(id).isEmpty(), "an interrupted stage stores nothing");
        assertEquals(1, handler.executions("TASK_DECOMPOSITION"));
        RunNode node = nodes.findByRunIdAndNodeId(id, "TASK_DECOMPOSITION").orElseThrow();
        assertEquals(1, node.getAttemptCount(), "history stays honest: the interrupted attempt was counted");
        assertEquals(List.of("INTERRUPTED"), attempts.findByRunNodeIdOrderByAttemptAsc(node.getId()).stream().map(StageAttempt::getStatus).toList());
        List<AuditEvent> ev = audit.events(id, 0, 100).items();
        assertTrue(ev.stream().noneMatch(e -> e.getEventType().equals("STAGE_RETRY") || e.getEventType().equals("NODE_FAILED")));
        assertTrue(ev.stream().anyMatch(e -> e.getEventType().equals("NODE_READY") && e.getDetails().contains("interrupted by safe-stop")));

        service.start(id);
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(20), "run to finish");
        assertEquals("SUCCEEDED", service.detail(id).status());
        assertEquals(2, handler.executions("TASK_DECOMPOSITION"), "one interrupted invocation and one that completed");
        assertEquals(1, handler.executions("REQUIREMENTS_ANALYSIS"));
        assertEquals(List.of("INTERRUPTED", "SUCCEEDED"),
                attempts.findByRunNodeIdOrderByAttemptAsc(node.getId()).stream().map(StageAttempt::getStatus).toList());
    }
}
