package com.example.shortener.governance;

import jakarta.persistence.*;
import java.time.Instant;

/** One recorded policy evaluation (append-only). */
@Entity
@Table(name = "policy_decision")
public class PolicyDecisionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "node_id", updatable = false, length = 64)
    private String nodeId;

    @Column(nullable = false, updatable = false, length = 64)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PolicyOutcome outcome;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "policy_version", nullable = false, updatable = false, length = 16)
    private String policyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PolicyDecisionRecord() {}

    public PolicyDecisionRecord(Long runId, String nodeId, PolicyDecision d) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.rule = d.rule();
        this.outcome = d.outcome();
        this.reason = d.reason() == null ? "" : d.reason().substring(0, Math.min(500, d.reason().length()));
        this.policyVersion = d.policyVersion();
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public String getRule() { return rule; }
    public PolicyOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getPolicyVersion() { return policyVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
