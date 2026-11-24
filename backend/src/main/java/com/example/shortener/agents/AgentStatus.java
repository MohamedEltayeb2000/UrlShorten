package com.example.shortener.agents;

/** Outcome of one agent invocation. FALLBACK means a deterministic mock replaced an unavailable/invalid provider. */
public enum AgentStatus {
    SUCCESS,
    NEEDS_CLARIFICATION,
    FAILED,
    FALLBACK
}
