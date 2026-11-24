package com.example.shortener.orchestrator;

/** Lifecycle state of a whole workflow run. */
public enum RunStatus {
    CREATED, RUNNING, PAUSED, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, CANCELLED
}
