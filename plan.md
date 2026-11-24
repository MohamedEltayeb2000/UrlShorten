# Plan: Agentic Software Engineering System — URL Shortener

## 1. Project Goal

Build a complete, runnable prototype TODAY that demonstrates how an agentic engineering system can take a software requirement and drive it through a governed SDLC workflow: requirements analysis, planning, design, implementation, testing, documentation, and release readiness. The project is a one-day build: prioritize a working end-to-end product and demo over multi-day polish.

The URL Shortener is the sample product the system will build and evolve. The primary showcase is the orchestration engine—not simply AI-generated code. Backend must be Java with Spring Boot; frontend must be React with TypeScript.

**Assessment focus:** controlled autonomy, explicit workflow dependency graph, stateful/non-linear execution, parallel work, synchronization, approval gates, dynamic re-planning, bounded retries, fallback, rollback/safe-stop, policy guardrails, traceability, observability, and validation.

## 2. Success Criteria

- The project runs locally with clear setup instructions and a demo mode that does not require an AI API key.
- An orchestration run can be created from a requirement and represented as a persisted workflow graph.
- The workflow executes stages with explicit dependencies and can run independent stages in parallel.
- Each stage records inputs, outputs, status, timestamps, attempts, decisions, errors, and links to artifacts.
- Human approval is required before high-impact actions (e.g., applying code changes, executing destructive operations, or marking release-ready).
- An upstream artifact change invalidates/re-queues affected downstream work and triggers controlled re-planning.
- Retry, fallback, safe-stop, and rollback behavior are demonstrable.
- The system reports metrics: completion/success rate, retry count, rollback count, end-to-end duration, stage latency, and failure/blocked counts.
- Three required demos are available: greenfield, brownfield, and ambiguous requirements.
- Tests, architecture documentation, risk/trade-off notes, and final engineering summaries are included.

## 3. Scope

### In scope
- URL shortener REST API and minimal user-facing dashboard.
- Agent/workflow orchestration with graph-based execution and persisted run state.
- Requirement normalization and ambiguity/assumption tracking.
- Task decomposition with dependencies.
- Design, implementation proposal, test, documentation, and release-readiness artifacts.
- Simulated agent execution by default; optional LLM provider behind an interface and environment configuration.
- Human approval checkpoints, audit events, policy checks, retries, fallback, safe-stop, and rollback simulation.
- Greenfield, brownfield, and ambiguous scenario templates.

### Out of scope for the prototype
- Real production deployment, cloud provisioning, or Kubernetes.
- Autonomous deployment to production.
- A fully general autonomous coding agent that can safely modify arbitrary repositories.
- Multiple real LLM vendors and complex agent-to-agent communication protocols.
- Enterprise identity integration; use local development authentication or a clearly documented demo-only mode.
- Advanced billing, custom domains, and large-scale abuse detection.

## 4. Product: URL Shortener

### Functional requirements
- Create a short code for a valid long URL.
- Redirect short code to its original URL.
- Allow optional custom alias if available and valid.
- Reject invalid URLs and aliases; prevent unsafe schemes such as `javascript:`.
- Record redirect analytics (timestamp, short code, coarse user-agent/referrer fields where safe; do not store sensitive data unnecessarily).
- Provide aggregate analytics: total clicks, clicks over time, top links, and recent events.
- Support link expiration as an optional field.
- Return clear API errors and use consistent response schemas.

### Reliability and security
- Unique short-code constraint enforced by database.
- Collision handling with bounded generation retries.
- Idempotency support for create-link requests when an idempotency key is provided.
- Rate limiting for link creation and analytics endpoints (simple local implementation is acceptable).
- Validate and normalize URLs; protect against open-redirect abuse by only redirecting to stored, validated URLs.
- Pagination for list and analytics endpoints.
- Health/readiness endpoints.
- Structured logging and correlation/request IDs.

## 5. Proposed Stack and Architecture

Use this required stack. Do not substitute Python, Node-only backend, or another framework:

