# Code Review - Phase 7 (Brief conformance)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: explicit stage entry/exit gates, cooperative safe-stop, automatic re-plan on upstream change (artifact edit or rollback),
change control on approvals with firing command/migration/deployment policy rules, text-driven requirement analysis, structural
brownfield analysis, and real-browser end-to-end tests. Four slices were built by parallel agents in isolated worktrees and merged.

## Checklist
- [x] Correctness: every stage records entry and exit gate results (audit + run detail); a paused stage stops before storing output and runs once on resume; editing or rolling back an upstream artifact stales downstream stages, re-runs only those, re-applies the approval gates and records `AUTO_REPLAN`; HIGH-risk approvals need a change ticket and justification; proposed commands and migrations are evaluated by the policy engine; the analyst derives ambiguity from the text; the brownfield analyzer works from code structure on any directory. Verified by the new integration tests, `mvn test` (330 tests) and 4 Playwright journeys in a real browser
- [x] Security: edit endpoint refuses agents (403), secrets (400, not echoed) and missing comments; change-control inputs validated; destructive commands and production deployments are denied and the output held; no new execution paths
- [x] Simplicity: gates in one class, auto re-plan reuses the existing re-plan code, change record is five columns on `approval`
- [x] Test quality: tests tagged with requirement IDs 031-037; browser tests tagged and traced through the Playwright JSON report
- [x] Readability: `StageGates`, `ChangeDetectionService`, `RequirementAnalysis` each own one concern
- [x] Constitution compliance (I-VII)

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | HIGH | Merging the parallel slices dropped the run-to-artifacts link from the run page (a later edit replaced the earlier one). It was found by the browser test, not by any unit test. | FIXED - link restored and `RunDetailLinks.test.tsx` added |
| R2 | MEDIUM | Runs now record more than 100 audit events, and several tests read only the first page, so they missed later events. | FIXED - tests read the full trail (`AuditTestSupport`) |
| R3 | MEDIUM | `ChangeDetectionService.autoReplan` waits up to 15 s for running downstream stages after pausing the run; on timeout it leaves the run PAUSED and returns 409. | ACCEPTED - documented behavior |
| R4 | LOW | A failed entry gate skips the stage and fails the run rather than waiting; gate check names are descriptive, the real checks are fixed in `StageGates`. | ACCEPTED |
| R5 | MEDIUM | The simulated agents are heuristic (rule-based ambiguity detection, regex code analysis, one primary type per file). They analyze the text and code structure but are not a language model. | ACCEPTED - the optional LLM provider is the path to real model reasoning; it has only been tested against a stub |
| R6 | MEDIUM | The approver identity is a demo header, not authentication. | ACCEPTED - documented |
| R7 | MEDIUM | Spring Framework 6.2.19 CVEs remain suppressed until 2026-12-31 (see `docs/security/residual-risk.md`). | WAIVED (reason: no fixed release; unreachable features; approver: repository owner via chat; date: 2026-11-24) |
