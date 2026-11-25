package com.example.shortener.metrics;

import com.example.shortener.metrics.MetricsDtos.OverallMetrics;
import com.example.shortener.metrics.MetricsDtos.RunMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping("/api/agent-runs/{runId}/metrics")
    public RunMetrics run(@PathVariable long runId) {
        return service.forRun(runId);
    }

    @GetMapping("/api/metrics")
    public OverallMetrics overall() {
        return service.overall();
    }
}
