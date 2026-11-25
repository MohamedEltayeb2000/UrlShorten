# Feature Specification: Agentic Software Engineering System — URL Shortener

**Feature Branch**: `001-agentic-url-shortener`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "(none typed) — derived from the project brief in `plan.md`: build a governed, agent-driven SDLC orchestration system that takes a software requirement through analysis, planning, design, implementation proposal, testing, documentation and release readiness, using a URL shortener as the sample product."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a requirement through a governed workflow (Priority: P1)

An engineering lead enters a requirement (or picks the Greenfield preset) and starts a run. The system turns it into a dependency graph of stages (requirements analysis, task decomposition, architecture design, implementation plan, implementation proposal, testing, documentation, release readiness), executes independent stages at the same time, waits at join points until all prerequisites finish, and records inputs, outputs, status, timing and decisions for every stage. All of this works without any external AI service.

**Why this priority**: This is the core showcase; without it nothing else has meaning.

**Independent Test**: Launch the Greenfield preset with no AI credentials; observe the run reach release readiness, with architecture design and implementation planning running concurrently and implementation starting only after both finish.

**Acceptance Scenarios**:

1. **Given** a new run from the Greenfield preset, **When** it is started, **Then** every stage executes in dependency order and the run completes with all stage outputs recorded.
2. **Given** two stages with no dependency between them, **When** both are ready, **Then** they run concurrently.
3. **Given** a stage with two prerequisites, **When** only one has finished, **Then** it stays waiting until both succeed.
4. **Given** the application is restarted mid-run, **When** it comes back, **Then** the run's state is intact and interrupted stages are handled safely (retried within policy or flagged for operator action).

---

### User Story 2 - Approve, reject and be protected by policy (Priority: P1)

A reviewer sees pending approvals for high-impact actions (applying a proposed patch, destructive operations, release readiness). They approve or reject with a comment. Rejection blocks the stage and records feedback; approval lets downstream work continue. Policy checks block unsafe content (secrets, unsafe URL schemes, production deployment, release with failing tests unless an audited override is approved). An agent can never approve its own work.

**Why this priority**: Controlled autonomy is the central assessment theme.

**Independent Test**: Reach an approval gate, reject once and confirm the downstream stage stays blocked with feedback recorded; approve a corrected proposal and confirm progress resumes.

**Acceptance Scenarios**:

1. **Given** a stage awaiting approval, **When** the reviewer rejects it, **Then** the stage is blocked, the comment and decision are recorded, and downstream stages do not run.
2. **Given** a stage awaiting approval, **When** the reviewer approves, **Then** the approval records approver, time, decision, comment, artifact version and policy reason, and downstream stages proceed.
3. **Given** test failures, **When** release readiness is requested without an approved override, **Then** policy denies it and the denial is recorded.
4. **Given** an agent produced an artifact, **When** that same agent attempts to approve it, **Then** the attempt is refused.

---

### User Story 3 - Recover from failure, pause and roll back (Priority: P2)

An operator watches a run that hits a transient failure: the system retries a bounded number of times, then falls back to deterministic simulated output (labeled as simulated) or ends in a clear failed state. The operator can pause the run (safe-stop), resume it, manually retry an eligible stage, and restore a prior approved artifact version without losing history.

**Why this priority**: Demonstrates resilience, but depends on the core run and approvals.

**Independent Test**: Trigger a seeded transient failure and observe bounded retries then fallback; pause mid-run and confirm no new stages start; roll back an artifact and confirm the audit history remains.

**Acceptance Scenarios**:

1. **Given** a stage with a transient failure, **When** it fails, **Then** it is retried up to the configured maximum, each attempt recorded, then falls back or fails terminally.
2. **Given** a validation or policy failure, **When** it occurs, **Then** no automatic retry happens.
3. **Given** a running run, **When** the operator pauses it, **Then** no new stages are scheduled until resumed.
4. **Given** several artifact versions, **When** the operator rolls back to a prior approved version, **Then** that version becomes current and no version or audit record is deleted.

---

### User Story 4 - Re-plan after an upstream change (Priority: P2)

A user edits the requirement or an upstream artifact. The system identifies all downstream stages and artifacts affected, marks them stale so they cannot be consumed, creates a new plan version while preserving the old one, requests approval when the change is high impact, and re-runs only the affected stages.

**Why this priority**: Shows non-linear, stateful execution; builds on the core run.