- **Backend:** Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Validation, Spring Data JPA, Spring Security only if lightweight demo auth is feasible.
- **Database:** PostgreSQL with Docker Compose; H2 only for isolated unit tests if needed.
- **Migrations:** Flyway.
- **Concurrency:** Spring-managed executor/thread pool for independent workflow nodes; bounded concurrency and persisted node state.
- **Frontend:** React + TypeScript + Vite. Use React Flow (or a lightweight SVG graph if React Flow setup threatens delivery) for workflow visualization. Keep UI responsive and polished but do not spend excessive time on styling.
- **Frontend data:** typed API client layer, React hooks, polling for run progress; avoid overengineering state management.
- **Testing:** JUnit 5, Mockito, Spring Boot Test, Testcontainers only if it can be configured quickly; frontend unit/component tests for critical UI behavior.
- **AI integration:** `AgentProvider` abstraction; deterministic mock provider is the default and must be fully usable. Optional LLM provider configured by environment variables. Never hardcode API keys.
- **API docs:** OpenAPI/Swagger UI.
- **Observability:** structured logs, run/stage event records, and metrics endpoint or a metrics dashboard.
- **Developer experience:** root README with copy-paste commands, `.env.example`, Docker Compose for PostgreSQL, and one command/script to start the backend and frontend where practical.

### High-level modules
1. `shortener`: URL creation, lookup, redirect, expiration, validation.
2. `analytics`: click events and aggregated metrics.
3. `orchestrator`: run lifecycle, graph model, scheduler, dependency handling, parallel tasks, re-planning.
4. `agents`: requirement analyst, planner, architect, implementer/proposal agent, test agent, documentation agent, release-readiness agent. Keep agent responsibilities and contracts explicit.
5. `governance`: policy evaluation, approval gates, safe-stop, rollback actions, retry policy.
6. `audit`: immutable-style append-only events and decision lineage.
7. `artifacts`: versioned stage artifacts (requirement, assumptions, task plan, design, code/change proposal, test report, docs, release checklist).
8. `web`: React dashboard for creating runs, viewing graph/status/artifacts, approvals, logs, metrics, and scenario demos.

## 6. Orchestration Model (Critical)

Implement a persisted graph-based workflow, not a hard-coded sequence of function calls.


### Workflow stages / nodes
- `REQUIREMENTS_ANALYSIS`
- `TASK_DECOMPOSITION`
- `ARCHITECTURE_DESIGN`
- `IMPLEMENTATION_PLAN`
- `IMPLEMENTATION` (generate a proposal/patch artifact; actual repository mutation is disabled by default)
- `TESTING`
- `DOCUMENTATION`
- `RELEASE_READINESS`

### Example dependency graph
- Requirements Analysis → Task Decomposition
- Task Decomposition → Architecture Design
- Task Decomposition → Implementation Plan
- Architecture Design + Implementation Plan → Implementation (synchronization gate; both must finish)
- Implementation → Testing
- Architecture Design → Documentation (can run in parallel with implementation/testing where inputs are sufficient)
- Testing + Documentation → Release Readiness

The graph must be configurable in code or stored as a workflow definition. Include at least one example where independent nodes run concurrently and a join/synchronization node waits for both prerequisites.

### Node state machine
Use explicit states such as:
`PENDING → READY → RUNNING → SUCCEEDED`
Additional terminal or intermediate states: `FAILED`, `BLOCKED`, `WAITING_FOR_APPROVAL`, `RETRYING`, `CANCELLED`, `SKIPPED`.
Validate legal transitions. Store transition history.

### Run state and context
- Persist the run, graph, nodes, stage attempts, artifacts, decisions, approvals, and audit events.
- Each agent receives a scoped context: normalized requirement, assumptions, relevant upstream artifacts, policy constraints, and prior decision history.
- Store artifact versions and their parent/version lineage.
- Avoid passing all context indiscriminately; provide only stage-relevant inputs.

