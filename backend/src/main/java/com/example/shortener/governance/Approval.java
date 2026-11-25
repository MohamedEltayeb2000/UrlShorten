package com.example.shortener.governance;

import jakarta.persistence.*;
import java.time.Instant;

/** A human decision gate on one stage of a run. Decided exactly once; the decision is never edited afterwards. */
@Entity
@Table(name = "approval")
public class Approval {
    public enum Status { PENDING, APPROVED, REJECTED, SUPERSEDED }

    public enum Kind { CLARIFICATION, HIGH_IMPACT_ACTION, HELD_OUTPUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "node_id", nullable = false, updatable = false, length = 64)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(length = 2000)
    private String comment;

    @Column(name = "policy_reason", length = 500)
    private String policyReason;

    @Column(name = "artifact_version")
    private Integer artifactVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Approval() {}

    public Approval(Long runId, String nodeId, Kind kind, String policyReason, Integer artifactVersion) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.kind = kind;
        this.policyReason = policyReason == null ? null : policyReason.substring(0, Math.min(500, policyReason.length()));
        this.artifactVersion = artifactVersion;
    }

    /** Records the decision. Only a PENDING approval can be decided. */
    public void decide(Status decision, String actorId, String comment) {
        if (status != Status.PENDING) throw new IllegalStateException("approval already " + status);
        if (decision == Status.PENDING) throw new IllegalArgumentException("not a decision");
        this.status = decision;
        this.actorId = actorId;
        this.comment = comment == null ? null : comment.substring(0, Math.min(2000, comment.length()));
        this.decidedAt = Instant.now();
    }

    /** A pending approval that no longer applies (the stage was re-planned). */
    public void supersede(String reason) {
        if (status != Status.PENDING) return;
        this.status = Status.SUPERSEDED;
        this.comment = reason;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public String getActorId() { return actorId; }
    public String getComment() { return comment; }
    public String getPolicyReason() { return policyReason; }
    public Integer getArtifactVersion() { return artifactVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