**Independent Test**: Change the requirement after task decomposition succeeded; confirm downstream stages become stale, unaffected stages are untouched, and only affected stages re-run.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** an upstream artifact changes, **Then** exactly the downstream dependents are marked stale and a new plan version is created with the old one retained.
2. **Given** stale stages, **When** a later stage would consume their output, **Then** it is prevented until they are re-run.

---

### User Story 5 - Three demonstration scenarios (Priority: P2)

A presenter launches Greenfield, Brownfield and Ambiguous scenarios from the dashboard or API, each seeded and repeatable. Brownfield analyses a small seeded fixture and proposes idempotent link creation. Ambiguous ("Make URL links safer and improve analytics.") surfaces open questions and assumptions, enters a clarification-needed state, and proceeds only after a human decision.

**Why this priority**: Required deliverable; each scenario is a repeatable demo.

**Independent Test**: Launch each scenario and verify its expected distinctive outputs (task graph and release checklist; impacted files and compatibility risks; open questions and a clarification pause).

**Acceptance Scenarios**:

1. **Given** the Greenfield preset, **When** run, **Then** it reaches release readiness with approvals and metrics.
2. **Given** the Brownfield preset, **When** run, **Then** impacted fixture files, data flow, proposed changes and compatibility risks are reported.
3. **Given** the Ambiguous preset, **When** run, **Then** it pauses with open questions and listed assumptions and resumes only after a human approves proceeding.
4. **Given** the AI provider is unavailable, **When** any scenario runs, **Then** simulated agents complete it.

---

### User Story 6 - Dashboard visibility, audit and metrics (Priority: P2)

A user opens a dashboard to create runs, see the workflow graph (with stale, blocked, waiting-for-approval and failed states visually distinct), view artifacts and their versions, work an approvals queue, read a chronological audit timeline, and see metrics: success rate, retries, rollbacks, total duration, stage latency, failed and blocked counts. Loading, empty, failure and approval-pending states are handled, and generated code is clearly labeled as a proposal, not applied.

**Why this priority**: Makes everything observable and demonstrable.

**Independent Test**: With seeded runs, open each view and confirm the data and states shown match the run's recorded state.

**Acceptance Scenarios**:

1. **Given** a run in progress, **When** the user views it, **Then** progress updates without manual reload.
2. **Given** a stage with several artifact versions, **When** opened, **Then** version, provenance and input references are shown.
3. **Given** completed runs, **When** metrics are viewed, **Then** all listed metrics are shown and consistent with recorded events.

---

### User Story 7 - Use the URL shortener product (Priority: P3)

An end user creates a short link for a valid URL (optionally with a custom alias and expiration), visits the short link and is redirected, and an admin views click analytics (total clicks, clicks over time, top links, recent events). The product works independently of the agent system.

**Why this priority**: It is the sample product; the orchestration is the main showcase.

**Independent Test**: Create a link, follow it, and see the click reflected in analytics.

**Acceptance Scenarios**:

1. **Given** a valid URL, **When** a link is created, **Then** a unique short code is returned and visiting it redirects to the original URL and records a click.
2. **Given** an invalid URL, unsafe scheme (e.g. `javascript:`), or invalid/taken alias, **When** creation is attempted, **Then** it is rejected with a clear, consistent error.
3. **Given** an expired or deactivated link, **When** visited, **Then** it does not redirect.
4. **Given** a repeated create request with the same idempotency key, **When** retried, **Then** no duplicate link is created and the original result is returned.

---

### Edge Cases

- Dependency graph contains a cycle, missing dependency or duplicate node ID: definition is rejected before a run starts.
- Short-code collision: bounded regeneration, then a clear failure rather than an endless loop.
- Re-plan requested while stages are running: running stages are stopped or completed safely before stale marking.
- Approval submitted twice or for a superseded artifact version: rejected as stale/duplicate.
- Rollback requested for a version that was never approved: refused.
- Rate limit exceeded on link creation or analytics: clear, retry-able error.
- Restart while a stage was mid-execution: no duplicate execution of the same attempt.
- Secrets appearing in generated output or logs: blocked by policy and not persisted.
- Link visited concurrently at expiry boundary: consistent single outcome.

## Requirements *(mandatory)*

### Functional Requirements

**Orchestration**

