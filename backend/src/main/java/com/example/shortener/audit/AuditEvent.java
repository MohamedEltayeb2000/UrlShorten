package com.example.shortener.audit;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit record: set once through the constructor, never modified. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", updatable = false)
    private Long runId;

    @Column(name = "actor_type", nullable = false, length = 16, updatable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false, length = 64, updatable = false)
    private String actorId;

    @Column(name = "event_type", nullable = false, length = 48, updatable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(length = 4000, updatable = false)
    private String details;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    protected AuditEvent() {}

    public AuditEvent(Long runId, String actorType, String actorId, String eventType, Instant occurredAt,
                      String details, String correlationId) {
        this.runId = runId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.details = details;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getDetails() { return details; }
    public String getCorrelationId() { return correlationId; }
}
