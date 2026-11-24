package com.example.shortener.audit;

import java.time.Instant;

public record AuditEventView(Long id, String actorType, String actorId, String eventType, Instant occurredAt,
                             String details, String correlationId) {
    static AuditEventView of(AuditEvent e) {
        return new AuditEventView(e.getId(), e.getActorType(), e.getActorId(), e.getEventType(), e.getOccurredAt(),
                e.getDetails(), e.getCorrelationId());
    }
}
