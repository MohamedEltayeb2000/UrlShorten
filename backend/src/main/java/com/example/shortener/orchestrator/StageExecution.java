package com.example.shortener.orchestrator;

import java.util.function.BooleanSupplier;

/** What a stage handler is asked to run. {@code cancelled} turns true when the run is no longer RUNNING. */
public record StageExecution(long runId, String nodeId, int attempt, BooleanSupplier cancelled) {}
