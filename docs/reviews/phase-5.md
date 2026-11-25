# Code Review - Phase 5 (Governance and resilience)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: actor identity and approval rules, policy engine and recorded policy decisions, approvals (V5), human gates for the patch
proposal / release readiness / clarifications, bounded retry with simulated fallback, seeded failure injection, manual retry,
re-plan with stale propagation, artifact rollback, run-control API, and the approvals / controls frontend. The policy and
frontend slices were built by parallel agents in isolated worktrees; scheduler, services, API and integration tests were written
on top of them.

## Checklist
- [x] Correctness: patch proposal and release readiness wait for a recorded human decision; rejection blocks with feedback and downstream never runs; an agent identity or the artifact's author cannot approve; transient failures are retried within the bound then fall back to a labeled simulated output; validation failures are never retried; failing tests block release unless an audited override is approved; re-plan stales exactly the downstream set, keeps old versions, re-runs only affected stages; rollback restores without deleting history. Verified by `GovernanceFlowIT`, `RetryFallbackIT`, `RetryRulesIT`, `FailingTestsReleaseIT`, `DownstreamClosureTest`, `GovernanceSecurityTest` and a live run
- [x] Security: no bypass by ordering, replay, forged identity or wrong stage (`GovernanceSecurityTest`); policy checks run before an artifact is stored; a secret pattern gap for JSON output was found and fixed in both the evaluator and the policy engine
- [x] Simplicity: one scheduler, one `RunTransitions` choke point for state changes, a pure `DownstreamClosure`, approvals as one small table
- [x] Test quality: scenario tests use the real agents; failure paths use scripted handlers; every owned requirement has a tagged test
- [x] Readability: control operations isolated in `RunControlService`; exceptions name their meaning
- [x] Constitution compliance (I-VII): governed autonomy (II) and resilience (IV) are enforced and tested; nothing executes or applies generated output

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | MEDIUM | The approver identity is the `X-Demo-User` header, not authentication. Anyone who can call the API can claim to be any human, so the "agents cannot approve" rule only holds for honest callers. | ACCEPTED - demo-only identity by design (documented in the constitution scope and the README); production needs real authentication and authorization |
| R2 | MEDIUM | Re-plan is refused (409) while an affected stage is running; it does not stop running stages cooperatively. | ACCEPTED - pause, let stages finish, then re-plan |
| R3 | MEDIUM | `HELD_OUTPUT` approvals are declared in the API but never generated: a held (flagged) output is BLOCKED and recovered with manual retry. | ACCEPTED - holding an output that failed evaluation should be fixed and re-run, not approved as-is |
| R4 | LOW | Rollback restores an artifact version but does not stale or re-run downstream stages. | ACCEPTED - documented in the API; use re-plan to refresh downstream |
| R5 | LOW | The state machine gained re-queue transitions (SKIPPED, BLOCKED and WAITING_FOR_APPROVAL to PENDING) for retry and re-plan; the exhaustive transition test was updated with them. | ACCEPTED |
| R6 | HIGH | The policy engine's secret pattern missed secrets in JSON output (`"password":"..."`), the same gap as in the evaluator. | FIXED - pattern accepts quoted keys and values; `PolicyEngineJsonSecretTest` added |
| R7 | LOW | The in-process per-run lock and in-memory failure injector assume a single application instance. | ACCEPTED - prototype scope |
| R8 | MEDIUM | Spring Framework 6.2.19 CVEs remain suppressed until 2026-12-31 (see `docs/security/residual-risk.md`). | WAIVED (reason: no fixed release; unreachable features; approver: repository owner via chat; date: 2026-11-24) |
