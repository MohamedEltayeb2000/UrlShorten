# Assignment map: how each requirement is met

This page is for the reviewer. It follows the assignment's structure (objective, eight core requirements, five deliverables, eight
evaluation criteria) and points to the code, tests and documents that answer each item. Paths are relative to the repository root.

**Interpretation.** I read the assignment as: build an agentic software-engineering system that takes a requirement through the SDLC
with governance, using a URL shortener as the sample product. The shortener is also a complete, working service on its own
(`backend/.../shortener`, `analytics`). Honest limits are in section 4.

**Fastest way to see it working:** follow `README.md` (run the backend and frontend), open the Metrics page, launch the Greenfield,
Brownfield and Ambiguous scenarios, and look at Runs, Approvals and Artifacts. `docs/demo-script.md` is a timed walkthrough.

## 1. Core requirements

| # | Requirement | How it is met | Where to look | Proof (tests) |
|---|-------------|---------------|---------------|---------------|
| 1 | Requirement understanding (intent, ambiguity, normalization) | The analyst agent derives goals, constraints, assumptions and open questions from the requirement text. Vague or incomplete input produces a clarification that waits for a human decision. | `agents/mock/RequirementAnalysis.java`, `RequirementsAnalystAgent.java`; `docs/scenarios.md` (Ambiguous) | `RequirementAnalysis*Test`, Ambiguous scenario IT, Playwright "Ambiguous" journey |
| 2 | Task decomposition with dependencies and sequencing | The planner turns the analysis into ordered tasks with dependencies; the run itself is an 8-stage dependency graph with dependency levels. | `agents/mock/PlannerAgent.java`, `orchestrator/DefaultWorkflow.java`, `GraphValidator.java`; `specs/002-phased-quality-gates/tasks.md` (164+ tasks used to build the system) | `DefaultWorkflowTest`, `GraphValidatorTest` |
| 3 | Codebase reasoning (brownfield) | The brownfield analyzer reads a source directory, builds a class/endpoint/table graph and reports impacted modules, APIs and data flows for a change. | `agents/mock/BrownfieldAnalyzer.java`, `fixtures/brownfield/`; `docs/scenarios.md` (Brownfield) | brownfield analyzer tests, Brownfield scenario IT |
| 4 | Workflow orchestration (the differentiator) | See the detailed table in section 2. | `backend/.../orchestrator/`, `docs/architecture.md` | `orchestrator/*IT`, `*Test` |
| 5 | Engineering output (code, API/schema, tests, docs) | The implementation stage emits a code proposal (never applied automatically), the architecture stage emits components/decisions/data flow, testing and documentation stages emit plans and docs. The system itself ships OpenAPI, migrations, ~330 backend / 72 frontend / 4 browser tests. | `agents/mock/`, `backend/src/main/resources/db/migration/`, `docs/api.md`, `OpenApiConfig.java` | `mvn test`, `npm test -- --run`, `npm run e2e` |
| 6 | Validation and risk control | Five gates per phase (tests with requirement traceability, security, review, performance, AI-output evaluation); risk register and trade-offs; failure scenarios are tested (retry exhaustion, held output, policy denial, provider outage). | `scripts/gate-*.ps1`, `docs/gates/`, `docs/risks-and-tradeoffs.md`, `docs/security/`, `docs/traceability.md` | `HeldOutputIT`, `RetryExhaustedFallbackIT`, `FiringPolicyIT`, hostile-input suites |
| 7 | Controlled autonomy | Agents only propose; approval gates guard high-impact stages; an agent actor cannot approve; output that fails evaluation or policy is held. Approvals record who decided and a change record. | `governance/ApprovalService.java`, `ApprovalRules.java`, `PolicyEngine.java`, `evaluation/AgentOutputEvaluator.java` | `GovernanceFlowIT`, `ChangeControlIT`, `HeldOutputIT` |
| 8 | Final engineering summary | Plan and rationale, artifacts, risks, assumptions, limitations; also generated per run from the UI. | `docs/final-summary.md`, `explanation.md`, `summary/` package | summary tests |

