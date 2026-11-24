package com.example.shortener.shortener;

import com.example.shortener.analytics.AnalyticsService;
import com.example.shortener.common.ApiException;
import com.example.shortener.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {
    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final ShortLinkRepository repo;
    private final AnalyticsService analytics;

    public RedirectController(ShortLinkRepository repo, AnalyticsService analytics) {
        this.repo = repo;
        this.analytics = analytics;
    }

    /** Redirects only to URLs that were validated and stored at creation time (never to request input). */
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ShortLink link = repo.findByCode(code).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found"));
        if (link.getStatus() != ShortLink.Status.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found");
        }
        if (link.isExpired(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "LINK_EXPIRED", "Link has expired");
        }
        try {
            Object rid = request.getAttribute(RequestIdFilter.MDC_KEY);
            analytics.recordClick(link, request.getHeader(HttpHeaders.REFERER),
                    request.getHeader(HttpHeaders.USER_AGENT), rid == null ? null : rid.toString());
        } catch (RuntimeException e) {
            log.warn("Could not record click for {}: {}", code, e.toString()); // never break the redirect
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getOriginalUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
