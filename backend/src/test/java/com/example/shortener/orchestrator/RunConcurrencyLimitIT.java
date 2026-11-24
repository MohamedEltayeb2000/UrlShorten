package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** With max-parallel=1 independent stages must be serialised: the concurrency limit is honoured. */
@SpringBootTest(properties = "shortener.orchestrator.max-parallel=1")
@ActiveProfiles("test")
@Import(RunConcurrencyLimitIT.Cfg.class)
@Requirement("001-FR-004")
class RunConcurrencyLimitIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler limitedHandler() {
            return new RecordingStageHandler();
        }
    }

    @Autowired RunService service;
    @Autowired RecordingStageHandler handler;

    @Test
    void independentStagesDoNotOverlapWhenOnlyOneMayRun() throws Exception {
        handler.reset();
        handler.setDefaultDelayMs(120);
        long id = service.create("Concurrency limit", null).id();
        service.start(id);
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(30), "run to finish");

        assertEquals("SUCCEEDED", service.detail(id).status());
        var events = handler.events();
        for (var a : events) {
            for (var b : events) {
                if (a != b) assertFalse(a.overlaps(b), a.nodeId() + " overlapped " + b.nodeId() + " despite max-parallel=1");
            }
        }
    }
}
