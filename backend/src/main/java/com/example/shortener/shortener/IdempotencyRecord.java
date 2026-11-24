package com.example.shortener.shortener;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, unique = true, length = 128)
    private String key;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "short_link_id", nullable = false)
    private Long shortLinkId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, String fingerprint, Long shortLinkId, Instant expiresAt) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.shortLinkId = shortLinkId;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public String getFingerprint() { return fingerprint; }
    public Long getShortLinkId() { return shortLinkId; }
    public Instant getExpiresAt() { return expiresAt; }
}
