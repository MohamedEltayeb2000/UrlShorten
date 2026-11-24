package com.example.shortener.shortener;

import com.example.shortener.analytics.AnalyticsService;
import com.example.shortener.shortener.dto.CreateLinkRequest;
import com.example.shortener.shortener.dto.LinkResponse;
import com.example.shortener.shortener.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkController {
    private final LinkService service;
    private final AnalyticsService analytics;
    private final String baseUrl;

    public LinkController(LinkService service, AnalyticsService analytics,
                          @Value("${shortener.public-base-url:http://localhost:8080}") String baseUrl) {
        this.service = service;
        this.analytics = analytics;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        LinkService.CreateResult r = service.create(req.url(), req.alias(), req.expiresAt(), key);
        LinkResponse body = LinkResponse.from(r.link(), baseUrl, analytics.clickCounts(java.util.List.of(r.link().getId()))
                .getOrDefault(r.link().getId(), 0L));
        if (r.replayed()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(body);
        return ResponseEntity.created(URI.create("/api/links/" + r.link().getCode())).body(body);
    }

    @GetMapping
    public PageResponse<LinkResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        var p = service.list(page, size);
        Map<Long, Long> counts = analytics.clickCounts(p.getContent().stream().map(ShortLink::getId).toList());
        return PageResponse.of(p, l -> LinkResponse.from(l, baseUrl, counts.getOrDefault(l.getId(), 0L)));
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        ShortLink l = service.get(code);
        return LinkResponse.from(l, baseUrl, analytics.clickCounts(java.util.List.of(l.getId())).getOrDefault(l.getId(), 0L));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String code) {
        service.deactivate(code);
    }
}
