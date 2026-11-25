package com.example.shortener.orchestrator;

/** A failure that may succeed on retry (timeouts, temporary unavailability). */
public class TransientFailureException extends RuntimeException {
    public TransientFailureException(String message) { super(message); }
    public TransientFailureException(String message, Throwable cause) { super(message, cause); }
}
