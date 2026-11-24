package com.example.shortener.analytics;

import jakarta.persistence.*;
import java.time.Instant;

/** One redirect. Deliberately stores no IP address and only coarse, sanitized referrer/user-agent data. */
@Entity
@Table(name = "click_event")
public class ClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_link_id", nullable = false)
    private Long shortLinkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 255)
    private String referrer;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected ClickEvent() {}

    public ClickEvent(Long shortLinkId, Instant occurredAt, String referrer, String userAgent, String requestId) {
        this.shortLinkId = shortLinkId;
        this.occurredAt = occurredAt;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getShortLinkId() { return shortLinkId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
    public String getRequestId() { return requestId; }
}
