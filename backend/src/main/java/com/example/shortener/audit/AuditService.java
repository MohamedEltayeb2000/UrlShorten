package com.example.shortener.audit;

import com.example.shortener.shortener.dto.PageResponse;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    static final int MAX_DETAILS = 4000;
    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    /** Appends an event. Callers must never pass secrets in {@code details}; it is truncated to 4000 chars. */
    @Transactional
    public AuditEvent append(Long runId, String actorType, String actorId, String eventType, String details,
                             String correlationId) {
        String corr = correlationId != null ? correlationId : MDC.get("requestId");
        String d = details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details;
        return repo.save(new AuditEvent(runId, actorType, actorId, eventType, Instant.now(), d, corr));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEvent> events(long runId, int page, int size) {
        int s = Math.max(1, Math.min(size, 100));
        return PageResponse.of(repo.findByRunIdOrderByIdAsc(runId, PageRequest.of(Math.max(0, page), s)), e -> e);
    }
}
