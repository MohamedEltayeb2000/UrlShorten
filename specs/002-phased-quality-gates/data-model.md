# Data Model: Phased Delivery with Quality Gates

Two groups: **delivery-governance records** (this feature; stored as versioned files under
`docs/`, not in the database) and a pointer to the **product/orchestration model** (feature 001;
stored in PostgreSQL, introduced across phases).

## Delivery-governance records (file-based)

### Phase (`docs/phases.json`)
| Field | Notes |
|-------|-------|
| id | `P1`…`P6` |
| name, capabilities[] | Short, simple list |
| requirements[] | IDs like `001-FR-024`, `002-FR-010` owned by this phase |
| exitCriteria[] | Human-readable checks |
| status | `NOT_STARTED` → `IN_PROGRESS` → `GATES_RUNNING` → `COMPLETE` (or `BLOCKED`); after completion, publish state is `PUBLISH_PENDING` (committed/tagged locally, no confirmed remote) or `PUBLISHED` |
| evidence | Paths to gate outputs |

Transitions: `GATES_RUNNING → COMPLETE` only if all gate results are `PASS`; any `FAIL`/`NOT_RUN` → `BLOCKED`.

### Requirement Trace (`docs/traceability.md`, generated)
requirementId, owningPhase, tests[] (class#method or file#name), latestResult (`PASS`/`FAIL`/`MISSING`), lastRun.
Rule: a phase's owned requirement with `MISSING` or `FAIL` blocks the phase.

### Gate Result (`docs/gates/phase-N.json`)
phase, gate (`TESTS|SECURITY|REVIEW|EVAL|PERF`), outcome (`PASS|FAIL|NOT_RUN|SKIPPED`; `NOT_RUN` blocks, `SKIPPED` is non-blocking and means not applicable to the phase), findings[] (severity, summary, location), timestamp, toolVersions.

### Review Record (`docs/reviews/phase-N.md`)
Checklist items with status; findings[] {id, severity `BLOCKER|HIGH|MEDIUM|LOW`, description, resolution or waiver {reason, approver, date}}.

### Evaluation Criteria / Report (`eval/criteria.json`, `docs/evals/phase-N.json`)
criteriaVersion; per scenario: outputs[] each with six criterion results {pass, reason}, overallScore, threshold, passed.

### Performance Budget / Result (`docs/perf/budgets.json`, `docs/perf/phase-N.md`)
metric, budget, measured, passed, environment {os, cpu, jdk, node}.

### Walkthrough Record (`docs/walkthroughs/phase-N.md`, from `docs/walkthrough-template.md`)
phase, steps, stopwatch seconds (budget < 180), completedWithoutInstructions (Y/N), accessibility checks (labels, keyboard path, focus, text status), tester, date.

### Publish Config / Record (`docs/publish-config.json`, `docs/publish-log.md`)
Config: repo URL, branch, confirmedByOwner (bool), confirmedAt. Record: phase, repo, branch, tag, commit, timestamp, gatesPassed[], state (`PUBLISHED|PUBLISH_PENDING`).

## Product & orchestration model (database, per feature 001 / root `plan.md` §11)

| Introduced in | Entities |
|---------------|----------|
| P1 | ShortLink (code unique, originalUrl, status, createdAt, expiresAt) |
| P2 | ClickEvent, IdempotencyRecord |
| P3 | AgentRun, WorkflowNodeDefinition (graph), RunNode, NodeTransition (history) |
| P4 | StageAttempt, Artifact (versioned, parentArtifactIds, checksum, status), AuditEvent (append-only; gains actor fields in P5) |
| P5 | Actor (type `HUMAN|AGENT`, id; resolved from request, default demo user; agents can never approve), Approval (stores actor), PolicyDecision; Artifact status adds `STALE`/`SUPERSEDED`; AgentRun.planVersion |
| P6 | Metrics derived from events (no new core table); final summary stored as an Artifact |

Each phase adds one Flyway migration; earlier migrations are never edited.

### Key validation rules
- ShortLink.code: unique, 4–32 chars, `[A-Za-z0-9_-]`; originalUrl: http/https only, bounded length.
- RunNode state changes only along legal transitions; each change appends a NodeTransition.
- AuditEvent and NodeTransition are insert-only.
- Artifact versions are immutable; rollback changes which version is `CURRENT`.
