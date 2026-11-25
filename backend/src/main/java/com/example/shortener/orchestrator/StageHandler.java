package com.example.shortener.orchestrator;

/**
 * Executes one stage of a run. Implementations must be side-effect free with respect to the host: they never run shell
 * commands, apply patches or deploy (generated output is only ever a proposal). Throwing marks the stage FAILED.
 */
public interface StageHandler {
    void execute(StageExecution execution) throws Exception;

    /** True when {@link #executeFallback} can complete a stage after the primary path kept failing transiently. */
    default boolean supportsFallback() {
        return false;
    }

    /** Deterministic fallback for an unavailable provider; its output must be labeled as simulated/FALLBACK. */
    default void executeFallback(StageExecution execution) throws Exception {
        throw new UnsupportedOperationException("no fallback available");
    }

    /** Name recorded on each stage attempt (e.g. the agent provider); "none" when no provider is involved. */
    default String providerName() {
        return "none";
    }
}
