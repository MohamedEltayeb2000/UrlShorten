package com.example.shortener.analytics;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public AnalyticsService.Summary summary(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return service.summary(page, size);
    }

    @GetMapping("/links/{code}")
    public AnalyticsService.LinkAnalytics forLink(@PathVariable String code,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return service.forLink(code, page, size);
    }
}
