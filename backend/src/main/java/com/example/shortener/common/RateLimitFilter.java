package com.example.shortener.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple in-memory token bucket per client address for link creation and analytics.
 * The client address is the TCP peer address; X-Forwarded-For is intentionally NOT trusted (it is spoofable).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private static final class Bucket {
        double tokens;
        long lastNanos;
        Bucket(double tokens, long now) { this.tokens = tokens; this.lastNanos = now; }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int createPerMinute;
    private final int analyticsPerMinute;
    private final ObjectMapper mapper;

    public RateLimitFilter(@Value("${shortener.rate-limit.create-per-minute:30}") int createPerMinute,
                           @Value("${shortener.rate-limit.analytics-per-minute:120}") int analyticsPerMinute,
                           ObjectMapper mapper) {
        this.createPerMinute = createPerMinute;
        this.analyticsPerMinute = analyticsPerMinute;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String group = null;
        int perMinute = 0;
        if ("POST".equals(req.getMethod()) && "/api/links".equals(req.getRequestURI())) {
            group = "create";
            perMinute = createPerMinute;
        } else if ("GET".equals(req.getMethod()) && req.getRequestURI().startsWith("/api/analytics")) {
            group = "analytics";
            perMinute = analyticsPerMinute;
        }
        if (group == null || tryAcquire(group + "|" + req.getRemoteAddr(), perMinute)) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(Math.max(1, 60 / Math.max(1, perMinute))));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object rid = req.getAttribute(RequestIdFilter.MDC_KEY);
        mapper.writeValue(res.getOutputStream(), ApiError.of("RATE_LIMITED",
                "Too many requests, please slow down", null, rid == null ? null : rid.toString()));
    }

    private boolean tryAcquire(String key, int perMinute) {
        if (perMinute <= 0) return false;
        if (buckets.size() > MAX_TRACKED_CLIENTS) buckets.clear(); // bound memory; at worst clients get a fresh bucket
        long now = System.nanoTime();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(perMinute, now));
        synchronized (b) {
            double refill = (now - b.lastNanos) / 60_000_000_000.0 * perMinute;
            b.tokens = Math.min(perMinute, b.tokens + refill);
            b.lastNanos = now;
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
