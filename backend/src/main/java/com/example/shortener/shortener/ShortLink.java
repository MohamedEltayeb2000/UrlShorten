package com.example.shortener.shortener;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "short_link")
public class ShortLink {
    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "owner", length = 64)
    private String owner;

    protected ShortLink() {}

    public ShortLink(String code, String originalUrl, Instant expiresAt, String owner) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.owner = owner;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getOriginalUrl() { return originalUrl; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getOwner() { return owner; }

    public boolean isExpired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
}
