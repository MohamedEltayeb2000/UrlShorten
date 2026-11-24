# Code Review — Phase 1 (Shortener core)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: backend `shortener` / `common` packages, Flyway V1, frontend `ShortenPage`, gate scripts.

## Checklist
- [x] Correctness: create, redirect, 404/410 handling, pagination (size capped at 100), alias conflicts verified by `LinkControllerIT` and a manual end-to-end run against the running app
- [x] Security: http/https allow-list, no credentials in URL, ASCII hosts only, port range, control characters rejected, redirect only to stored validated URLs, standard error body leaks no internals, reserved aliases blocked
- [x] Simplicity: no framework beyond Spring Web/JPA; frontend has 3 runtime dependencies and a single page
- [x] Test quality: tests are tagged with requirement IDs; the hostile-input suite is parameterized over a corpus file; gate self-test proves gates fail on bad input
- [x] Readability: small classes, one responsibility each; comments only where the reason is not obvious
- [x] Constitution compliance (I–VII): no execute/apply/deploy path exists (asserted by `NoAutoApplySecurityTest`); mock-only, no keys; phase published only after gates

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | MEDIUM | URL port was not range-checked (`:99999` accepted). Found while preparing the walkthrough. | FIXED - validator rejects ports outside 1-65535; two new hostile-corpus cases |
| R2 | MEDIUM | `trim()` stripped trailing control characters before validation, hiding a NUL byte case. | FIXED - switched to `strip()`; control characters now rejected |
| R3 | LOW | localhost / private-range hosts are accepted. The server never fetches stored URLs, so there is no SSRF path; browsers handle the redirect. | ACCEPTED - documented, revisit if link preview/fetching is ever added |
| R4 | LOW | Rate limiting is not yet present in P1. | ACCEPTED - owned by P2 (001-FR-028) |
| R5 | LOW | Dependency vulnerability database (NVD) download is slow without an API key, which delays the security gate. | ACCEPTED - documented in README; set `NVD_API_KEY` |
| R6 | LOW | `ShortLink.owner` is fixed to `demo-user` until the actor model arrives in P5. | ACCEPTED |
| R7 | MEDIUM | OWASP Dependency-Check could not finish before publishing (NVD database still downloading; gate = NOT_RUN). Owner instructed to publish Phase 1 now. | WAIVED (reason: NVD download in progress, other security checks passed; approver: repository owner via chat instruction; date: 2026-11-24). Follow-up: re-run `scripts/gate-security.ps1 -Phase 1` once the scan finishes and record the result. |
