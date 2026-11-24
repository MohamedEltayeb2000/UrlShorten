package com.example.shortener.orchestrator;

/** Thrown when a stage's output failed evaluation or policy: the stage is BLOCKED and its output is not consumed downstream. */
public class HeldForReviewException extends RuntimeException {
    public HeldForReviewException(String message) {
        super(message);
    }
}
