package com.example.shortener.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.shortener.dto.PageResponse;
import com.example.shortener.support.Requirement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-020")
class AuditTrailTest {
    private static final AtomicLong RUNS = new AtomicLong(8_000);
    @Autowired AuditService service;

    @Test
    void repositoryAndEntityAreAppendOnly() {
        assertTrue(Arrays.stream(AuditRepository.class.getMethods()).map(m -> m.getName())
                .noneMatch(n -> n.startsWith("delete") || n.startsWith("remove") || n.startsWith("update")));
        assertFalse(org.springframework.data.jpa.repository.JpaRepository.class.isAssignableFrom(AuditRepository.class));
        assertTrue(Arrays.stream(AuditEvent.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
    }

    @Test
    void eventsReturnInInsertionOrder() {
        long run = RUNS.incrementAndGet();
        for (int i = 0; i < 3; i++) service.append(run, "USER", "alice", "E" + i, "d", "c-1");
        List<AuditEvent> items = service.events(run, 0, 10).items();
        assertEquals(List.of("E0", "E1", "E2"), items.stream().map(AuditEvent::getEventType).toList());
    }

    @Test
    void correlationIdDefaultsToMdcRequestId() {
        long run = RUNS.incrementAndGet();
        MDC.put("requestId", "req-123");
        try {
            service.append(run, "SYSTEM", "orchestrator", "X", null, null);
            service.append(run, "SYSTEM", "orchestrator", "Y", null, "explicit");
        } finally {
            MDC.remove("requestId");
        }
        List<AuditEvent> items = service.events(run, 0, 10).items();
        assertEquals("req-123", items.get(0).getCorrelationId());
        assertEquals("explicit", items.get(1).getCorrelationId());
    }

    @Test
    void pagingAndTruncation() {
        long run = RUNS.incrementAndGet();
        for (int i = 0; i < 5; i++) service.append(run, "USER", "bob", "E" + i, "x".repeat(5000), "c");
        PageResponse<AuditEvent> p = service.events(run, 1, 2);
        assertEquals(2, p.items().size());
        assertEquals(5, p.totalItems());
        assertEquals(3, p.totalPages());
        assertEquals("E2", p.items().get(0).getEventType());
        assertEquals(4000, p.items().get(0).getDetails().length());
    }
}
