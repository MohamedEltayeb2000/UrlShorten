package com.example.shortener.analytics;

import java.time.Instant;

public record ClickView(String code, Instant occurredAt, String referrer, String userAgent) {}
