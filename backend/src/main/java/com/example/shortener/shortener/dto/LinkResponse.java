package com.example.shortener.shortener.dto;

import com.example.shortener.shortener.ShortLink;
import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String originalUrl, String status,
                           Instant createdAt, Instant expiresAt, long clicks) {
    public static LinkResponse from(ShortLink l, String baseUrl, long clicks) {
        return new LinkResponse(l.getCode(), baseUrl + "/" + l.getCode(), l.getOriginalUrl(),
                l.getStatus().name(), l.getCreatedAt(), l.getExpiresAt(), clicks);
    }
}
