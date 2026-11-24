package com.example.shortener.orchestrator;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only record of one node status change. */
@Entity
@Table(name = "node_transition")
public class NodeTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_node_id", nullable = false, updatable = false)
    private Long runNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32, updatable = false)
    private NodeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32, updatable = false)
    private NodeStatus toStatus;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(length = 500, updatable = false)
    private String reason;

    protected NodeTransition() {}

    public NodeTransition(Long runNodeId, NodeStatus fromStatus, NodeStatus toStatus, Instant occurredAt, String reason) {
        this.runNodeId = runNodeId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getRunNodeId() { return runNodeId; }
    public NodeStatus getFromStatus() { return fromStatus; }
    public NodeStatus getToStatus() { return toStatus; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getReason() { return reason; }
}
