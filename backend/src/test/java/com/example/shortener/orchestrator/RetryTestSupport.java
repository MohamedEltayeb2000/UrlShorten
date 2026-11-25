package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.RunTestSupport.await;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Shared helpers for the retry/fallback integration tests (not itself a test class). */
abstract class RetryTestSupport {
    @Autowired RunService service;
    @Autowired RunNodeRepository nodes;
    @Autowired StageAttemptRepository attempts;
    @Autowired AuditService audit;
    @Autowired ArtifactService artifacts;

    long runUntilWaiting() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(40), "run to reach the approval gate");
        return id;
    }

    List<String> events(long id) {
        return audit.events(id, 0, 100).items().stream().map(AuditEvent::getEventType).toList();
    }
}
