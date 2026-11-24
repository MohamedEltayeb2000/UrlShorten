<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0 (MINOR: new principle VII + workflow gate added)
- Modified principles: none renamed; VII "Phased Delivery to GitHub, Verified by Tests" added
- Added sections: Principle VII; per-phase test/traceability/push step in Development Workflow
- Removed sections: none
- Templates reviewed:
  ✅ .specify/templates/plan-template.md (generic "Constitution Check" gate; no edit required)
  ✅ .specify/templates/spec-template.md (no conflicting mandatory sections)
  ✅ .specify/templates/tasks-template.md (test/governance task types remain optional-compatible)
  ✅ .specify/templates/checklist-template.md (no edit required)
- Follow-up TODOs: none
-->
# URL Shortener Agentic Engineering System Constitution

## Core Principles

### I. Orchestration Is the Product
The workflow engine is the primary deliverable; the URL shortener is the sample product it
builds. Workflows MUST be a persisted dependency graph (code or stored definition), never a
hard-coded call sequence. Nodes MUST use an explicit, validated state machine with stored
transition history, run independent nodes concurrently (bounded), and start a join node only
after every prerequisite has succeeded. Run, node, attempt, artifact, approval, and audit state
MUST be persisted so runs are observable and recoverable after restart.

### II. Governed, Controlled Autonomy (NON-NEGOTIABLE)
Agents propose; humans approve. Generated code and patches are proposals until approved, and
repository mutation is disabled by default. Human approval MUST gate patch application,
destructive operations on non-test data, release-readiness, and any policy-defined high-impact
change. An agent MUST NOT approve its own work. No autonomous production deployment, and no
autonomous execution of generated code, shell commands, or destructive operations. Policy
checks (no secrets, no unsafe URL schemes, no production deploy, test failures block release
absent an audited override) MUST be evaluated and recorded as policy decisions.

### III. Deterministic Demo, Optional AI
A deterministic mock `AgentProvider` MUST be the default and fully usable without any API key.
An LLM provider is optional, configured only via environment variables, MUST conform to the same
structured agent contract, MUST have its output validated, and MUST NOT bypass the scheduler or
governance. Provider failure MUST fall back to mock agents with output clearly labeled as
simulated. API keys MUST never be hardcoded or committed.

### IV. Resilience and Reversibility
Retries MUST be bounded with configurable attempts and backoff, and apply only to transient
failures (never validation or policy failures). Safe-stop MUST halt new scheduling. Upstream
changes MUST compute the downstream closure, mark affected nodes/artifacts stale, prevent stale
outputs from being consumed, create a new plan version preserving history, and re-run only
affected stages. Artifacts MUST be versioned with parent lineage; rollback restores a prior
approved version without deleting history.

### V. Traceability and Observability
Every stage records inputs, outputs, status, timestamps, attempts, decisions, errors, and
artifact links. Audit events are append-only with correlation IDs. Structured logs, request
IDs, and metrics (success rate, retries, rollbacks, durations, stage latency, failed/blocked
counts) MUST be exposed. Claims of completed features MUST be backed by code and passing tests.

### VI. Test-Backed, Simple, Reproducible
Tests MUST accompany implementation and pass before completion is claimed, covering graph
validation, state transitions, readiness/synchronization, retry, policy/approval, re-planning,
rollback, and the three scenarios (greenfield, brownfield, ambiguous). Prefer the simplest safe
design and clean modules over speculative abstraction; ambiguity is resolved by recording an
assumption and choosing the simplest safe option. Setup MUST be reproducible from the README.

### VII. Phased Delivery to GitHub, Verified by Tests
Work MUST be delivered phase by phase (the phases in `plan.md` §14), with each phase committed
and pushed to the user's GitHub repository only after its tests pass. Every functional
requirement (FR-###) and success criterion (SC-###) in the spec MUST map to at least one
automated test, and the mapping MUST be maintained in a traceability document. A phase is not
complete, and MUST NOT be pushed as complete, while its mapped tests are failing or missing.
Pushes go to a feature branch on the user's remote; credentials MUST never be committed, and
the remote and branch MUST be confirmed with the user before the first push.

## Technology & Security Constraints

- Backend: Java 21, Spring Boot 3.x, Maven, Spring Web/Validation/Data JPA. No substitution
  with Python or Node-only backends.
- Data: PostgreSQL via Docker Compose, Flyway migrations; H2 only for isolated unit tests.
- Frontend: React + TypeScript + Vite; typed API client; polling for progress.
- API docs via OpenAPI/Swagger UI; consistent DTOs, validation, and error schema.
- URL safety: validate and normalize URLs, reject unsafe schemes (e.g. `javascript:`), redirect
  only to stored validated URLs, enforce short-code uniqueness in the database with bounded
  collision retries, support idempotency keys, and rate-limit creation and analytics endpoints.
- Privacy: sanitize and minimize stored user-agent/referrer data.
- Out of scope: real production deployment, cloud/Kubernetes, enterprise identity, billing.

## Development Workflow & Quality Gates

- Use Spec Kit (specify → plan → tasks → implement) as scaffold, completing phases quickly
  and continuing autonomously without pausing between phases.
- Keep the project runnable after every milestone; the shortener MUST work independently of
  the agent system.
- Each plan MUST pass the Constitution Check against Principles I–VII; deviations MUST be
  justified in the plan's complexity tracking and recorded in a decision log.
- At the end of each phase: run that phase's tests, update the requirement-to-test
  traceability table, then commit and push the phase to GitHub (Principle VII).
- Before completion: backend and frontend tests, lint, and build pass; the three scenarios run
  end to end with mock agents; limitations and trade-offs are documented.

## Governance

This constitution supersedes other practices. Amendments require a documented change, an
updated Sync Impact Report, a version bump, and review of dependent templates. Versioning
follows semantic versioning: MAJOR for incompatible principle removals or redefinitions, MINOR
for added or materially expanded principles/sections, PATCH for clarifications. Every plan and
review MUST verify compliance; unjustified complexity or violations of Principle II block
approval. `plan.md` at the repository root is the authoritative project brief for runtime
guidance.

**Version**: 1.1.0 | **Ratified**: 2026-11-24 | **Last Amended**: 2026-11-24
