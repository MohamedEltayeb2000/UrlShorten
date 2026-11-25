# Explanation: why the system is built this way

This file explains the choices behind the project and maps them to the assessment brief (reproduced in the
appendix). It separates what is **built and tested today** from what is **planned**, so nothing here claims more
than the code and tests prove.

## 1. Where things stand (2026-11-24)

| Area | State |
|------|-------|
| Spec, plan, research, contracts, tasks, constitution v1.1.0 | Done (`specs/`, `.specify/memory/constitution.md`) |
| Phase gates (tests + traceability, security, review, performance, evaluation, publish) | Built and used for every phase |
| P1 shortener core; P2 expiry, analytics, idempotency, rate limiting | Published (`phase-1`, `phase-2`) |
| P3 orchestration core; P4 agents, artifacts, audit, evaluation; P5 policy, approvals, retry/fallback, re-plan, rollback | Published (`phase-3` to `phase-5`) |
| P6 scenarios, metrics (incl. MTTR), engineering summary, optional LLM provider, documentation | Published (`phase-6`) |
| P7 brief conformance (gates, auto re-plan, change control, safe-stop, analysis, browser e2e) | Published with the `phase-7` tag when its gates pass; see `docs/publish-log.md` |

The orchestration layer, which the brief calls the critical differentiator, is **implemented and tested** (graph, state machine,
parallel scheduler with joins, approvals, retry, fallback, safe-stop, rollback, re-plan, audit, metrics). Remaining gaps are in
section 4.

## 2. Why these decisions

### Spec-driven (Spec Kit) before code
The brief rewards "clarity and defensibility of decisions". Writing the constitution, specs, plan and tasks first gives
every later choice a written reason and a requirement ID. The cost is time before the first line of code; the benefit is
that a reviewer can follow any code or test back to a requirement.

### A constitution with seven principles
Principles I-VII (orchestration is the product, governed autonomy, deterministic demo, resilience, traceability, tests,
phased GitHub delivery) turn the brief's priorities into rules that the plan and tasks are checked against. A conflict
between the plan and the constitution is a blocking finding, not a judgment call.

### Phase by phase, simplest first
You asked for simple functionality delivered in phases. The shortener comes first (P1-P2) because it is small, easy to
verify, and has no dependency on the agent system. Orchestration is added only after a working base exists, so a
failure in a later phase cannot hide a broken foundation.

### Every phase passes the same gates before it is published
| Gate | Why it exists |
|------|---------------|
| Tests + requirement traceability | "Make sure it satisfies the requirement through test cases." Every requirement ID maps to a tagged test; a requirement with no passing test blocks its phase |
| Security | The product accepts untrusted URLs and redirects users. Hostile-input corpus, secret scanning, dependency scanning, and a test that no endpoint can execute code, apply changes or deploy |
| Code review | A recorded review against a fixed checklist, with findings, fixes and waivers kept in the repo |
| AI evaluation (P4+) | Agent output is judged against explicit, versioned criteria, with a "degraded" set that must fail |
| Performance | Stated budgets (redirect p95, concurrency speed-up, bundle size) measured, with the environment recorded |
| Publish | Several focused commits, a `phase-N` tag, and a refusal if any gate is red or the owner has not confirmed the remote |

A gate that cannot run reports `NOT_RUN` and blocks. It never passes silently. That is why the Phase 1 vulnerability
scan is recorded as a waiver rather than a pass.

### Deterministic mock agents by default
LLM output is non-deterministic and needs a key. A mock provider makes every scenario repeatable and every test
reliable, and lets the evaluator assert exact behavior. An optional LLM provider (planned in P6) sits behind the same
contract and validation, so it can never bypass the scheduler or governance.

### Stack: Java 21 + Spring Boot, React + TypeScript, PostgreSQL + Flyway
The stack was fixed by the brief. Concrete reasons it fits: Spring gives validated DTOs, a managed thread pool for the
parallel scheduler and Actuator health; Flyway gives one migration per phase and never edits history; PostgreSQL gives the
database-enforced unique short code. H2 is used only for fast, isolated tests and the optional no-Docker `local` profile.
The frontend is deliberately plain (three runtime dependencies, one page per capability) because you asked for a simple
frontend and because simplicity is checked by a test.

### A persisted graph, not a hard-coded sequence
The brief asks for "an explicit dependency graph" with "non-linear, stateful execution". The plan stores the workflow as a
definition, validates it (no cycles, missing dependencies or duplicate IDs), tracks each node through an explicit state
machine with stored history, and starts a node only when every prerequisite has succeeded. This is what makes parallel
paths, join points, restart recovery and re-planning possible.

### Proposals, never actions
Generated code and patches are proposals until a human approves, and repository mutation is off by default. A test already
asserts that no endpoint exposes execute, deploy or apply behavior. This is the "controlled autonomy" the brief asks for.

### Evidence lives in the repo
Traceability, gate results, reviews, walkthroughs, performance results and the publish log are committed under `docs/`.
That makes "validation and risk management rigor" inspectable instead of asserted.

### Small, frequent commits
You asked for several commits rather than one. Work is committed in logical steps, and publishing splits into grouped
commits (backend, frontend, scripts, specs, evidence).

## 3. Mapping the brief to the design

