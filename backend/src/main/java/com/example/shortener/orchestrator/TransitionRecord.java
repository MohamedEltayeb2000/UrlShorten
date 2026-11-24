package com.example.shortener.orchestrator;

import java.time.Instant;

/** One entry of a node's transition history. {@code from} is null for the initial state. */
public record TransitionRecord(NodeStatus from, NodeStatus to, Instant at, String reason) {}