## 2. Orchestration, item by item (requirement 4)

| Brief item | Implementation | Test |
|------------|----------------|------|
| Explicit dependency graph | `WorkflowDefinition`, `DefaultWorkflow`, validated by `GraphValidator` (cycles and missing dependencies rejected) | `GraphValidatorTest` |
| Entry/exit gates | `StageGates`: prerequisites and current artifacts on entry; stored output, evaluation and policy on exit; recorded in audit and shown in the run detail | `StageGatesTest`, `StageGatesIT` |
| Sequential and parallel paths with synchronization | `RunScheduler` runs independent stages concurrently (architecture and implementation plan in parallel, join at implementation); join stages wait for all inputs | `RunExecutionIT`, performance test for parallel speed-up |
| Stateful, non-linear execution | Persisted node state machine with history; `RunTransitions` is the single choke point; restart recovery | `NodeStateMachineTest`, `RunPersistenceTest` |
| Cross-stage context and decision lineage | Versioned artifacts with upstream references (lineage) and STALE marking | `artifacts/`, `AutoReplanIT` |
| Human approval checkpoints | Approvals before implementation and release readiness; clarification decisions; actor identity recorded | `GovernanceFlowIT` |
| Bounded retries, fallback | `RetryPolicy` with backoff; transient failures retried, then a labeled simulated fallback | `RetryPolicyTest`, `RetryTransientThenSuccessIT`, `RetryExhaustedFallbackIT` |
| Rollback | Artifact rollback to an earlier version via the API/UI, with downstream impact | `RunControlService`, `ArtifactEditApiIT` |
| Safe-stop | Pause cooperatively stops a running stage before it stores output; resume runs it once | `SafeStopIT` |
| Policy guardrails (security, compliance, change control) | `PolicyEngine` checks secrets, unsafe URLs, destructive commands, production deployments, migrations; HIGH-risk approvals need a change ticket and justification | `FiringPolicyIT`, `ChangeControlIT` |
| Audit-grade observability and traceability | Append-only audit trail with request ids; gate, policy, approval and re-plan events; per-run events page | `audit/`, `AuditTestSupport` use in ITs |
| Reliability metrics (success rate, retries, rollbacks, MTTR, end-to-end latency) | `metrics/` package, Metrics page, per-run metrics | metrics tests |
| Dynamic re-plan on upstream change | `ChangeDetectionService`: editing or rolling back an artifact stales downstream results and re-runs only those stages, with approvals re-applied | `AutoReplanIT`, `AutoReplanWhileRunningIT` |
| Controlled agent autonomy | Provider contract (`AgentProvider`), deterministic default agents, optional LLM provider behind the same contract, evaluation before use | `evaluation/`, `eval/` sets |

## 3. Deliverables and evaluation criteria

| Deliverable | Location |
|-------------|----------|
| Working prototype | `backend/`, `frontend/`, `docker-compose.yml`; run steps in `README.md` |
| Architecture overview | `docs/architecture.md` (components, orchestration model, control flow, decisions) |
| Three scenarios (each with decomposition, orchestration, validation) | `docs/scenarios.md`; launch from the Metrics page; `eval/*.json` |
| Setup instructions | `README.md` |
| Testing approach, limitations, trade-offs | `docs/risks-and-tradeoffs.md`, `docs/traceability.md`, `explanation.md` section 4 |

Each of the eight evaluation criteria, with evidence and how to verify it, is in `docs/evaluation-guide.md`. The reasoning behind the
choices (why this design, what was rejected) is in `explanation.md`. Delivery history (seven phases, each gated) is in `docs/publish-log.md`.

## 4. Honest limits

- The agents are deterministic, rule-based simulations. An optional LLM provider exists behind the same contract but has only been tested against a stub endpoint.
- Approver identity is a demo header, not authentication; the scheduler, rate limiter and failure injector are single-instance.
- Spring Framework 6.2.19 CVEs are suppressed until 2026-12-31 (no fixed release; affected features unused); see `docs/security/residual-risk.md`.
- Change tickets are checked by format only. No human first-use or screen-reader test was done; browser tests are automated.
