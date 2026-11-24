package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
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
@Import(RunExecutionIT.Cfg.class)
@Requirement("001-FR-001")
@Requirement("001-FR-003")
@Requirement("001-FR-004")
@Requirement("001-FR-006")
@Requirement("001-FR-007")
class RunExecutionIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler recordingStageHandler() {
            return new RecordingStageHandler();
        }
    }

    @Autowired RunService service;
    @Autowired RunScheduler scheduler;
    @Autowired RecordingStageHandler handler;
    @Autowired AgentRunRepository runs;
    @Autowired RunNodeRepository nodes;
    @Autowired NodeTransitionRepository history;

    @BeforeEach
    void reset() {
        handler.reset();
    }

    private long newRun() {
        return service.create("Build a URL shortener API", "GREENFIELD").id();
    }

    private RunDtos.RunDetail awaitTerminal(long id) throws InterruptedException {
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(20), "run " + id + " to finish");
        return service.detail(id);
    }

    private static String nodeStatus(RunDtos.RunDetail d, String nodeId) {
        return d.nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow().status();
    }

    @Test
    void fullRunSucceedsInDependencyOrderWithParallelBranchesAndJoin() throws Exception {
        long id = newRun();
        service.start(id);
        RunDtos.RunDetail d = awaitTerminal(id);

        assertEquals("SUCCEEDED", d.status());
        assertEquals(8, d.nodes().size());
        assertTrue(d.nodes().stream().allMatch(n -> n.status().equals("SUCCEEDED") && n.attempts() == 1));
        assertNotNull(d.startedAt());
        assertNotNull(d.completedAt());

        Map<String, List<String>> deps = d.nodes().stream()
                .collect(Collectors.toMap(RunDtos.NodeView::id, RunDtos.NodeView::dependsOn));
        for (var e : deps.entrySet()) {
            for (String dep : e.getValue()) {
                assertTrue(handler.event(e.getKey()).startNs() >= handler.event(dep).endNs(),
                        e.getKey() + " started before its prerequisite " + dep + " finished");
            }
        }
        // independent branches really overlapped
        assertTrue(handler.event("ARCHITECTURE_DESIGN").overlaps(handler.event("IMPLEMENTATION_PLAN")),
                "architecture and implementation plan must run concurrently");
        assertTrue(handler.event("DOCUMENTATION").overlaps(handler.event("IMPLEMENTATION")),
                "documentation runs in parallel with implementation");
        // every stage executed exactly once
        d.nodes().forEach(n -> assertEquals(1, handler.executions(n.id()), n.id()));
    }

    @Test
    void transitionHistoryIsPersistedForEveryNode() throws Exception {
        long id = newRun();
        service.start(id);
        awaitTerminal(id);
        for (RunNode n : nodes.findByRunIdOrderById(id)) {
            List<String> path = history.findByRunNodeIdOrderByIdAsc(n.getId()).stream()
                    .map(t -> (t.getFromStatus() == null ? "-" : t.getFromStatus().name()) + ">" + t.getToStatus().name())
                    .toList();
            assertEquals(List.of("->PENDING".replace("->", "-"+">"), "PENDING>READY", "READY>RUNNING", "RUNNING>SUCCEEDED"), path,
                    n.getNodeId());
        }
    }

    @Test
    void joinNodeWaitsForTheSlowerPrerequisiteWhileOtherPathsProceed() throws Exception {
        handler.delay("IMPLEMENTATION_PLAN", 500);
        handler.delay("ARCHITECTURE_DESIGN", 50);
        long id = newRun();
        service.start(id);
        awaitTerminal(id);

        assertTrue(handler.event("IMPLEMENTATION").startNs() >= handler.event("IMPLEMENTATION_PLAN").endNs(),
                "join must wait for the slower prerequisite");
        assertTrue(handler.event("DOCUMENTATION").startNs() < handler.event("IMPLEMENTATION_PLAN").endNs(),
                "documentation only needs the architecture and must not wait for the plan");
    }

    @Test
    void pauseStopsNewSchedulingAndResumeCompletesTheRunWithoutRerunningStages() throws Exception {
        CountDownLatch gate = handler.holdAt("REQUIREMENTS_ANALYSIS");
        long id = newRun();
        service.start(id);
        await(() -> nodeStatus(service.detail(id), "REQUIREMENTS_ANALYSIS").equals("RUNNING"), Duration.ofSeconds(10), "first stage running");

        assertEquals("PAUSED", service.pause(id).status());
        gate.countDown(); // the running stage is allowed to finish
        await(() -> nodeStatus(service.detail(id), "REQUIREMENTS_ANALYSIS").equals("SUCCEEDED"), Duration.ofSeconds(10), "first stage done");
        Thread.sleep(400); // give a buggy scheduler time to start the next stage

        RunDtos.RunDetail paused = service.detail(id);
        assertEquals("PAUSED", paused.status());
        assertEquals("PENDING", nodeStatus(paused, "TASK_DECOMPOSITION"), "nothing new may start while paused");
        assertEquals(0, handler.executions("TASK_DECOMPOSITION"));

        service.start(id); // resume
        RunDtos.RunDetail done = awaitTerminal(id);
        assertEquals("SUCCEEDED", done.status());
        assertEquals(1, handler.executions("REQUIREMENTS_ANALYSIS"), "finished stage must not rerun");
        assertEquals(1, handler.executions("TASK_DECOMPOSITION"));
    }

    @Test
    void failedStageSkipsItsDependentsAndFailsTheRunWithoutAutomaticRetry() throws Exception {
        handler.failOn("TASK_DECOMPOSITION");
        long id = newRun();
        service.start(id);
        RunDtos.RunDetail d = awaitTerminal(id);

        assertEquals("FAILED", d.status());
        assertEquals("SUCCEEDED", nodeStatus(d, "REQUIREMENTS_ANALYSIS"));
        assertEquals("FAILED", nodeStatus(d, "TASK_DECOMPOSITION"));
        for (String skipped : List.of("ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN", "IMPLEMENTATION", "TESTING",
                "DOCUMENTATION", "RELEASE_READINESS")) {
            assertEquals("SKIPPED", nodeStatus(d, skipped), skipped);
            assertEquals(0, handler.executions(skipped), skipped + " must not run");
        }
        assertEquals(1, handler.executions("TASK_DECOMPOSITION"), "automatic retry arrives with the governance phase");
        assertTrue(d.nodes().stream().filter(n -> n.id().equals("TASK_DECOMPOSITION")).findFirst().orElseThrow()
                .errorSummary().contains("injected failure"));
    }

    @Test
    void restartMarksInterruptedStagesBlockedPausesTheRunAndResumeRunsEachStageOnce() throws Exception {
        long id = newRun();
        // Simulate a crash: the run is RUNNING, the first stage finished, the second was RUNNING when the app died.
        List<RunNode> all = nodes.findByRunIdOrderById(id);
        RunNode ra = all.stream().filter(n -> n.getNodeId().equals("REQUIREMENTS_ANALYSIS")).findFirst().orElseThrow();
        RunNode td = all.stream().filter(n -> n.getNodeId().equals("TASK_DECOMPOSITION")).findFirst().orElseThrow();
        ra.setStatus(NodeStatus.SUCCEEDED);
        nodes.save(ra);
        td.setStatus(NodeStatus.RUNNING);
        td.setAttemptCount(1);
        nodes.save(td);
        AgentRun run = runs.findById(id).orElseThrow();
        run.setStatus(RunStatus.RUNNING);
        runs.save(run);

        scheduler.recoverInterruptedRuns();

        RunDtos.RunDetail recovered = service.detail(id);
        assertEquals("PAUSED", recovered.status());
        assertEquals("BLOCKED", nodeStatus(recovered, "TASK_DECOMPOSITION"));
        assertEquals(0, handler.executions("TASK_DECOMPOSITION"), "recovery must not execute anything");

        service.start(id); // operator resumes
        RunDtos.RunDetail done = awaitTerminal(id);
        assertEquals("SUCCEEDED", done.status());
        assertEquals(0, handler.executions("REQUIREMENTS_ANALYSIS"), "already succeeded before the crash");
        assertEquals(1, handler.executions("TASK_DECOMPOSITION"), "interrupted stage runs exactly once after resume");
        done.nodes().forEach(n -> assertTrue(handler.executions(n.id()) <= 1, n.id()));
    }
}
