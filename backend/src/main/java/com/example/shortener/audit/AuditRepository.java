package com.example.shortener.audit;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately not a JpaRepository: exposes only append and read operations. */
public interface AuditRepository extends Repository<AuditEvent, Long> {
    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findByRunIdOrderByIdAsc(Long runId, Pageable pageable);

    List<AuditEvent> findByRunIdAndEventTypeInOrderByIdAsc(Long runId, Collection<String> eventTypes);

    long count();

    long countByRunIdAndEventType(Long runId, String eventType);

    long countByEventType(String eventType);
}
