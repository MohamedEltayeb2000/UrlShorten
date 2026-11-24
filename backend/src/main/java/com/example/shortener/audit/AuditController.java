package com.example.shortener.audit;

import com.example.shortener.shortener.dto.PageResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs/{runId}/events")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditEventView> events(@PathVariable long runId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        PageResponse<AuditEvent> p = service.events(runId, page, size);
        return new PageResponse<>(p.items().stream().map(AuditEventView::of).toList(), p.page(), p.size(),
                p.totalItems(), p.totalPages());
    }
}
