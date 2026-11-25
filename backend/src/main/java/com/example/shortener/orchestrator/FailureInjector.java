package com.example.shortener.orchestrator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Seeded, config-driven transient failures for demos and tests: {@code shortener.orchestrator.inject-failures=TESTING:2,IMPLEMENTATION:1}
 * makes the first N attempts of those stages (per run) throw a {@link TransientFailureException}. Empty by default.
 */
@Component
public class FailureInjector {
    private final Map<String, Integer> plan = new HashMap<>();
    private final Map<String, AtomicInteger> used = new ConcurrentHashMap<>();

    public FailureInjector(@Value("${shortener.orchestrator.inject-failures:}") String spec) {
        for (String part : spec.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length == 2 && !kv[0].isBlank()) {
                try {
                    plan.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // malformed entries are ignored on purpose: this is a demo/test aid
                }
            }
        }
    }

    /** Throws a transient failure while this stage of this run still has injected failures left. */
    public void maybeFail(long runId, String nodeId) {
        Integer total = plan.get(nodeId);
        if (total == null) return;
        int n = used.computeIfAbsent(runId + ":" + nodeId, k -> new AtomicInteger()).incrementAndGet();
        if (n <= total) throw new TransientFailureException("injected transient failure " + n + "/" + total + " in " + nodeId);
    }
}
