# Code Review - Phase 3 (Orchestration core)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: graph definition and validation, state machine and history, run/node persistence (V3), dependency scheduler with
bounded parallelism, pause/resume, restart recovery, run API, and the frontend Runs page. Four slices were built by parallel
agents in isolated worktrees and merged; the scheduler, service, API and integration tests were written on top of them.

## Checklist
- [x] Correctness: a node becomes READY only when every prerequisite SUCCEEDED; independent branches overlap in time; the join waits for the slower prerequisite; a failed stage skips its dependents and fails the run; pause stops new scheduling; recovery marks interrupted stages BLOCKED and pauses the run; verified by `RunExecutionIT`, `RunConcurrencyLimitIT`, `RunApiIT` and a manual end-to-end run
- [x] Security: requirement input bounded (4000 chars, control characters rejected), script-like text stored as inert data, a source scan asserts production code never spawns processes or writes files, no apply/execute/deploy endpoints
- [x] Simplicity: one scheduler class, a transition helper as the single place state changes, virtual threads plus a semaphore for the concurrency bound, no queueing framework
- [x] Test quality: tests are tagged with requirement IDs; timing assertions use recorded start/end timestamps from a recording handler instead of sleeps where possible
- [x] Readability: scheduler documented with its invariants; DTOs separated from entities
- [x] Constitution compliance (I-VII): the workflow is a persisted, validated graph with an explicit state machine (I); nothing executes generated output (II)

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | MEDIUM | Scheduling decisions use an in-process per-run lock. With several application instances two schedulers could act on the same run. | ACCEPTED - single-instance prototype; document and use a DB row lock or lease for multi-instance |
| R2 | MEDIUM | Pause is cooperative at stage granularity: a running stage is allowed to finish (the handler receives a `cancelled` signal but the no-op handler ignores it). | ACCEPTED - stages are short here; real agent stages should poll the signal (wired in P4) |
| R3 | LOW | A failed stage is not retried automatically and `requiresApproval` is stored but not enforced yet. | ACCEPTED - owned by P5 (retry policy, approvals) |
| R4 | LOW | `RunTransitions.move` and the run row update are separate transactions, so a crash between them can leave a run RUNNING with all nodes terminal. | ACCEPTED - the next resume or `scheduleLocked` call completes the run; revisit with a reconciliation job |
| R5 | LOW | `AgentRunRepository.findAllByOrderByCreatedAtDescIdDesc` returns a List, so totals come from a separate `count()`. | ACCEPTED |
| R6 | MEDIUM | Spring Framework 6.2.19 CVEs remain suppressed until 2026-12-31 (see `docs/security/residual-risk.md`). | WAIVED (reason: no fixed release; unreachable features; approver: repository owner via chat; date: 2026-11-24) |
