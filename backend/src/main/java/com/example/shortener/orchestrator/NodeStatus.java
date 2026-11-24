package com.example.shortener.orchestrator;

/** Execution state of one workflow node. Legal transitions are defined by {@code NodeStateMachine}. */
public enum NodeStatus {
    PENDING,
    READY,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    WAITING_FOR_APPROVAL,
    RETRYING,
    CANCELLED,
    SKIPPED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == SKIPPED;
    }
}
