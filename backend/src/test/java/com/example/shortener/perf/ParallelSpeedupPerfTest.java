package com.example.shortener.perf;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.orchestrator.*;
import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Independent stages run concurrently, so wall-clock time beats running the same stages one after another. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ParallelSpeedupPerfTest.Cfg.class)
@Tag("perf")
@Requirement("002-FR-015")
class ParallelSpeedupPerfTest {
    static final long STAGE_MS = 600; // long enough that fixed scheduling and audit overhead does not dominate

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler perfHandler() {
            RecordingStageHandler h = new RecordingStageHandler();
            h.setDefaultDelayMs(STAGE_MS);
            return h;
        }
    }

    @Autowired RunService service;
    @Autowired Workflows workflows;
    @Autowired RecordingStageHandler handler;

    @Test
    void fanOutOfFourStagesIsFasterThanRunningThemSequentially() throws Exception {
        List<NodeDef> nodes = new ArrayList<>();
        nodes.add(new NodeDef("FAN_START", "Start", List.of(), false));
        List<String> branches = List.of("FAN_1", "FAN_2", "FAN_3", "FAN_4");
        branches.forEach(b -> nodes.add(new NodeDef(b, b, List.of("FAN_START"), false)));
        nodes.add(new NodeDef("FAN_JOIN", "Join", branches, false));
        workflows.register(new WorkflowDefinition("fanout-perf", nodes));

        long sequentialMs = nodes.size() * STAGE_MS; // the same stages one after another
        double ratioBudget = new ObjectMapper().readTree(RepoFiles.read("docs/perf/budgets.json")).get("parallel_ratio_max").asDouble();

        // Warm-up: the first run in a fresh JVM pays one-off JPA/JIT start-up costs that are not what this budget measures.
        handler.setDefaultDelayMs(0);
        long warm = service.create("Warm-up", null, "fanout-perf").id();
        service.start(warm);
        await(() -> terminal(service.detail(warm).status()), Duration.ofSeconds(30), "warm-up run to finish");
        handler.setDefaultDelayMs(STAGE_MS);

        long id = service.create("Parallel speed-up", null, "fanout-perf").id();
        long t0 = System.nanoTime();
        service.start(id);
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(30), "fan-out run to finish");
        long concurrentMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("SUCCEEDED", service.detail(id).status());
        double ratio = concurrentMs / (double) sequentialMs;
        RepoFiles.recordPerf("parallel/sequential time ratio (6 stages, 4 parallel)", ratioBudget, String.format("%.2f", ratio), ratio < ratioBudget);
        assertTrue(ratio < ratioBudget, "concurrent " + concurrentMs + " ms vs sequential " + sequentialMs + " ms (ratio " + ratio + ")");
    }
}