| Brief item | Design | Status |
|------------|--------|--------|
| 1 Requirement understanding | Requirements-analysis agent normalizes the requirement, records assumptions and open questions; Ambiguous scenario pauses for a human decision (`NEEDS_CLARIFICATION`) | Planned P4/P6 |
| 2 Task decomposition | Task-decomposition stage emitting tasks with dependencies; the spec/tasks files themselves follow the same rule | Planned P3/P4 (tasks.md done) |
| 3 Brownfield reasoning | Seeded fixture in `fixtures/brownfield/`; impact analysis grounded in the fixture, not arbitrary repositories | Planned P4/P6 |
| 4 Workflow orchestration | Persisted graph, state machine, bounded parallel scheduler with joins, cross-stage artifacts with lineage, approval gates, bounded retries, fallback, rollback, safe-stop, policy engine, audit trail, metrics, re-planning with stale propagation | Planned P3-P6 |
| 5 Engineering output | Production-style code, OpenAPI, unit/integration tests, docs | P1 done for the shortener |
| 6 Validation and risk control | Gates, hostile-input suite, risk and trade-off document | P1 done; docs in P6 |
| 7 Controlled autonomy | Agents propose, humans approve, agents cannot approve their own work | Planned P5 |
| 8 Final summary | Generated per run, plus `docs/final-summary.md` | Planned P6 |
| Deliverables: prototype, architecture overview, three scenarios, setup, testing approach, limitations | README (setup) exists; architecture, scenarios and risk docs are tasks T143 / T155 | Partly done |

### Evaluation criteria

| Criterion | How it is addressed |
|-----------|---------------------|
| Effectiveness of agentic orchestration | P3-P5; each behavior has a named test (parallelism, join wait, approval block, retry, rollback, re-plan) |
| Architecture / system design quality | Module boundaries (`shortener`, `orchestrator`, `agents`, `governance`, `audit`, `artifacts`); a test fails if the shortener imports agent code |
| Depth of decomposition and execution | 164 dependency-ordered tasks in `tasks.md`; the runtime graph has 8 stages |
| Realism / quality of outputs | Evaluator criteria plus Brownfield fixture grounding |
| Validation and risk rigor | Five gates per phase, evidence files, waivers recorded |
| Clarity and defensibility of decisions | This document, the constitution, research.md with alternatives considered |
| Engineering principles | Modular, tested, secure defaults, scalable only to the extent a one-day local prototype needs |
| Engineering judgment | Simplest-first phasing, honest status, refusal to claim unbuilt features |

## 4. Gaps against the brief and limitations

Phase 7 closed the gaps found by comparing the build with the brief: explicit entry/exit gates, automatic re-plan on upstream change,
change control with firing policy rules, cooperative safe-stop, text-driven requirement analysis, structural brownfield analysis, MTTR,
and verification in a real browser and against PostgreSQL. What remains, honestly:

1. **The agents are simulations.** Ambiguity detection, decomposition and code analysis are deterministic heuristics; they are not a language model.
   The optional LLM provider supports real model output but has only been tested against a stub endpoint.
2. **Identity is a demo header,** not authentication, so "agents cannot approve their own work" holds for honest callers only.
3. **Single instance.** The scheduler lock, rate limiter and failure injector are in-process.
4. **Security waiver.** Spring Framework 6.2.19 CVEs are suppressed until 2026-12-31 (no fixed release; affected features unused).
5. **No human first-use test or screen-reader pass** has been done; the browser tests are automated.
6. **Change tickets are validated by format only;** no ticketing system is consulted.

## Appendix: the assessment brief as received

### 4. Core Requirements
1) Requirement Understanding: Interpret intent, identify ambiguity, normalize into a clear engineering problem.
2) Task Decomposition: Convert high-level requirements into actionable tasks with dependencies and sequencing.
3) Codebase Reasoning (Brownfield): Identify impacted modules/services/APIs/data flows and demonstrate architectural understanding.
4) Workflow Orchestration (Critical Differentiator): Design and implement an agentic orchestration layer that coordinates
the full SDLC lifecycle across requirements, architecture/design, implementation, testing, documentation, release
readiness, and demonstrates non-linear, stateful execution with governance rather than simple linear task chaining. The
orchestration must use an explicit dependency graph with entry/exit gates, support sequential and parallel paths with
synchronization, preserve cross-stage context and decision lineage, enforce human approval checkpoints for high-impact
actions, and include bounded retries, fallback, rollback, and safe-stop controls. It must also embed policy guardrails for
security, compliance, and change control, provide audit-grade observability and traceability, track reliability metrics
such as success rate, retry/rollback frequency, MTTR, and end-to-end latency, and dynamically re-plan when upstream
outputs change while maintaining governance and controlled agent autonomy.
5) Engineering Output Generation: Produce production-quality code, API/schema definitions, unit/integration tests, and
supporting documentation with clean design and maintainability.
6) Validation and Risk Control: Identify risks/trade-offs/failure scenarios and define validation and safety guardrails.
7) Controlled Autonomy: Agents execute multi-step work; humans provide oversight, approvals, and final quality control.
8) Final Engineering Summary: Include plan/rationale, artifacts, risks/trade-offs/validation, assumptions, and limitations.

### 5. Deliverables
- Working prototype (runnable end-to-end)
- Architecture overview (components, orchestration model, control flow, key decisions)
- Three scenarios: greenfield, brownfield, ambiguous (each shows decomposition, orchestration, validation)
- Setup instructions
- Testing approach, limitations, and trade-offs

### 6. Evaluation Criteria
- Effectiveness of agentic orchestration
- Architecture/system design quality
- Depth of decomposition and execution quality
- Realism/quality of outputs
- Validation and risk management rigor
- Clarity and defensibility of decisions
- Core engineering principles: modular, testable, reliable, secure, scalable code with safe change management
- Engineering judgment
