package com.example.shortener.orchestrator;

/**
 * Thrown by a stage handler that observed {@link StageExecution#cancelled()} (the run was paused) and stopped
 * cooperatively without storing any output. The scheduler returns the stage to READY; it is not a failure and is not retried.
 */
public class StageInterruptedException extends RuntimeException {
    public StageInterruptedException(String message) {
        super(message);
    }
}
