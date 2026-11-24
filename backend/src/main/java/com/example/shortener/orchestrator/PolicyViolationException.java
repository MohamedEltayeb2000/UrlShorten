package com.example.shortener.orchestrator;

/** A policy check denied the work; never retried. */
public class PolicyViolationException extends RuntimeException {
    public PolicyViolationException(String message) { super(message); }
    public PolicyViolationException(String message, Throwable cause) { super(message, cause); }
}
