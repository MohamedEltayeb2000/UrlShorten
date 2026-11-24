package com.example.shortener.shortener;

import com.example.shortener.common.ApiException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {
    private final ShortLinkRepository repo;

    public RedirectController(ShortLinkRepository repo) {
        this.repo = repo;
    }

    /** Redirects only to URLs that were validated and stored at creation time (never to request input). */
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = repo.findByCode(code).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found"));
        if (link.getStatus() != ShortLink.Status.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found");
        }
        if (link.isExpired(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "LINK_EXPIRED", "Link has expired");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getOriginalUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
