package com.example.shortener.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class RunTestSupport {
    private RunTestSupport() {}

    public static void await(Supplier<Boolean> condition, Duration timeout, String what) throws InterruptedException {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (condition.get()) return;
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    public static boolean terminal(String runStatus) {
        return runStatus.equals("SUCCEEDED") || runStatus.equals("FAILED") || runStatus.equals("CANCELLED");
    }
}
