package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.NodeStatus.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Pure, stateless definition of the legal node status transitions. */
public final class NodeStateMachine {

    private static final Map<NodeStatus, Set<NodeStatus>> TABLE = new EnumMap<>(NodeStatus.class);

    static {
        TABLE.put(PENDING, Set.of(READY, BLOCKED, SKIPPED, CANCELLED));
        TABLE.put(READY, Set.of(RUNNING, BLOCKED, SKIPPED, CANCELLED));
        TABLE.put(RUNNING, Set.of(SUCCEEDED, FAILED, RETRYING, WAITING_FOR_APPROVAL, BLOCKED, CANCELLED,
                READY)); // READY = interrupted by safe-stop, re-runs on resume
        TABLE.put(RETRYING, Set.of(RUNNING, FAILED, CANCELLED));
        // BLOCKED here means rejected by a human.
        TABLE.put(WAITING_FOR_APPROVAL, Set.of(SUCCEEDED, BLOCKED, PENDING, CANCELLED)); // PENDING = re-queued by a re-plan
        TABLE.put(BLOCKED, Set.of(READY, SKIPPED, PENDING, CANCELLED)); // PENDING = re-queued by a re-plan
        // RETRYING = manual retry; PENDING = re-queue after re-plan.
        TABLE.put(FAILED, Set.of(RETRYING, PENDING, CANCELLED));
        // Re-queue when an upstream change makes the result stale.
        TABLE.put(SUCCEEDED, Set.of(PENDING));
        TABLE.put(CANCELLED, Set.of());
        // A skipped stage is re-queued when a retry or re-plan makes it runnable again.
        TABLE.put(SKIPPED, Set.of(PENDING));
    }

    private NodeStateMachine() {}

    public static boolean canTransition(NodeStatus from, NodeStatus to) {
        return TABLE.get(from).contains(to);
    }

    /** Returns {@code to} if the move is legal, otherwise throws {@link IllegalTransitionException}. */
    public static NodeStatus transition(NodeStatus from, NodeStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
        return to;
    }

    /** Statuses reachable from {@code from} in one step (unmodifiable). */
    public static Set<NodeStatus> allowedFrom(NodeStatus from) {
        return TABLE.get(from); // Set.of is already unmodifiable
    }
}
