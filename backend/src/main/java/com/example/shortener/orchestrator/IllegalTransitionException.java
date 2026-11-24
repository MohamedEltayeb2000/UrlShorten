package com.example.shortener.orchestrator;

/** Thrown when a node is asked to move between two statuses the state machine does not allow. */
public class IllegalTransitionException extends RuntimeException {

    private final NodeStatus from;
    private final NodeStatus to;

    public IllegalTransitionException(NodeStatus from, NodeStatus to) {
        super("Illegal node transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public NodeStatus from() {
        return from;
    }

    public NodeStatus to() {
        return to;
    }
}
