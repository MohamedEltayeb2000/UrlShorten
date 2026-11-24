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
| R6 | HIGH | The completed OWASP Dependency-Check scan found CVSS >= 7 CVEs in Spring Boot 3.5.6, Spring Framework 6.2.11, Tomcat, the PostgreSQL driver, log4j and the Swagger UI bundle. | FIXED - upgraded Spring Boot to 3.5.16, Tomcat 10.1.62, PostgreSQL 42.7.15, log4j 2.25.5, Swagger UI 5.36.0; scan re-run clean except for the item below |
| R7 | HIGH | Spring Framework 6.2.19 (latest 6.2.x) has 12 open CVEs with CVSS >= 7 and no fixed release. All affect features this application does not use (see `docs/security/residual-risk.md`). | WAIVED (reason: no fixed version exists and the affected features are unreachable here; approver: repository owner via chat instruction "waive it and push phase 2"; date: 2026-11-24). Time-boxed suppression expires 2026-12-31; owner decision needed on moving to a fixed Spring release or Spring Boot 4 |