- **FR-001**: System MUST represent each run as a persisted workflow graph with explicit stage dependencies, configurable without rewriting the scheduler.
- **FR-002**: System MUST validate graphs (no cycles, no missing dependencies, no duplicate stage IDs) before execution.
- **FR-003**: System MUST track each stage through defined states (pending, ready, running, succeeded, failed, blocked, waiting for approval, retrying, cancelled, skipped), reject illegal transitions, and store transition history.
- **FR-004**: System MUST start a stage only when all its prerequisites have succeeded and MUST run independent ready stages concurrently up to a configurable limit.
- **FR-005**: System MUST give each stage only its relevant inputs (normalized requirement, assumptions, relevant upstream artifacts, policy constraints, decision history).
- **FR-006**: System MUST persist run, stage, attempt, artifact, approval, decision and audit data and recover interrupted runs safely after restart without duplicate execution.
- **FR-007**: System MUST support start, pause (safe-stop), resume and manual retry of eligible stages.

**Agents**

- **FR-008**: Every agent MUST return a structured result including agent name, status (success, needs clarification, failed, fallback), summary, stage-specific output, assumptions, risks, open questions, recommended next actions, confidence, and references to input artifact versions.
- **FR-009**: System MUST provide deterministic simulated agents for every stage, usable with no external AI service or credentials, and outputs MUST be labeled as simulated.
- **FR-010**: System MAY use an optional external AI provider configured only through environment settings; its output MUST be validated against the same contract and MUST NOT bypass scheduling or governance; on unavailability the system MUST fall back to simulated agents.

**Governance**

- **FR-011**: System MUST require human approval before applying a proposed patch, running destructive operations against non-test data, approving release readiness, and any policy-defined high-impact change.
- **FR-012**: System MUST record each approval with approver identity, timestamp, decision, comment, artifact version and policy reason; rejection MUST block the stage and record feedback; an agent MUST NOT approve its own work.
- **FR-013**: System MUST evaluate and record policy decisions covering: no secrets in artifacts or logs; no unsafe URL schemes; no destructive commands without approval; no production deployment actions; outputs are proposals until approved; test failures block release readiness unless an approved, audited override exists.
- **FR-014**: System MUST NOT apply code changes to any repository, run generated code, or deploy to production by default.

**Resilience**

- **FR-015**: System MUST retry transient stage failures with configurable bounded attempts and backoff, MUST NOT auto-retry validation or policy failures, and MUST log each retry reason and outcome.
- **FR-016**: System MUST version every artifact with parent lineage and support restoring a prior approved version without deleting history (rollback is artifact-level and simulated for code).
- **FR-017**: When an upstream requirement, assumption or artifact changes, System MUST compute the affected downstream set, mark it stale, block use of stale outputs, create a new plan version retaining the old, request approval for high-impact changes, and re-run only affected stages.

**Scenarios, observability and UI**

- **FR-018**: System MUST provide repeatable seeded Greenfield, Brownfield (grounded in a seeded fixture) and Ambiguous presets launchable from the dashboard and API.
- **FR-019**: For ambiguous requirements, System MUST record open questions and assumptions, enter a clarification-needed state, and proceed only after a human decision.
- **FR-020**: System MUST maintain an append-only audit trail with correlation identifiers and expose run metrics: completion/success rate, retry count, rollback count, end-to-end duration, stage latency, and failed/blocked counts.
- **FR-021**: Dashboard MUST provide: run creation with scenario selection, run detail with graph and controls, artifact viewer with versions, approvals queue, audit timeline, and metrics; it MUST visually distinguish stale, blocked, waiting-for-approval and failed stages and handle loading, empty and error states.
- **FR-022**: System MUST generate a final engineering summary per run (plan, rationale, artifacts, assumptions, risks, validation results, limitations) viewable or exportable.

**URL shortener product**

- **FR-023**: System MUST create a unique short code for a valid URL, with optional custom alias and optional expiration.
- **FR-024**: System MUST validate and normalize URLs, reject unsafe schemes and invalid aliases, and redirect only to stored validated URLs.
- **FR-025**: System MUST redirect a valid, active, unexpired short code to its URL and record a click event with only minimal, sanitized data (timestamp, code, coarse user-agent/referrer).
- **FR-026**: System MUST allow listing links (paginated), viewing link metadata, and deactivating links without hard-deleting them.
- **FR-027**: System MUST provide aggregate and per-link analytics (total clicks, clicks over time, top links, recent events) with pagination.
- **FR-028**: System MUST enforce short-code uniqueness, handle collisions with bounded retries, support idempotent creation via a client-supplied key, and rate-limit link creation and analytics requests.
- **FR-029**: System MUST return consistent, clear error responses and expose health/readiness status, structured logs with request correlation IDs, and API documentation.

**Delivery**

- **FR-030**: System MUST be runnable locally from documented copy-paste steps with a demo mode needing no AI credentials, and MUST include automated tests, architecture documentation, risk/trade-off notes and a demo script.

**Brief conformance (phase 7)**

