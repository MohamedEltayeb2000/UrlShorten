# Evaluation guide for reviewers

This guide maps each evaluation criterion of the assignment to the evidence in this repository and shows how to check it yourself.
It also explains how the **output of the AI agents** is evaluated by the system itself.

## 1. How AI agent output is evaluated (built into the system)

Every agent result is scored by a rule-based evaluator before it can be stored or used downstream
(`backend/src/main/java/com/example/shortener/evaluation/AgentOutputEvaluator.java`, criteria in `eval/criteria.json`, version 1.0, pass threshold 100%).

| Criterion | What is checked |
|-----------|-----------------|
| `schema_valid` | all contract fields present and valid (status, summary, confidence in 0..1, a confidence note when confidence is low) |
| `stage_complete` | the stage's required structured output exists (for example the design has components, decisions and data flow); a clarification must carry open questions; an Ambiguous requirement must record questions and assumptions |
| `upstream_consistent` | every reference points to a real upstream artifact |
| `no_unsafe_content` | no secrets (also inside JSON), unsafe URL schemes, destructive commands or production-deploy actions |
| `labeled_simulated` | simulated or fallback output is clearly labeled as such |
| `deterministic` | the same input gives the same output (for the simulated agents) |

What happens with the result:
- **Fails evaluation or policy** -> the stage is held (BLOCKED), the event `OUTPUT_FLAGGED` or `POLICY_DENIED` is audited, nothing is stored or used downstream.
- **Needs a clarification or is high impact** -> the stage waits for a recorded human decision; agents can never approve their own work.
- **Evaluation sets** (`eval/greenfield.json`, `brownfield.json`, `ambiguous.json`, `degraded.json`): the golden sets must all pass; the degraded set (14 deliberately broken outputs) must fail on exactly the expected criteria. `ScenarioPipelineEvalIT` also scores the live agents on all three scenarios. Results: `docs/evals/latest.json` (all three scenarios score 1.0, `degradedCaughtAll: true`).
- Run it: `cd backend && mvn test -Dgroups=eval`.

## 2. The eight criteria and where to look

| Criterion | Evidence | How to verify |
|-----------|----------|---------------|
| **Effectiveness of agentic orchestration** | persisted workflow graph with parallel branches and joins, explicit state machine, entry/exit gates, approvals, bounded retry, fallback, safe-stop, rollback, automatic re-plan on upstream change, audit trail, metrics (success rate, retries, rollbacks, MTTR, latency) - `docs/architecture.md` | start the app, launch a scenario from the Metrics page, open Runs / Approvals / Artifacts; `docs/demo-script.md` is a 15-minute walkthrough |
| **Architecture / system design quality** | package boundaries (`shortener`, `analytics`, `orchestrator`, `agents`, `governance`, `artifacts`, `audit`, `evaluation`, `metrics`, `summary`); one choke point for state changes (`RunTransitions`); a test fails if the shortener imports agent code | `docs/architecture.md`, `PhasePlanTest` |
| **Depth of decomposition and execution quality** | 8-stage graph with dependency levels; task decomposition derived from the requirement text; 164+ ordered tasks for the build itself in `specs/002-phased-quality-gates/tasks.md` | `docs/scenarios.md`, `specs/` |
| **Realism / quality of outputs** | structured, evaluated, versioned artifacts with lineage; Brownfield analysis derived from the code structure of a source directory; generated code is only ever a proposal (`applied:false`) | open an artifact in the UI; `docs/scenarios.md` |
| **Validation and risk management rigor** | 5 gates every phase (tests + requirement traceability, security, review, performance, AI evaluation); hostile-input suites; secret and dependency scans; risk register; recorded waivers | `docs/traceability.md`, `docs/gates/`, `docs/reviews/`, `docs/risks-and-tradeoffs.md`, `docs/security/residual-risk.md` |
| **Clarity and defensibility of decisions** | constitution with seven principles; research with alternatives; every decision explained with its reason and its limits | `explanation.md`, `specs/002-phased-quality-gates/research.md`, `.specify/memory/constitution.md` |
| **Core engineering principles** (modular, testable, reliable, secure, scalable, safe change management) | about 330 backend, 69 frontend and 4 real-browser tests; bounded retries and recovery after restart; validated input, no secrets, policy engine, approvals with change tickets; phase-by-phase delivery with gates; scalability is limited to one instance (stated honestly) | `mvn test`, `npm test -- --run`, `npm run e2e`, `docs/publish-log.md` |
| **Engineering judgment** | simplest-first phasing, deterministic default so the demo needs no API key, explicit trade-offs, honest list of what is not done | `explanation.md` section 4, `docs/risks-and-tradeoffs.md` |

## 3. What is honestly not claimed

- The agents are rule-based simulations; the optional LLM provider exists but has only been tested against a stub endpoint.
- Identity is a demo header, not authentication; the scheduler is single-instance.
- No human first-use test or screen-reader pass has been done (the browser tests are automated).
