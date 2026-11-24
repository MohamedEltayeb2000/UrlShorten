package com.example.shortener.orchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Thread-safe current status plus the ordered history of how a node got there. */
public class NodeHistory {

    private final List<TransitionRecord> records = new ArrayList<>();
    private NodeStatus current;

    public NodeHistory(NodeStatus initial) {
        this.current = initial;
        records.add(new TransitionRecord(null, initial, Instant.now(), "initial"));
    }

    public synchronized NodeStatus current() {
        return current;
    }

    /** Validates the move, records it and returns the new status; state is unchanged if illegal. */
    public synchronized NodeStatus apply(NodeStatus to, String reason) {
        NodeStatus next = NodeStateMachine.transition(current, to);
        records.add(new TransitionRecord(current, next, Instant.now(), reason));
        current = next;
        return next;
    }

    /** Unmodifiable copy, oldest first. */
    public synchronized List<TransitionRecord> records() {
        return List.copyOf(records);
    }
}
