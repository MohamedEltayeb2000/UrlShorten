package com.example.shortener.orchestrator;

import java.util.List;

/** Thrown when a workflow definition fails validation; carries every problem found. */
public class InvalidWorkflowException extends RuntimeException {
    private final List<String> problems;

    public InvalidWorkflowException(List<String> problems) {
        super("Invalid workflow: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
