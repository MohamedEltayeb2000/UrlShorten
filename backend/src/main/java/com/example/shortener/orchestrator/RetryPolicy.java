package com.example.shortener.orchestrator;

import java.time.Duration;

/** Bounded retries: only transient failures are retried, with capped exponential backoff. */
public final class RetryPolicy {
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final double multiplier;
    private final Duration maxBackoff;

    public RetryPolicy(int maxAttempts, Duration baseBackoff, double multiplier, Duration maxBackoff) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (baseBackoff == null || baseBackoff.isNegative()) {
            throw new IllegalArgumentException("baseBackoff must be >= 0");
        }
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier must be >= 1");
        if (maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= baseBackoff");
        }
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.multiplier = multiplier;
        this.maxBackoff = maxBackoff;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(50), 2.0, Duration.ofMillis(500));
    }

    public int maxAttempts() { return maxAttempts; }

    public boolean shouldRetry(int attemptsSoFar, Throwable failure) {
        return attemptsSoFar < maxAttempts && isTransient(failure);
    }

    public Duration backoff(int attemptNumber) {
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be >= 1");
        double millis = baseBackoff.toMillis() * Math.pow(multiplier, attemptNumber - 1);
        return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
    }

    private static boolean isTransient(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof TransientFailureException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
