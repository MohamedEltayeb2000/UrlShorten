package com.example.shortener.support;

import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import java.util.ArrayList;
import java.util.List;

/** Reads a run's complete audit trail (the API caps one page at 100 events and runs now record more than that). */
public final class AuditTestSupport {
    private AuditTestSupport() {}

    public static List<AuditEvent> all(AuditService audit, long runId) {
        List<AuditEvent> out = new ArrayList<>();
        for (int page = 0; page < 100; page++) {
            List<AuditEvent> items = audit.events(runId, page, 100).items();
            out.addAll(items);
            if (items.size() < 100) break;
        }
        return out;
    }

    public static List<String> types(AuditService audit, long runId) {
        return all(audit, runId).stream().map(AuditEvent::getEventType).toList();
    }
}
