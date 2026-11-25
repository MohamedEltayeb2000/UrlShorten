# Final engineering summary

## What was built
A URL shortener (create, redirect, expiry, deactivation, idempotent creation, click analytics, rate limiting) and, on top of it, a
governed agentic SDLC orchestration engine that takes a requirement through eight stages (requirements analysis, task
decomposition, architecture, implementation plan, implementation proposal, testing, documentation, release readiness) as a
persisted dependency graph with parallel branches and join points. Delivered in six phases, each published to GitHub only after
its tests, security, review, performance and (from phase 4) AI-evaluation gates passed. Details: `docs/architecture.md`,
`docs/scenarios.md`, `docs/api.md`, `docs/demo-script.md`; the reasoning behind the choices: `explanation.md`.

## Plan and rationale
Spec-driven (constitution, specs, plan, 164+ tasks), simplest-first phases, deterministic simulated agents by default so every
scenario is repeatable without an API key, and evidence (traceability, reviews, walkthroughs, performance, evaluations, publish log)
committed beside the code.

## Artifacts
Code (`backend/`, `frontend/`), gate scripts (`scripts/`), evaluation sets (`eval/`), the brownfield fixture (`fixtures/`),
evidence under `docs/`, and the specification set under `specs/`.

## Validation
About 280 backend and 57 frontend automated tests. Every functional requirement of both features maps to at least one tagged test
(`docs/traceability.md`). Each phase ran the security gate (secret scan, hostile-input suites, dependency scan), a recorded
review, performance budgets (redirect p95, parallel speed-up, scenario demo time, bundle size) and, for agent output, the
rule-based evaluation with a deliberately degraded set that must fail.

## Risks and trade-offs
See `docs/risks-and-tradeoffs.md`. The main ones: the approver identity is a demo header, not authentication; the scheduler lock is
in-process (single instance); agents are deterministic simulations and the optional LLM provider has only been run against a stub;
rate limiting is in memory; Spring Framework 6.2.19 has CVEs with no fixed release (unreachable features; suppression expires
2026-12-31, owner decision needed).

## Assumptions
Single-tenant local demo; Java 21+ and Node 20+; PostgreSQL via Docker or embedded H2; no production deployment of any kind.

## Limitations and next steps
No real authentication or multi-instance support; no real LLM validation; policy rules for commands, database changes and
deployments exist but no stage proposes those actions; rollback is artifact-level only. A human first-use test of the UI, a
screen-reader pass, and moving to a fixed Spring release are the first follow-ups.
