package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Retry rules with a handler that has NO fallback: transient = bounded retries then FAILED; validation/policy = never retried. */
@SpringBootTest(properties = "shortener.orchestrator.retry.base-ms=10")
@ActiveProfiles("test")
@Import(RetryRulesIT.Cfg.class)
@Requirement("001-FR-015")
class RetryRulesIT {

    static final ConcurrentHashMap<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        StageHandler scriptedHandler() {
            return ex -> {
                int n = CALLS.computeIfAbsent(ex.runId() + ":" + ex.nodeId(), k -> new AtomicInteger()).incrementAndGet();
                switch (ex.nodeId()) {
                    case "REQUIREMENTS_ANALYSIS":
                        if (n <= 2) throw new TransientFailureException("flaky " + n); // succeeds on the 3rd attempt
                        break;
                    case "TASK_DECOMPOSITION":
                        throw new ValidationFailureException("input is invalid");
                    default:
                        break;
                }
            };
        }
    }

    @Autowired RunService service;
    @Autowired RunNodeRepository nodes;

    @Test
    void transientFailureRecoversWithinTheBoundButValidationFailureIsNeverRetried() throws Exception {
        long id = service.create("Retry rules", null).id();
        service.start(id);
        await(() -> terminal(service.detail(id).status()), Duration.ofSeconds(20), "run to finish");

        RunNode ra = nodes.findByRunIdAndNodeId(id, "REQUIREMENTS_ANALYSIS").orElseThrow();
        assertEquals(NodeStatus.SUCCEEDED, ra.getStatus());
        assertEquals(3, ra.getAttemptCount());
        assertEquals(3, CALLS.get(id + ":REQUIREMENTS_ANALYSIS").get());

        RunNode td = nodes.findByRunIdAndNodeId(id, "TASK_DECOMPOSITION").orElseThrow();
        assertEquals(NodeStatus.FAILED, td.getStatus());
        assertEquals(1, td.getAttemptCount(), "validation failures are not retried");
        assertEquals(1, CALLS.get(id + ":TASK_DECOMPOSITION").get());
        assertEquals("FAILED", service.detail(id).status());
    }
}
