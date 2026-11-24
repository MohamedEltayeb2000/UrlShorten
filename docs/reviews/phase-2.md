# Code Review - Phase 2 (Hardening and analytics)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: expiry, deactivation, idempotent create, bounded code retries, rate limiting, click events, analytics API,
Flyway V2, and the frontend click counts / expiry field / analytics panel.

## Checklist
- [x] Correctness: expiry (410), deactivation (soft delete, 404 on redirect), idempotent replay (200 + `Idempotent-Replay`), key reuse with a different payload (409), bounded retries (5), 429 with `Retry-After`, analytics totals/top links/paginated events; verified by 31 new backend tests and a manual end-to-end run
- [x] Security: click data stores no IP address, referrer reduced to origin, user agent stripped of control characters and bounded, `X-Forwarded-For` not trusted by the rate limiter, redirect ignores request-supplied targets, error bodies leak nothing (`AbuseSecurityTest`, `RateLimitFilterTest`)
- [x] Simplicity: in-memory token bucket and Java-side day bucketing instead of extra infrastructure; the page stays under the size cap enforced by `simplicity.test.ts`
- [x] Test quality: tests tagged with requirement IDs; mocks only for the pure collision-retry unit test, everything else runs against the real Spring context and H2
- [x] Readability: one responsibility per class (`ClickSanitizer`, `RateLimitFilter`, `AnalyticsService`)
- [x] Constitution compliance (I-VII): nothing executes, applies or deploys; no secrets; phase published only after gates

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | MEDIUM | The rate limiter is in-memory and per instance, and keyed by the TCP peer address. Behind a reverse proxy every client would share one bucket, and multiple instances would not share state. | ACCEPTED - documented trade-off for the single-instance prototype; a proxy-aware or shared limiter is the production follow-up |
| R2 | MEDIUM | `clicksOverTime` loads the last 30 days of click rows and groups them in Java. Fine at prototype volume, O(events) at scale. | ACCEPTED - replace with a SQL group-by if volume grows; perf test covers 300 clicks (summary ~350 ms) |
| R3 | LOW | `analytics` and `shortener` packages depend on each other (`LinkController` shows click counts; `AnalyticsService` reads links). | ACCEPTED - small and one-directional per class; revisit if the modules are split |
| R4 | LOW | Idempotency race handling is compensating, not transactional: a concurrent duplicate is created then deleted. | ACCEPTED - correct outcome, brief extra row |
| R5 | LOW | Idempotency records expire after 24 h but are only removed when the same key is reused. | ACCEPTED - add a scheduled cleanup if table growth matters |
| R6 | MEDIUM | The OWASP Dependency-Check database update is hitting a known "Value too long for column URL" error on some CVE rows (plugin 12.1.6) and the first full download is slow without an NVD API key. The phase 1 follow-up scan is still outstanding. | OPEN - owner action: provide `NVD_API_KEY` or accept a waiver at publish time; see the phase 2 publish decision |