### Scheduler and parallelism
- Identify all `READY` nodes whose dependencies have succeeded and run them concurrently up to a configurable limit.
- A node with multiple dependencies only becomes ready when every required dependency has succeeded.
- Persist state before/after execution to make run progress observable and recoverable.
- On restart, recover interrupted runs safely; mark abandoned executions and either retry within policy or require operator action.

### Re-planning
- When an upstream requirement, assumption, or artifact is changed, calculate the downstream dependency closure.
- Mark affected downstream artifacts/nodes stale and prevent stale outputs from being used in later stages.
- Create a new plan version, preserve the old plan and decision history, and request approval when the change is high impact.
- Re-run only affected stages, not the entire workflow, when safe.

### Retry, fallback, rollback, and safe-stop
- Bounded retries with configurable max attempts and backoff (keep demo timing short).
- Retry only transient failures; validation or policy failures should not retry automatically.
- If the AI provider is unavailable, use deterministic fallback templates/mock agents and clearly label outputs as simulated.
- Provide a safe-stop control to halt scheduling; running stages should stop cooperatively where possible.
- Rollback should be demonstrable using versioned artifacts and an explicit restore-to-prior-approved-version operation. For the demo, rollback of code changes is simulated because direct repository writes are disabled.
- Log retry/rollback reasons and outcomes.

### Human approvals and policy guardrails
Require approval before:
- Applying a proposed patch to the working repository (actual apply operation is disabled by default or behind an explicit demo-only safety switch).
- Running destructive commands or migrations against non-test data.
- Approving release readiness.
- Any policy-defined high-impact change.

Approval records include approver identity (demo user), timestamp, decision, comment, artifact version, and policy reason. Rejection blocks the node and records feedback. Do not allow an agent to approve its own work.

Policy checks should cover at minimum:
- No secrets in generated artifacts or logs.
- No unsafe URL schemes.
- No destructive shell commands without explicit approval.
- No production deployment actions.
- Generated outputs are proposals until human approval.
- Test failures block release readiness unless an explicit override is approved and audited.

## 7. Three Demonstration Scenarios

Implement scenario presets that can be launched from the dashboard and API.

### Scenario A — Greenfield
Requirement: "Build a URL shortener API with link creation, redirects, expiration, and click analytics."
Show: requirement normalization, assumptions, task graph, parallel architecture/implementation planning, implementation proposal artifact, tests, documentation, release checklist, approvals, metrics.

### Scenario B — Brownfield
Seed a small existing URL-shortener codebase/module or fixture with an intentionally introduced defect or missing feature. Requirement: "Add idempotent link creation and prevent duplicate links when clients retry requests."
Show: codebase/module analysis, impacted files and data flow, proposed migration/schema/API changes, tests, compatibility risks, review approval, validation. Keep brownfield analysis grounded in the fixture rather than pretending to inspect arbitrary external repositories.

### Scenario C — Ambiguous Requirement
Requirement: "Make URL links safer and improve analytics."
Show: the analyst identifies ambiguity, asks/records unresolved questions, proposes explicit assumptions, classifies risks, and proceeds only within safe assumptions. Demonstrate a clarification-needed state and a human decision/approval to proceed.

Each scenario must include a readable, seeded, deterministic path so the demo works without external LLM access.

## 8. API Design (Initial)

### URL shortener APIs
- `POST /api/links` — create a link; supports optional alias, expiration, and idempotency key.
- `GET /api/links` — paginated list of links.
- `GET /api/links/{code}` — link metadata.
- `GET /{code}` — redirect and record click event.
- `DELETE /api/links/{code}` — deactivate link (not hard-delete by default).
- `GET /api/analytics/summary` — aggregate click metrics.
- `GET /api/analytics/links/{code}` — per-link metrics.
- `GET /actuator/health` — health check.