- **FR-031**: Every stage MUST have an explicit entry gate (all prerequisites succeeded with a current artifact) and exit gate (output stored, evaluated and policy-checked); each gate result MUST be recorded in the audit trail and visible in the run detail.
- **FR-032**: When an upstream artifact is changed (a human edit or a rollback), System MUST detect it and re-plan automatically: mark downstream artifacts stale, re-run only the affected stages, keep every approval gate, and record the decision lineage.
- **FR-033**: Approvals for high-impact actions MUST carry a change record (risk class, change ticket and risk justification for high risk), and the command, database-change and production-deployment policy rules MUST evaluate the actions a stage actually proposes.
- **FR-034**: Safe-stop MUST be cooperative: a running stage that observes a pause MUST stop before storing output and return to ready, and run exactly once more on resume.
- **FR-035**: The requirements analyst MUST derive goals, constraints and ambiguity from the requirement text (vague terms, missing acceptance criteria, undefined references) and ask questions that name the actual triggers, independent of any scenario label.
- **FR-036**: Brownfield analysis MUST derive impacted files, data flow and compatibility risks from the code structure of a configured source directory (classes, dependencies, endpoints, tables), not from keywords or comments.
- **FR-037**: The system MUST be verified end to end in a real browser against the running backend and frontend for the main user journeys.

### Key Entities

- **Agent Run**: One execution of a requirement; scenario type, raw and normalized requirement, status, plan version, timings.
- **Workflow Graph / Stage Definition**: Stages and their dependencies for a plan version.
- **Run Stage**: A stage instance in a run; state, attempt count, input/output artifact references, error summary, transition history.
- **Stage Attempt**: One execution try; status, timings, provider used, whether fallback was used.
- **Artifact**: Versioned stage output (requirement, assumptions, task plan, design, change proposal, test report, docs, release checklist) with parent lineage, checksum and status (current, stale, superseded).
- **Approval**: Decision on a gated action/artifact version with approver, comment and policy reason.
- **Policy Decision**: Recorded outcome of a policy check with reason and policy version.
- **Audit Event**: Append-only record of actor, event type, details and correlation ID.
- **Short Link**: Short code, original URL, status, creation and expiration times, owner.
- **Click Event**: Sanitized redirect record tied to a short link.
- **Idempotency Record**: Client key, request fingerprint and stored result for create-link requests.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a clean setup following the documented steps, a new user can launch the Greenfield scenario and see it reach release readiness in under 5 minutes without any AI credentials.
- **SC-002**: In 100% of repeated runs of each of the three scenarios, the same stages, outcomes and approval prompts occur.
- **SC-003**: Independent stages are observed running concurrently, and a join stage never starts before all its prerequisites succeed, in 100% of tested runs.
- **SC-004**: No code change, generated-code execution or production deployment can occur without an explicit recorded human approval (0 bypass paths found in testing).
- **SC-005**: A seeded transient failure is retried no more than the configured maximum, then completes via fallback or fails clearly, with every attempt recorded.
- **SC-006**: After an upstream change, 100% of downstream dependents are marked stale and 0 unaffected stages are re-run.
- **SC-007**: After a rollback, all prior artifact versions and audit events remain retrievable (0 records lost).
- **SC-008**: Every stage and approval has a complete audit record, and the displayed metrics match recorded events exactly.
- **SC-009**: A link created by a user redirects correctly and its click appears in analytics within 5 seconds; invalid or unsafe URLs are rejected in 100% of tested cases.
- **SC-010**: Retrying a create-link request with the same idempotency key produces exactly one link in 100% of tested cases.
- **SC-011**: The full automated test suite and build pass before the work is declared complete.
- **SC-012**: A presenter can walk through the full demo script (all three scenarios, approval, retry, safe-stop, re-plan, rollback, metrics, link creation) in under 20 minutes.

## Assumptions

- Primary users are an interviewer/evaluator, engineering leads and reviewers using a single-tenant local deployment; identity is a demo user, not enterprise sign-in.
- The mandated technology stack and architecture live in `plan.md` and the constitution and are decisions for the planning phase, not this specification.
- Demo timing for retries and backoff is kept short; deterministic simulated agents are the default and the reference behavior.
- Brownfield analysis is limited to a small seeded fixture, not arbitrary repositories.
- Real production deployment, cloud provisioning, custom domains, billing and large-scale abuse detection are out of scope.
- Click analytics retention follows a minimal-data default and will be documented as an assumption.
- Rate limits use simple local thresholds suitable for a demo.
- Rollback of code changes is simulated; only artifact-level restore is real.
