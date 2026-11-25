package com.example.shortener.summary;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-runs")
public class SummaryController {
    private final EngineeringSummaryService service;

    public SummaryController(EngineeringSummaryService service) {
        this.service = service;
    }

    @GetMapping("/{runId}/summary")
    public ResponseEntity<String> summary(@PathVariable long runId) {
        String body = service.generate(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/markdown;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"engineering-summary-run-" + runId + ".md\"")
                .body(body);
    }
}
