package com.example.shortener.orchestrator;

/** A stage's exit gate failed after the handler returned: the stage is FAILED and the message names the failing check. */
public class GateFailedException extends RuntimeException {
    public GateFailedException(String message) {
        super(message);
    }
}
