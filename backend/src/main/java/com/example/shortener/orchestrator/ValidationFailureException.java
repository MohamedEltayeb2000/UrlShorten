package com.example.shortener.orchestrator;

/** Invalid output or input; never retried. */
public class ValidationFailureException extends RuntimeException {
    public ValidationFailureException(String message) { super(message); }
    public ValidationFailureException(String message, Throwable cause) { super(message, cause); }
}
