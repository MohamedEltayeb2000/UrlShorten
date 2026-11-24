package com.example.shortener.orchestrator;

/** Thrown by a stage handler when a human decision is needed before the stage may complete (clarification or approval). */
public class AwaitingDecisionException extends RuntimeException {
    public AwaitingDecisionException(String message) {
        super(message);
    }
}
