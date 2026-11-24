# Code Review - Phase 4 (Agents, artifacts, audit, AI evaluation)

Reviewer: coding agent (self-review against `docs/review-checklist.md`; the owner approves any waiver).
Scope: agent contract and eight deterministic mock agents, the brownfield fixture and analyzer, the rule-based output
evaluator and evaluation sets, versioned artifacts with lineage, the append-only audit trail, stage attempts, the agent stage
handler wired into the scheduler, and the artifact/audit frontend page. Slices were built by four parallel agents in isolated
worktrees, merged, and integrated here.

## Checklist
- [x] Correctness: each stage runs its agent with only its prerequisites' artifacts, output is evaluated, stored as a new CURRENT artifact with parent lineage and input references, and audited; an ambiguous requirement stops for a human decision; held output is BLOCKED and never stored; verified by `AgentRunIT`, `HeldOutputIT`, `ScenarioPipelineEvalIT` and a live run of all three scenarios
- [x] Security: the evaluator rejects secrets (prose and JSON, a gap found and fixed during this review), unsafe URL schemes, destructive commands and production-deploy actions; the audit table exposes no delete/update API; held output never echoes the secret; generated code is always `applied:false`
- [x] Simplicity: deterministic rule-based evaluation, no LLM dependency; one handler class connects agents to the scheduler
- [x] Test quality: golden and degraded evaluation sets are separate from tests of live agent output; the degraded set must fail on exactly the expected criteria
- [x] Readability: stage-to-artifact-type mapping in one place; exceptions name their meaning (`AwaitingDecisionException`, `HeldForReviewException`)
- [x] Constitution compliance (I-VII): proposals only (II), deterministic default provider (III), traceability and audit (V)

## Findings
| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| R1 | HIGH | The secret pattern in the evaluator only matched `key = value`, so a secret inside JSON structured output (`"password":"..."`) was not detected. Found by a new test while writing this review. | FIXED - pattern now accepts quoted keys and values; `SecretInStructuredOutputTest` covers positive and false-positive cases |
| R2 | MEDIUM | An ambiguous run stops in WAITING_FOR_APPROVAL and a held (BLOCKED) stage waits for an operator, but there is no approval endpoint yet, so neither can be released. | ACCEPTED - owned by P5 (approvals); the states, audit events and artifacts needed are in place |
| R3 | MEDIUM | `AgentStageHandler` runs each agent twice to check determinism. Fine for the mock agents; an optional LLM provider is non-deterministic and costly. | ACCEPTED - P6 makes the determinism check apply to simulated providers only |
| R4 | LOW | Artifact content is a `varchar(100000)`; larger outputs would be rejected. | ACCEPTED |
| R5 | LOW | The hand-authored golden evaluation sets could drift from the live agents. | ACCEPTED - `ScenarioPipelineEvalIT` scores the live agents on all three scenarios every run |
| R6 | LOW | `restore` (rollback) exists in `ArtifactService` but is not exposed or approval-gated yet. | ACCEPTED - P5 |
