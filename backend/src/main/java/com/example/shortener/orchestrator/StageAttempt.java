package com.example.shortener.orchestrator;

import jakarta.persistence.*;
import java.time.Instant;

/** One execution attempt of a run node (retries and provider fallback are recorded per attempt). */
@Entity
@Table(name = "stage_attempt")
public class StageAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_node_id", nullable = false)
    private Long runNodeId;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "provider_used", length = 32)
    private String providerUsed;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(length = 1000)
    private String error;

    protected StageAttempt() {}

    public StageAttempt(Long runNodeId, int attempt, String status, Instant startedAt) {
        this.runNodeId = runNodeId;
        this.attempt = attempt;
        this.status = status;
        this.startedAt = startedAt;
    }

    public void finish(String status, Instant endedAt, String providerUsed, boolean fallbackUsed, String error) {
        this.status = status;
        this.endedAt = endedAt;
        this.providerUsed = providerUsed;
        this.fallbackUsed = fallbackUsed;
        this.error = error;
    }

    public Long getId() { return id; }
    public Long getRunNodeId() { return runNodeId; }
    public int getAttempt() { return attempt; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getProviderUsed() { return providerUsed; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public String getError() { return error; }
}