### Orchestration APIs
- `POST /api/agent-runs` — create a run from a requirement and optional scenario preset.
- `GET /api/agent-runs` — list runs.
- `GET /api/agent-runs/{runId}` — run details, graph/node state, current progress.
- `POST /api/agent-runs/{runId}/start` — start/resume scheduling.
- `POST /api/agent-runs/{runId}/pause` — pause/safe-stop.
- `POST /api/agent-runs/{runId}/replan` — submit changed requirement or artifact and re-plan downstream.
- `POST /api/agent-runs/{runId}/nodes/{nodeId}/retry` — manually retry an eligible node.
- `POST /api/agent-runs/{runId}/approvals` — approve/reject a gated action/artifact.
- `POST /api/agent-runs/{runId}/rollback` — restore prior artifact version for demo.
- `GET /api/agent-runs/{runId}/events` — audit and execution events.
- `GET /api/agent-runs/{runId}/artifacts` — list generated artifacts.
- `GET /api/agent-runs/{runId}/metrics` — run metrics.

Use consistent DTOs, validation, error responses, pagination, and OpenAPI documentation. Adjust endpoint shapes if needed after design, but preserve the capabilities.

## 9. Dashboard UX

Build a simple but polished dashboard with these views:
- **Home:** create run, select greenfield/brownfield/ambiguous scenario, recent runs, overall metrics.
- **Run detail:** requirement, assumptions, current status, dependency graph, stage statuses, and controls (start, pause, retry, re-plan).
- **Graph view:** show dependencies, parallel nodes, and synchronization points; make stale, blocked, waiting-for-approval, and failed states visually distinct.
- **Artifacts:** view stage outputs and versions; compare prior/current versions where simple.
- **Approvals:** pending approval queue with artifact/context, approve/reject and comment.
- **Audit/metrics:** chronological event timeline, attempts, retries, rollbacks, stage durations, total duration, and outcome.

Prioritize clarity and demo usability over complex visual design. Ensure loading, empty, failure, and approval-pending states are handled.

## 10. Agent Contracts

Every agent follows a common contract and returns structured output, not only free-form text:
- `agentName`
- `status` (`SUCCESS`, `NEEDS_CLARIFICATION`, `FAILED`, `FALLBACK`)
- `summary`
- `structuredOutput` (stage-specific JSON/schema)
- `assumptions[]`
- `risks[]`
- `openQuestions[]`
- `recommendedNextActions[]`
- `confidence` (qualitative or 0–1; explain low confidence)
- `referencesToInputs[]` (artifact/version IDs)

Implement mock agents first, with deterministic outputs. Optional LLM provider may improve content but must conform to the same contracts, validate outputs, and never bypass scheduler or governance.

## 11. Data Model (Suggested)

- `ShortLink`: id, code, originalUrl, createdAt, expiresAt, status, owner/demo user.
- `ClickEvent`: id, shortLinkId, occurredAt, referrer (sanitized), userAgent (bounded/sanitized), requestId.
- `IdempotencyRecord`: key, requestFingerprint, responsePayload/reference, createdAt, expiry.
- `AgentRun`: id, scenarioType, rawRequirement, normalizedRequirement, status, createdAt, startedAt, completedAt, planVersion.
- `WorkflowDefinition`/`WorkflowNodeDefinition`: graph definition and dependencies.
- `RunNode`: runId, nodeId, status, attemptCount, inputArtifactRefs, outputArtifactRefs, startedAt, completedAt, errorCode, errorSummary.
- `StageAttempt`: nodeId, attempt, status, timestamps, error, providerUsed, fallbackUsed.
- `Artifact`: id, runId, nodeId, artifactType, version, content (structured JSON/text), checksum, createdAt, status, parentArtifactIds.
- `Approval`: id, runId, nodeId/artifactId, status, approver, comment, policy, createdAt, decidedAt.
- `AuditEvent`: id, runId, actorType, actorId, eventType, timestamp, details, correlationId.
- `PolicyDecision`: id, runId, action, outcome, reason, policyVersion, timestamp.

Use normalized relational tables for core state. Avoid over-engineering event sourcing; append audit events while maintaining current state tables for efficient reads.


## 12. Testing Strategy

