# Implementation Plan: Phased Delivery with Quality Gates

**Branch**: `002-phased-quality-gates` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-phased-quality-gates/spec.md`

## Summary

Deliver the full system described in feature 001 (and `plan.md` at the repo root) in six small
phases, simplest first. Every phase ends with the same five gates — tests (with requirement
traceability), security, code review, AI evaluation (from the phase where agents exist), and
performance — and is committed and pushed to GitHub only when all gates pass. The frontend is
a deliberately plain React app that grows one page per phase. Gates are implemented as
repeatable scripts and tests inside the repo, so "phase complete" is a command, not a claim.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5 / Node 20 (frontend)

**Primary Dependencies**: Spring Boot 3.x (Web, Validation, Data JPA, Actuator), Flyway, springdoc-openapi, React + Vite; no UI component library

**Storage**: PostgreSQL via Docker Compose; H2 only for isolated unit tests

**Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc; Vitest + React Testing Library; Testcontainers only if quick

**Target Platform**: Local developer machine (Windows/macOS/Linux) with Docker

**Project Type**: Web application (backend + frontend)

**Performance Goals**: Create/redirect p95 < 1 s (target far lower); scenario demo < 2 min; concurrent independent stages measurably faster than sequential

**Constraints**: No AI API key needed; no secrets committed; generated code never applied or executed; one-day build, so thin vertical slices

**Scale/Scope**: Single-tenant demo; ~6 phases, ~8 workflow stages, 3 scenarios

**Quality tooling (resolved in research.md)**: Gitleaks (secrets), OWASP Dependency-Check Maven plugin + `npm audit` (dependencies), JUnit hostile-input suites (input security), JUnit rule-based evaluator (AI evaluation), JUnit/Java `HttpClient` burst test (performance), markdown review checklist per phase.

## Phase Plan

| Phase | Simple capability delivered | Frontend page added | Spec FRs covered (001 / 002) |
|-------|-----------------------------|---------------------|------------------------------|
| P1 Shortener core | Create link, redirect, validation, unsafe-scheme rejection, error model, health, OpenAPI | Shorten form + link list | 001: FR-023,024,025(redirect),026,029 / 002: see ownership table below |
| P2 Hardening & analytics | Expiry, deactivate, click events, analytics, idempotency, collision retry, rate limit | Click counts column, simple analytics panel | 001: FR-025,027,028 |
| P3 Orchestration core | Graph definition + validation, state machine, scheduler, parallel + join, persistence, recovery, start/pause | Run launcher + stage status list (polling) | 001: FR-001–004,006,007 (FR-005 scoped context is delivered in P4) / 002: see ownership table below |
| P4 Agents, artifacts, audit, evaluation | Contract, mock agents for all stages, artifact versions, audit trail, evaluator + eval set | Artifact viewer (version list) | 001: FR-005,008,009,020(audit) / 002: see ownership table below |
| P5 Governance & resilience | Policy engine, approvals, retry/fallback, rollback, re-plan, stale propagation | Approvals page (approve/reject + comment) | 001: FR-011–017,019 |
| P6 Scenarios, metrics, summary | Greenfield/Brownfield/Ambiguous presets, metrics, final engineering summary, docs | Metrics page, scenario buttons | 001: FR-018,020,021,022,030 |

Phases are strictly ordered; P1–P2 work without any agent code (FR-003 of 002).

### Mapping to root `plan.md` §14 (constitution Principle VII)

| Root §14 phase | Delivered as |
|----------------|--------------|
| 0 Reconnaissance, 1 Foundation | tasks.md Setup + Foundational (repo, skeletons, gates, plumbing) |
| 2 URL Shortener Product | P1 + P2 |
| 3 Orchestration Core | P3 |
| 4 Agents and Governance | P4 + P5 (split so evaluation lands before governance) |
| 5 Scenarios and UI | P6 (UI pages are added incrementally in P1–P6) |
| 6 Final validation and documentation | P6 gate block + tasks.md Polish |

Naming: "P1–P6" always means delivery phases; tasks.md "Phase N" headings are task-list
sections, and `--phase N` on scripts means delivery phase PN.

### Requirement ownership for feature 002 (recorded in `docs/phases.json`)

| Owner | 002 requirements | Why |
|-------|------------------|-----|
| P1 (gates must work for the first publish) | FR-001–009, 014, 016–020 | Phasing, traceability, security, review, performance, frontend and publish machinery are needed from the first phase and are re-verified every phase |
| P3 | FR-015 | Parallel speed-up needs the scheduler |
| P4 | FR-010–013 | Evaluation needs agents |

Feature 001 requirement ownership is the "Spec FRs covered" column above, plus: 001-FR-010 (provider
abstraction, fallback, optional LLM provider) is owned by P6 (retry/fallback mechanics land in P5, the
optional provider and provider-unavailable scenario in P6), and 001-FR-012 identity/actor model by P5.
This ownership table is the single source of truth; `docs/phases.json` is generated from it and the
phase table above is a summary.

### Scope notes
- 001-FR-010's optional external AI provider is delivered as a thin, env-configured `LlmAgentProvider`
  that validates output and falls back to mock; it is exercised with a disabled/fake endpoint only, so
  the demo never needs a key.
- Root `plan.md` §18 says not to pause between phases; the constitution (Principle VII) overrides it for
  one point only: the GitHub remote and branch must be owner-confirmed before the first push.
  Until then phases are committed and tagged locally with status `PUBLISH_PENDING`.

## Gates (apply to every phase)

| Gate | How it is run | Blocks when |
|------|---------------|-------------|
| Tests + traceability | `scripts/gate-tests` runs backend + frontend tests and regenerates `docs/traceability.md` from test annotations (`@Requirement("001-FR-024")`) | Any test fails, or a requirement owned by the phase has no passing test, or an earlier phase regresses |
| Security | `scripts/gate-security`: hostile-input suites, Gitleaks, Dependency-Check, `npm audit`, "no apply/execute without approval" tests | Finding ≥ HIGH, or any sub-check "not run" |
| Code review | Structured review written to `docs/reviews/phase-N.md` against `docs/review-checklist.md` | Open blocking finding without recorded waiver |
| AI evaluation (P4+) | `scripts/gate-eval` runs evaluator over `eval/*.json` for 3 scenarios; writes `docs/evals/phase-N.json` with criteria version | Score below threshold (default 100% for deterministic agents) or degraded fixture not caught |
| Performance | `scripts/gate-perf`: budget tests incl. parallel-vs-sequential and redirect burst; writes `docs/perf/phase-N.md` with environment | Budget exceeded (generous thresholds) |
| Publish | `scripts/publish-phase N` verifies all gate outputs, then commits and pushes labeled `phase-N` | Any gate not green. If the remote/branch is not yet owner-confirmed: local commit + tag only, status `PUBLISH_PENDING`, pushed later |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Orchestration Is the Product | PASS | P3 delivers persisted graph + state machine + scheduler; data model has stage transitions |
| II. Governed Autonomy | PASS | P5 policy/approvals; security gate asserts no apply/execute without approval |
| III. Deterministic Demo, Optional AI | PASS | Mock provider default; evaluator targets deterministic output; no keys |
| IV. Resilience & Reversibility | PASS | P5 retry/fallback/rollback/re-plan with tests |
| V. Traceability & Observability | PASS | Audit trail in P4, metrics in P6, traceability doc every phase |
| VI. Test-Backed, Simple, Reproducible | PASS | Gate scripts; thin phases; README quickstart |
| VII. Phased Delivery to GitHub | PASS (pending) | `publish-phase` script; first push requires owner-confirmed remote — currently none |

Post-design re-check: PASS. No complexity violations.

## Project Structure

### Documentation (this feature)

```text
specs/002-phased-quality-gates/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── gates.md            # gate script CLI + output contract
│   └── api-by-phase.md     # HTTP endpoints introduced per phase
└── tasks.md                # created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/com/example/shortener/
│   ├── shortener/      # P1–P2
│   ├── analytics/      # P2
│   ├── orchestrator/   # P3, P5
│   ├── agents/         # P4
│   ├── artifacts/      # P4
│   ├── audit/          # P4
│   ├── governance/     # P5
│   ├── evaluation/     # P4
│   └── common/         # error model, correlation id, rate limit
├── src/main/resources/db/migration/   # Flyway per phase
└── src/test/java/...  # tests carry @Requirement tags

frontend/
├── src/{pages,components,api,hooks}/
└── tests/

eval/                    # evaluation sets (greenfield, brownfield, ambiguous, degraded)
fixtures/brownfield/     # seeded small codebase for Scenario B
scripts/                 # gate-*.{ps1,sh}, publish-phase
docs/                    # traceability.md, reviews/, evals/, perf/, architecture, README parts
docker-compose.yml
.env.example
```

**Structure Decision**: Web application (backend + frontend) with gate scripts and evidence under `docs/`, so each phase's proof is versioned alongside the code.

## Complexity Tracking

No constitution violations; none to justify.
