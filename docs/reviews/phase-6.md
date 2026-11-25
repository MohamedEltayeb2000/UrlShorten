# Code Review - Phase 6 (Scenarios, metrics, summary, documentation)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: seeded scenario presets and `GET /api/scenarios`, scenario-only run creation, run and overall metrics including mean time
to recovery (MTTR), the markdown engineering summary, the optional env-configured LLM provider, the metrics page and scenario
cards, and the documentation set. The metrics, summary, LLM provider, frontend and documentation slices were built by parallel
agents in isolated worktrees and merged; presets and scenario tests were written on top.

## Checklist
- [x] Correctness: Greenfield reaches release readiness through both gates and is repeatable (three identical runs); Brownfield derives impacted files and risks from the seeded fixture and proposes idempotency; Ambiguous stops with questions and assumptions and proceeds only after a human decision; an unavailable provider is covered by the labeled simulated fallback for every stage; metrics equal the audit trail and attempt rows; the summary has all required sections. Verified by the scenario ITs, `MetricsServiceTest`, `EngineeringSummaryTest` and a live run
- [x] Security: the LLM provider never exposes its key, treats 5xx/timeouts as transient and malformed output as a validation failure, and caps response size; the summary is escaped and policy-redacted; metrics and summary are read-only endpoints
- [x] Simplicity: presets are a static list; metrics are computed on read from the persisted history; no new dependencies
- [x] Test quality: every Phase 6 requirement has a tagged test; scenario outcomes are compared as fingerprints
- [x] Readability: package per concern (`metrics`, `summary`, `agents/llm`)
- [x] Constitution compliance (I-VII): deterministic default provider (III) with an optional validated one; traceability (V); evidence committed (VII)

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | MEDIUM | `MetricsService.overall()` loads all runs and nodes into memory; fine for a prototype, O(rows) at scale. | ACCEPTED - replace with aggregate queries if volume grows |
| R2 | MEDIUM | The metrics JSON omits null fields (`mttrMs`, `successRate`, ...); clients must treat a missing field as null. | ACCEPTED - the frontend already does |
| R3 | LOW | The brownfield analyzer drops its schema-specific compatibility risk because `schema.sql` mentions "idempot" in a comment, so Brownfield lists three risks instead of four. | ACCEPTED - documented in `docs/scenarios.md`; the fixture or the keyword rule can be adjusted |
| R4 | LOW | The mock `TestAgent` always reports 0 failed tests, so the failing-tests release override cannot be shown in the live demo (it is proven by `FailingTestsReleaseIT`). | ACCEPTED - documented |
| R5 | LOW | Several policy rules (command, database, production deploy) exist but no stage proposes those actions yet. | ACCEPTED - documented as a limitation |
| R6 | LOW | The LLM provider is exercised only against a stub server; it has never talked to a real model. | ACCEPTED - optional feature; demo mode needs no key |
| R7 | MEDIUM | The approver identity is a demo header, not authentication (carried from phase 5). | ACCEPTED - documented in `docs/risks-and-tradeoffs.md` |
| R8 | MEDIUM | Spring Framework 6.2.19 CVEs remain suppressed until 2026-12-31 (see `docs/security/residual-risk.md`). | WAIVED (reason: no fixed release; unreachable features; approver: repository owner via chat; date: 2026-11-24) |