### Unit tests
- URL normalization/validation and unsafe scheme rejection.
- Short-code generation, uniqueness, collision retry bound.
- Idempotency fingerprint and duplicate request behavior.
- Analytics aggregation.
- Workflow graph validation (cycles, missing dependencies, duplicate node IDs).
- State transition legality.
- Dependency readiness and synchronization behavior.
- Retry eligibility and maximum attempts.
- Policy evaluation and approval requirements.
- Downstream invalidation/re-planning.
- Artifact versioning and rollback.
- Metric calculations.

### Integration tests
- Link creation → redirect → click event → analytics.
- Persistence/restart behavior for runs and node states.
- Parallel independent nodes and join node waits for all dependencies.
- Approval gate blocks downstream execution until approval.
- Rejection blocks gated action and captures feedback.
- Failure → bounded retry → fallback or terminal failure.
- Pause/safe-stop prevents new scheduling.
- Upstream change stales downstream nodes and triggers re-plan.
- Rollback restores prior artifact version without deleting history.

### End-to-end demo tests
- Greenfield scenario reaches release readiness.
- Brownfield scenario identifies fixture impact and proposes idempotency changes.
- Ambiguous scenario pauses for clarification/approval and resumes safely.
- AI provider unavailable: mock fallback completes a demo run.

## 13. Non-functional Requirements
- Modular architecture and clear separation of product domain, orchestration, agents, governance, persistence, and presentation.
- Secure defaults and no secrets committed.
- Consistent API errors and validation.
- Useful structured logs with correlation IDs.
- Recoverable persisted workflow execution.
- UI communicates that generated code/patches are proposals and not automatically applied.
- Demo scenarios run reliably with predictable timing and seeded data.
- Keep implementation approachable and explainable during an interview.

## 14. Implementation Plan — Complete Today (Autonomous Execution)

**Deadline: complete the working project in this session/today.** Do not treat this as a 2–3 day roadmap. Use short milestones only as internal checkpoints; continue automatically through all phases without waiting for user approval. If time or complexity threatens completion, simplify the implementation while keeping the orchestration demo functional. Use deterministic mock agents to guarantee an end-to-end demo. Do not stop after Spec Kit artifacts are generated.

### Phase 0 — Repository reconnaissance
- Inspect current repository, conventions, existing dependencies, and run commands.
- If repository is empty, initialize the project using the proposed stack.
- Write a brief architecture decision record for any deviation from this plan.

### Phase 1 — Foundation
- Set up backend, frontend, database, migrations, local Docker Compose, config, health checks, API docs.
- Implement error model, logging, and request correlation.
- Add CI-quality test commands and seed data.

### Phase 2 — URL Shortener Product
- Implement create/list/get/deactivate, redirect, expiration, validation, idempotency, click tracking, analytics.
- Add unit/integration tests and OpenAPI.
- Ensure the product demo works independently of the agent system.

### Phase 3 — Orchestration Core
- Implement graph model and validation, node states, dependency scheduler, parallel execution, synchronization, run persistence, artifact versioning, and event log.
- Implement run start/pause/resume and recovery behavior.
- Add metrics aggregation.
- Add tests proving parallelism and dependency gates.

### Phase 4 — Agents and Governance
- Implement deterministic mock agents and structured agent contract.
- Add requirements analysis, decomposition, architecture, implementation proposal, testing, docs, and release readiness agents.
- Implement policy engine, approval checkpoints, rejection handling, retries, fallback, safe-stop, and rollback.
- Implement re-planning and downstream stale-node invalidation.
- Add tests for all governance paths.

### Phase 5 — Scenarios and UI
- Seed greenfield, brownfield fixture, and ambiguous scenarios.
- Implement dashboard, workflow graph, artifact viewer, approvals, audit timeline, and metrics.
- Connect UI to APIs and show live/polling progress.
- Ensure all scenarios can be demonstrated with mock agents and no API keys.

### Phase 6 — Final validation and documentation (same-day finish)
- Run backend/frontend tests, lint/build, and end-to-end demo scenarios.
- Fix critical issues; document known limitations.
- Create README setup/run instructions, architecture overview, API reference, scenario walkthroughs, risk/trade-offs, and demo script.
- Produce a final engineering summary including plan/rationale, artifacts, assumptions, risks, validation results, limitations, and next steps.
- Do not leave a phase unfinished for a future day; if necessary, implement a thin, demonstrable version and document the limitation.

## 15. Demo Script (Interview)

1. Start the application in local demo mode and show health/readiness.
2. Launch Greenfield scenario; show normalized requirement, assumptions, dependency graph, and concurrent independent nodes.
3. Show the synchronization point waiting for multiple upstream tasks.
4. Open an artifact and show version, provenance, and input references.
5. Trigger or use a seeded transient failure to demonstrate bounded retry and fallback.
6. Pause the run to demonstrate safe-stop; resume after operator action.
7. Change an upstream requirement and show re-planning plus stale downstream nodes being invalidated.
8. Show an approval gate; reject once (audit record), then approve a corrected proposal.
9. Show rollback to a prior artifact version and verify audit history remains.
10. Run Brownfield scenario and explain fixture-based impact analysis.
11. Run Ambiguous scenario; show open questions/assumptions and human approval before proceeding.
12. Show metrics: successful/failed runs, retries, rollback frequency, stage latency, total duration.
13. Demonstrate URL creation, redirect, and click analytics.


## 16. Risks and Trade-offs

- **Scope risk:** a fully autonomous coding platform is too large for one day. Focus on a compact, working end-to-end prototype using deterministic agent simulations and structured proposals, with optional LLM enhancement.
- **AI reliability:** LLM outputs are nondeterministic and may be invalid. Validate structured outputs, provide fallback, and never bypass policies.
- **Parallel execution:** avoid shared mutable context; nodes consume immutable/versioned artifacts.
- **Persistence/recovery:** avoid duplicate node execution by using state transitions, attempt IDs, and idempotent stage handlers.
- **Rollback:** rollback is artifact-level in the prototype, not a real git/deployment rollback.
- **Security:** never run generated shell/code automatically; code changes remain proposals pending human approval.
- **Analytics privacy:** sanitize and minimize user-agent/referrer data; document retention assumptions.
- **Demo complexity:** optimize for reproducible demos with seeded scenarios and mock provider.

## 17. Definition of Done

- A clean clone can be run locally following README steps.
- URL shortener endpoints work and tests cover core behavior.
- Agentic runs are persisted and visible from the UI.
- Graph scheduler supports dependencies, concurrency, synchronization, and state recovery.
- Approval gate, policy denial, bounded retry, fallback, safe-stop, rollback, and re-plan are each demonstrable.
- Greenfield, brownfield, and ambiguous scenarios are complete and repeatable.
- Run/stage audit events and required metrics are visible.
- No automatic production deployment or unapproved repository mutation is possible.
- Automated tests and build pass; limitations and trade-offs are documented.
- Final engineering summary is available in the app or generated as a downloadable/exportable artifact.

## 18. Instructions to the Coding Agent

- Start by inspecting the repository; do not overwrite or replace existing work without understanding it.
- Use Spec Kit as the workflow scaffold, but complete its specification/design/tasks steps quickly and then implement end-to-end today. Do not pause for user approval between phases.
- If a decision is ambiguous, record the assumption and choose the simplest safe option; ask only if it materially blocks implementation. Otherwise continue autonomously.
- Keep the project runnable after every short milestone and continue until all deliverables are complete today.
- Prefer clean, maintainable, modular code over unnecessary abstractions.
- Do not claim features are implemented unless code and tests prove they exist.
- Never expose secrets; do not execute generated code, shell commands, destructive operations, or deployment actions autonomously.
- Add tests alongside implementation and run them before claiming completion.
- Maintain a concise decision log and update README/architecture docs as the implementation evolves.
- Prioritize assessment-critical orchestration capabilities over visual polish or optional integrations.
