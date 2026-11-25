---

description: "Task list for phased delivery with quality gates"
---

# Tasks: Phased Delivery with Quality Gates

**Input**: Design documents from `/specs/002-phased-quality-gates/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md); product requirements from `/specs/001-agentic-url-shortener/spec.md` and root `plan.md`.

**Tests**: REQUIRED. The spec and constitution (Principles VI, VII) demand automated tests for every requirement; tests are written before the code they cover and tagged with the requirement ID.

**Organization**: Setup → Foundational (gate infrastructure) → six delivery phases P1–P6 (the plan's phases) → Polish. Each delivery phase is a thin slice that ends with the same gate block and a publish step. Gate work follows the plan: nothing is pushed until all gates pass.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]** (spec 002 stories): **US1** phased delivery / product code, **US2** traceability & tests, **US3** security gate, **US4** code review gate, **US5** AI evaluation, **US6** performance, **US7** simple frontend, **US8** GitHub publish
- Requirement tags: `001-FR-nnn` (feature 001), `002-FR-nnn` (this feature)
- **Naming**: "P1–P6" are the delivery phases (plan.md); "Phase N" headings below are task-list sections. `--phase N` on scripts always means delivery phase PN. Setup/Foundational tasks carry story labels deliberately, because they build gates for those stories.
- **ID order**: IDs T156+ were added after analysis and sit inside the phase where they belong; execute by phase and the dependencies noted in each task, not by ID.
- **Requirement ownership**: see plan.md "Requirement ownership" and `docs/phases.json`; an owned requirement without a passing tagged test blocks its phase.

## Path Conventions

- `{main}` = `backend/src/main/java/com/example/shortener`
- `{test}` = `backend/src/test/java/com/example/shortener`
- `{res}` = `backend/src/main/resources`; `{tres}` = `backend/src/test/resources`
- Frontend: `frontend/src/`, `frontend/tests/`; scripts: `scripts/`; evidence: `docs/`
- Commands: PowerShell scripts (`.ps1`) are authoritative on this Windows machine; no `.sh` mirrors unless time allows

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository, project skeletons, and evidence folders. No product behavior yet.

- [X] T001 Initialize git repository (`git init`, default branch `main`, working branch `phase-work`) and create `.gitignore` (target/, node_modules/, .env, *.log, docs/gates/tmp/) — do NOT add a remote or push yet
- [X] T002 Create Maven project `backend/pom.xml` (Java 21, Spring Boot 3.x; Web, Validation, Data JPA, Actuator, Flyway, PostgreSQL driver, springdoc-openapi, H2 test scope, spring-boot-starter-test; surefire configured for JUnit `@Tag` groups `security`, `perf`, `eval`; OWASP Dependency-Check plugin with `failBuildOnCVSS=7`) and `{main}/ShortenerApplication.java`
- [X] T003 [P] Scaffold frontend with Vite React + TypeScript in `frontend/` (package.json, tsconfig, vite.config.ts with `/api` proxy to :8080, Vitest + React Testing Library configured)
- [X] T004 [P] Create `docker-compose.yml` (PostgreSQL 16, named volume, healthcheck) and `.env.example` (DB URL/user/password placeholders, `AGENT_PROVIDER=mock`, optional `LLM_API_KEY=` blank)
- [X] T005 [P] Create evidence skeleton: `docs/phases.json` (P1–P6 per plan.md table with owned requirement IDs), `docs/review-checklist.md` (correctness, security, simplicity, test quality, readability, constitution compliance), `docs/perf/budgets.json` seeded with concrete values (redirect p95 < 1000 ms and 0 errors; concurrent run < 0.7 × sequential; full three-scenario demo < 120 s; first-use walkthrough per phase < 180 s; frontend production bundle < 500 KB gzip; page render in test < 1000 ms), `eval/criteria.json` (version `1.0`, pass threshold 100% for deterministic agents), `docs/phases.json` also records the 002 requirement ownership from plan.md, empty `docs/gates/`, `docs/reviews/`, `docs/evals/`, `docs/perf/`
- [X] T006 [P] Add `.gitleaks.toml` with allow-list only for files under `{tres}/security/fake-secrets/` (clearly marked fakes)
- [X] T007 [P] Create root `README.md` stub (prerequisites, start commands from quickstart.md, "demo mode needs no API key", phase status table)
- [X] T008 [P] [US2] Create requirement tagging helpers: `{test}/support/Requirement.java` (repeatable annotation `@Requirement("001-FR-024")`; exactly one ID per annotation — ranges like `004–009` used in task text are shorthand only, and the scanner in T009 rejects them in code) and `frontend/tests/support/req.ts` (convention `// @req 001-FR-016` in test files)

---

## Phase 2: Foundational (Blocking Prerequisites — gate infrastructure + shared backend plumbing)

**Purpose**: The reusable gates and plumbing every delivery phase depends on (spec FR-004–009, 014, 019–020).

**⚠️ CRITICAL**: No delivery phase may be published until this phase is complete.

- [X] T009 [P] [US2] Write `scripts/traceability.ps1`: scan `{test}` for `@Requirement` and `frontend/tests` for `@req`, read Surefire/Vitest reports, regenerate `docs/traceability.md` (requirementId, owning phase from `docs/phases.json`, tests, PASS/FAIL/MISSING)
- [X] T010 [P] [US2] Write `scripts/gate-tests.ps1 --phase N`: run `mvn test` and `npm test -- --run`, call traceability, FAIL if any test fails or any requirement owned by phases ≤ N is MISSING/FAIL; write TESTS result into `docs/gates/phase-N.json`; exit codes 0/1/2 per contracts/gates.md
- [X] T011 [P] [US3] Write `scripts/gate-security.ps1 --phase N`: run hostile-input test group (`mvn test -Dgroups=security`), Gitleaks (`NOT_RUN` exit 2 if binary missing), OWASP Dependency-Check (fail CVSS ≥ 7), `npm audit --audit-level=high`; write SECURITY result. Always run the self-test first (plant a fake key in a scratch file; the scan MUST flag it or the gate fails). Cache the NVD data under `.cache/dependency-check/` and read an optional `NVD_API_KEY` env var so repeat runs are fast; first-run download is documented in README
- [X] T012 [P] [US4] Write `scripts/gate-review.ps1 --phase N`: verify `docs/walkthroughs/phase-N.md` exists (timed first-use run < 180 s, accessibility check ticked), verify `docs/reviews/phase-N.md` exists, all checklist items answered, no open BLOCKER/HIGH without waiver (reason, approver, date); write REVIEW result
- [X] T013 [P] [US6] Write `scripts/gate-perf.ps1 --phase N`: run `mvn test -Dgroups=perf`, compare to `docs/perf/budgets.json`, write `docs/perf/phase-N.md` with environment (OS, CPU, JDK, Node) and PERF result
- [X] T014 [P] [US5] Write `scripts/gate-eval.ps1 --phase N`: run `mvn test -Dgroups=eval`, require score ≥ threshold, degraded set failing, repeat-run identical; write `docs/evals/phase-N.json` and EVAL result (exit 3 `SKIPPED`, non-blocking, for phases before P4)
- [X] T015 [US2] Write `scripts/gate-all.ps1 --phase N` that runs tests, security, review, perf, eval (P4+) and prints a PASS/FAIL/NOT_RUN table, exiting non-zero unless every applicable gate is PASS or SKIPPED; update the phase status in `docs/phases.json` (`GATES_RUNNING` → `COMPLETE` or `BLOCKED`)
- [X] T016 [US8] Write `scripts/publish-phase.ps1 N [-DryRun]`: run gate-all; if `origin`/owner-confirmed branch (`docs/publish-config.json`) is missing, commit and tag locally and record `PUBLISH_PENDING` (never push) — otherwise verify them; run Gitleaks on staged diff, commit `phase-N: <name>` (with repository attribution trailer), push branch and tag `phase-N`, append `docs/publish-log.md`; refuse with named reason otherwise
- [X] T017 [P] [US3] Create hostile-input corpus `{tres}/security/hostile-urls.txt` (javascript:, data:, file:, vbscript:, >2048-char URL, control chars, CRLF in alias, SQL/script payloads, unicode lookalike hosts, `//evil.com`) and a clearly marked fake secret file `{tres}/security/fake-secrets/fake-key.txt` for scanner self-test
- [X] T018 [P] [US1] Implement common error model `{main}/common/ApiError.java`, `{main}/common/GlobalExceptionHandler.java` (shape `{error:{code,message,details,requestId}}`)
- [X] T019 [P] [US1] Implement correlation ID filter `{main}/common/RequestIdFilter.java` (generate/propagate `X-Request-Id`, MDC for structured logs) and JSON-style log pattern in `{res}/logback-spring.xml`
- [X] T020 [P] [US1] Configure `{res}/application.yml` (DB from env, Flyway enabled, springdoc, actuator health/readiness), `{res}/application-test.yml` (H2), and empty baseline `{res}/db/migration/V0__baseline.sql`
- [ ] T021 [US2] Add gate self-tests `scripts/tests/gate-selftest.ps1`: planted fake key → security gate fails; removed tagged test → tests gate reports MISSING; tiny budget → perf gate fails; degraded eval set → eval gate fails (quickstart "Failure checks"); results consumed by T159
- [X] T022 [US8] CHECKPOINT (owner action): ask the owner for the GitHub repository URL (or permission to create one with `gh repo create`) and branch name; record in `docs/publish-config.json`; add `origin`

### Added by analysis: tests and templates that cover every 002 requirement

- [X] T158 [P] [US1] `{test}/delivery/PhasePlanTest.java` (`@Requirement("002-FR-001")`, `002-FR-002`, `002-FR-003`): `docs/phases.json` has ordered P1–P6, each with capabilities, owned requirements and exit criteria; P1 lists only shortener capabilities (no orchestrator/agent package referenced); every requirement of 001 and 002 has exactly one owner
- [X] T159 [P] [US2] `{test}/delivery/GateContractTest.java` (`002-FR-004`–`009`, `019`, `020`): validates gate output JSON against contracts/gates.md (all gate types, exit-code meanings, NOT_RUN blocks), reads `scripts/tests/gate-selftest.ps1` results, checks `publish-phase.ps1 -DryRun` refuses on a failing gate, produces `PUBLISH_PENDING` when no remote is confirmed, never includes secrets, and requires owner confirmation of repo/branch before a push
- [X] T160 [P] [US7] `frontend/tests/simplicity.test.ts` (`@req 002-FR-016`, `002-FR-018`): `package.json` dependencies stay within an allow-list (react, react-dom, react-router-dom and dev tooling; no UI/state libraries), every page route exists for the phase, and each page file stays under a size cap
- [X] T161 [P] [US4] Create `docs/walkthrough-template.md` (steps, stopwatch result, "completed without instructions? Y/N", accessibility checks: labels, keyboard path, focus, status text) used to write `docs/walkthroughs/phase-N.md` each phase (`002-FR-017`, SC-002, SC-009)
- [X] T162 [P] [US6] Add frontend perf check `frontend/tests/perf/render.test.tsx` and bundle-size check in `scripts/gate-perf.ps1` against `docs/perf/budgets.json` (`002-FR-014`; "dashboard loads quickly"); first written and run in P1 once T036 exists, then extended for every page added in later phases

**Checkpoint**: Gates run (even if mostly empty) and fail correctly; repository ready to publish.

---

## Phase 3: P1 — Shortener Core (US1 + US7) 🎯 MVP

**Goal**: Create a short link, redirect, reject bad input, simple UI, health and OpenAPI.

**Independent Test**: Shorten `https://example.com` in the UI, open the short link → redirected; `javascript:alert(1)` → clear error. Covers 001-FR-023, 024, 025 (redirect), 026 (list/get), 029.

### Tests for P1 (write first, confirm failing)

- [X] T023 [P] [US3] `{test}/shortener/UrlValidatorTest.java` — parameterized over hostile corpus, tag `@Tag("security")` and `@Requirement("001-FR-024")`: all rejected; valid http/https normalized
- [X] T024 [P] [US1] `{test}/shortener/ShortCodeGeneratorTest.java` — length/charset, uniqueness over N samples (`001-FR-023`)
- [X] T025 [P] [US1] `{test}/shortener/LinkControllerIT.java` — create → 201, invalid → 400 standard error, taken alias → 409, get/list paginated, redirect → 302, unknown → 404, `X-Request-Id` present (`001-FR-023,024,025,026,029`)
- [X] T026 [P] [US7] `frontend/tests/ShortenPage.test.tsx` — submit valid URL shows short link + copy button; invalid shows message; loading/empty/error states, labeled input, keyboard-submittable form, focus moves to error (`@req 001-FR-023`, `002-FR-016,017`)

### Implementation for P1

- [X] T027 [US1] Flyway `{res}/db/migration/V1__short_link.sql` (unique constraint on code, status, timestamps)
- [X] T028 [P] [US1] `{main}/shortener/ShortLink.java` entity and `{main}/shortener/ShortLinkRepository.java`
- [X] T029 [P] [US1] `{main}/shortener/UrlValidator.java` (scheme allow-list http/https, length bound, normalization, control-char rejection) and alias validator
- [X] T030 [P] [US1] `{main}/shortener/ShortCodeGenerator.java` (secure random base62, configurable length)
- [X] T031 [US1] `{main}/shortener/LinkService.java` (create with alias/unique code, get, list paged)
- [X] T032 [US1] `{main}/shortener/LinkController.java` (`POST/GET /api/links`, `GET /api/links/{code}`) with DTOs and validation in `{main}/shortener/dto/`
- [X] T033 [US1] `{main}/shortener/RedirectController.java` (`GET /{code}` → 302, 404 otherwise; no open redirect — only stored URLs)
- [X] T034 [P] [US1] OpenAPI info bean `{main}/common/OpenApiConfig.java`; verify `/actuator/health` readiness group in `{res}/application.yml`
- [X] T035 [P] [US7] Typed API client `frontend/src/api/links.ts` and shared fetch/error helper `frontend/src/api/http.ts`
- [X] T036 [US7] `frontend/src/pages/ShortenPage.tsx` (form, result with copy, links list, loading/empty/error states, labeled inputs, focus on error) and `frontend/src/App.tsx` routing, plain `frontend/src/styles.css`

### Gate block for P1

- [X] T037 [US6] Performance test `{test}/perf/RedirectBurstPerfIT.java` (`@Tag("perf")`): a few hundred concurrent redirects via Java `HttpClient`, assert p95 < 1 s and 0 errors; add budgets to `docs/perf/budgets.json`
- [X] T038 [US3] Add approval-bypass baseline test `{test}/security/NoAutoApplySecurityTest.java` (`@Tag("security")`): application exposes no endpoint or code path that executes generated code, writes to the repository, or deploys (`@Requirement("001-FR-014")`, `@Requirement("001-FR-011")`)
- [X] T039 [US2] Run `scripts/gate-tests.ps1 --phase 1` and fix until green; commit traceability output
- [ ] T040 [US3] Run `scripts/gate-security.ps1 --phase 1`; fix findings
- [X] T041 [US4] Perform P1 code review against `docs/review-checklist.md`; write `docs/reviews/phase-1.md` and a timed first-use walkthrough `docs/walkthroughs/phase-1.md` (T161 template); resolve blockers
- [X] T042 [US6] Run `scripts/gate-perf.ps1 --phase 1`
- [ ] T043 [US8] Run `scripts/gate-all.ps1 --phase 1` then `scripts/publish-phase.ps1 1` (requires T022 done)

**Checkpoint**: P1 pushed. Shortener works with no other phase present.

---

## Phase 4: P2 — Hardening & Analytics (US1 + US7)

**Goal**: Expiry, deactivation, click tracking, analytics, idempotency, collision retry, rate limiting.

**Independent Test**: Open a link twice → clicks = 2; repeat create with same `Idempotency-Key` → one link; exceed rate → 429. Covers 001-FR-025 (clicks), 026 (deactivate), 027, 028.

### Tests for P2

- [X] T044 [P] [US1] `{test}/shortener/ExpirationAndDeactivationTest.java` — expired → 410, deactivated → 404, not hard-deleted (`001-FR-026`)
- [X] T045 [P] [US1] `{test}/shortener/CollisionRetryTest.java` — bounded regeneration then clear failure (`001-FR-028`)
- [X] T046 [P] [US1] `{test}/shortener/IdempotencyTest.java` — same key+payload returns original; same key different payload → 409; no duplicate row (`001-FR-028`)
- [X] T047 [P] [US3] `{test}/common/RateLimitFilterTest.java` — create and analytics endpoints return 429 with error body past threshold (`001-FR-028`)
- [X] T048 [P] [US1] `{test}/analytics/AnalyticsServiceTest.java` + `{test}/analytics/ClickFlowIT.java` — create → redirect → click event → summary/per-link/top/recent paginated; user-agent/referrer sanitized and bounded; a click appears in analytics within 5 seconds of the redirect (001 SC-009) (`001-FR-025,027`)
- [X] T049 [P] [US7] `frontend/tests/ClickCounts.test.tsx` — click counts and analytics panel states, accessible names on controls (`@req 001-FR-027`, `002-FR-017`)

### Implementation for P2

- [X] T050 [US1] Flyway `{res}/db/migration/V2__click_idempotency.sql` (click_event, idempotency_record, link expiry index)
- [X] T051 [P] [US1] `{main}/analytics/ClickEvent.java`, `ClickEventRepository.java`, `{main}/analytics/ClickSanitizer.java` (truncate/strip UA & referrer)
- [X] T052 [P] [US1] `{main}/shortener/IdempotencyRecord.java`, `IdempotencyService.java` (request fingerprint)
- [X] T053 [P] [US3] `{main}/common/RateLimitFilter.java` (in-memory token bucket per client, configurable)
- [X] T054 [US1] Extend `{main}/shortener/LinkService.java` and `LinkController.java`: `expiresAt`, `DELETE /api/links/{code}`, `Idempotency-Key`, bounded collision retry
- [X] T055 [US1] Update `{main}/shortener/RedirectController.java` to record click and return 410 for expired
- [X] T056 [US1] `{main}/analytics/AnalyticsService.java` and `AnalyticsController.java` (`/api/analytics/summary`, `/api/analytics/links/{code}`)
- [X] T057 [US7] Extend `frontend/src/api/links.ts` and add `frontend/src/api/analytics.ts`; update `frontend/src/pages/ShortenPage.tsx` with click counts, deactivate button, expiry field and analytics panel

### Gate block for P2

- [X] T058 [P] [US3] Extend security suite `{test}/security/AbuseSecurityTest.java` (rate-limit bypass attempts, oversized headers, analytics data minimization)
- [X] T059 [P] [US6] Add analytics query perf test to `{test}/perf/RedirectBurstPerfIT.java` (redirect with click recording still within budget)
- [X] T060 [US2] Run `scripts/gate-tests.ps1 --phase 2`; verify P1 tests still pass
- [ ] T061 [US3] Run `scripts/gate-security.ps1 --phase 2`
- [X] T062 [US4] Review P2; write `docs/reviews/phase-2.md` and a timed first-use walkthrough `docs/walkthroughs/phase-2.md` (T161 template)
- [X] T063 [US6] Run `scripts/gate-perf.ps1 --phase 2`
- [X] T064 [US8] Run `scripts/gate-all.ps1 --phase 2` then `scripts/publish-phase.ps1 2`

**Checkpoint**: P2 pushed. Full shortener product complete without orchestration.

---

## Phase 5: P3 — Orchestration Core (US1 + US7)

**Goal**: Persisted workflow graph, validated state machine, dependency scheduler with parallelism and join, start/pause, restart recovery.

**Independent Test**: Launch a run with the 8-stage graph using timed no-op handlers; two independent stages run together and the join waits; pause stops new scheduling. Covers 001-FR-001–004, 006, 007.

### Tests for P3

- [X] T065 [P] [US1] `{test}/orchestrator/GraphValidatorTest.java` — cycle, missing dependency, duplicate ID rejected (`001-FR-002`)
- [X] T066 [P] [US1] `{test}/orchestrator/NodeStateMachineTest.java` — legal/illegal transitions, history stored (`001-FR-003`)
- [X] T067 [P] [US1] `{test}/orchestrator/SchedulerTest.java` — readiness only when all prerequisites succeeded; concurrency limit honored (`001-FR-004`)
- [X] T068 [US1] `{test}/orchestrator/RunExecutionIT.java` — parallel independent nodes, join waits, pause prevents new scheduling, restart recovery without duplicate execution (`001-FR-001,004,006,007`)
- [X] T069 [P] [US6] `{test}/perf/ParallelSpeedupPerfTest.java` (`@Tag("perf")`) — concurrent total < 0.7 × sequential for two delayed stages (`002-FR-015`)
- [X] T070 [P] [US7] `frontend/tests/RunsPage.test.tsx` — launcher, polling updates, distinct stage status labels as text (not color only) (`@req 001-FR-021`, `002-FR-016,017`)

### Implementation for P3

- [X] T071 [US1] Flyway `{res}/db/migration/V3__orchestration.sql` (agent_run, workflow_node_definition, run_node, node_transition)
- [X] T072 [P] [US1] Entities/repositories in `{main}/orchestrator/` (`AgentRun`, `RunNode`, `NodeTransition`, `NodeStatus` enum)
- [X] T073 [P] [US1] `{main}/orchestrator/WorkflowDefinition.java` + default 8-stage graph `{main}/orchestrator/DefaultWorkflow.java` (per root plan.md §6) and `GraphValidator.java`
- [X] T074 [US1] `{main}/orchestrator/NodeStateMachine.java` (transition table, history append)
- [X] T075 [US1] `{main}/orchestrator/StageHandler.java` interface and `{main}/orchestrator/NoOpStageHandler.java` (configurable delay, for tests)
- [X] T076 [US1] `{main}/orchestrator/Scheduler.java` (ready-set calc, bounded `ThreadPoolTaskExecutor`, persist state before/after execution, attempt IDs)
- [X] T077 [US1] `{main}/orchestrator/RunService.java` (create/start/pause/resume, startup recovery of interrupted RUNNING nodes) and `RunController.java` (`POST/GET /api/agent-runs`, `/start`, `/pause`)
- [X] T078 [US7] `frontend/src/api/runs.ts`, `frontend/src/hooks/usePolling.ts` (2 s), `frontend/src/pages/RunsPage.tsx` (requirement input, scenario select, start/pause, stage list grouped by dependency level with status labels)

### Gate block for P3

- [X] T079 [P] [US3] `{test}/security/RunSecurityTest.java` (`@Tag("security")`): oversized/malicious requirement text rejected or sanitized; no stage handler can execute shell/code
- [X] T080 [US2] Run `scripts/gate-tests.ps1 --phase 3`
- [ ] T081 [US3] Run `scripts/gate-security.ps1 --phase 3`
- [X] T082 [US4] Review P3; write `docs/reviews/phase-3.md` and a timed first-use walkthrough `docs/walkthroughs/phase-3.md` (T161 template)
- [X] T083 [US6] Run `scripts/gate-perf.ps1 --phase 3`
- [ ] T084 [US8] Run `scripts/gate-all.ps1 --phase 3` then `scripts/publish-phase.ps1 3`

**Checkpoint**: P3 pushed. Real graph execution, still with no-op stages.

---

## Phase 6: P4 — Agents, Artifacts, Audit, AI Evaluation (US1 + US5 + US7)

**Goal**: Agent contract and deterministic mock agents for all stages, versioned artifacts, append-only audit trail, and the evaluator with fixed eval sets.

**Independent Test**: Run Greenfield with mock agents; artifacts appear with versions and input references; `scripts/gate-eval.ps1` scores 100% on the three sets and fails the degraded set. Covers 001-FR-005, 008, 009, 010, 020 (audit); 002-FR-010–013.

### Tests for P4

- [X] T085 [P] [US1] `{test}/agents/AgentContractTest.java` — every mock agent returns all contract fields, labeled simulated, deterministic across two runs (`001-FR-008,009`)
- [X] T086 [P] [US1] `{test}/artifacts/ArtifactVersioningTest.java` — immutable versions, parent lineage, checksum, scoped context only (`001-FR-005`)
- [X] T087 [P] [US1] `{test}/audit/AuditTrailTest.java` — append-only (no update/delete API), correlation ID recorded (`001-FR-020`)
- [X] T088 [P] [US5] `{test}/evaluation/AgentOutputEvaluatorTest.java` (`@Tag("eval")`) — each of six criteria pass/fail with reason; below-threshold flagged in audit and not consumed (`002-FR-010,012`)
- [X] T089 [US5] `{test}/evaluation/ScenarioEvalSetTest.java` (`@Tag("eval")`) — runs `eval/greenfield.json`, `eval/brownfield.json`, `eval/ambiguous.json` ≥ threshold; `eval/degraded.json` fails; repeat run identical; criteria version recorded (`002-FR-011,013`)
- [X] T090 [P] [US7] `frontend/tests/ArtifactsPage.test.tsx` — version list, input references, "Proposal — not applied" label, keyboard-navigable version list (`@req 001-FR-021`, `002-FR-017`)

### Implementation for P4

- [X] T091 [US1] Flyway `{res}/db/migration/V4__agents_artifacts_audit.sql` (stage_attempt, artifact, audit_event)
- [X] T092 [P] [US1] `{main}/agents/AgentResult.java`, `AgentStatus.java`, `AgentProvider.java` interface (per root plan.md §10)
- [X] T093 [US1] `{main}/agents/mock/` deterministic agents: `RequirementsAnalystAgent`, `PlannerAgent`, `ArchitectAgent`, `ImplementationPlanAgent`, `ImplementerAgent` (proposal only), `TestAgent`, `DocumentationAgent`, `ReleaseReadinessAgent`, plus `MockAgentProvider`
- [X] T094 [P] [US1] `{main}/agents/AgentStageHandler.java` bridging orchestrator `StageHandler` to agents with scoped context; replace `NoOpStageHandler` in default wiring
- [X] T095 [P] [US1] `{main}/artifacts/Artifact.java`, `ArtifactRepository.java`, `ArtifactService.java` (versioning, lineage, checksum)
- [X] T096 [P] [US1] `{main}/audit/AuditEvent.java`, `AuditRepository.java`, `AuditService.java` (insert-only), and `GET /api/agent-runs/{id}/artifacts` and `/events` in `{main}/orchestrator/RunController.java`
- [X] T097 [US5] `{main}/evaluation/AgentOutputEvaluator.java`, `EvaluationCriteria.java` (versioned), `EvaluationReport.java`; hook: below-threshold output flagged in audit and the stage set to `BLOCKED` (held; not consumed downstream). Release of a held output by human approval is added in P5 (T118)
- [X] T098 [P] [US5] Create evaluation sets `eval/greenfield.json`, `eval/brownfield.json`, `eval/ambiguous.json` (ambiguous requires non-empty openQuestions+assumptions), `eval/degraded.json` (missing field, unsafe URL, missing simulated label)
- [X] T099 [P] [US1] Brownfield fixture `fixtures/brownfield/` (small URL-shortener module with a missing idempotency feature) and `fixtures/brownfield/README.md` explaining the seeded defect
- [X] T100 [US7] `frontend/src/pages/ArtifactsPage.tsx` and route `/runs/:id/artifacts` (version list, parent/input refs, proposal label)

### Gate block for P4

- [X] T101 [P] [US3] `{test}/security/ArtifactSecurityTest.java` (`@Tag("security")`): secret-like strings in agent output are blocked/redacted; unsafe URL schemes in generated artifacts flagged
- [X] T102 [US2] Run `scripts/gate-tests.ps1 --phase 4`
- [ ] T103 [US3] Run `scripts/gate-security.ps1 --phase 4`
- [X] T104 [US5] Run `scripts/gate-eval.ps1 --phase 4`; commit `docs/evals/phase-4.json`
- [X] T105 [US4] Review P4; write `docs/reviews/phase-4.md` and a timed first-use walkthrough `docs/walkthroughs/phase-4.md` (T161 template)
- [X] T106 [US6] Run `scripts/gate-perf.ps1 --phase 4` (full Greenfield run time within budget)
- [ ] T107 [US8] Run `scripts/gate-all.ps1 --phase 4` then `scripts/publish-phase.ps1 4`

**Checkpoint**: P4 pushed. Greenfield run produces real (simulated) artifacts, audited and evaluated.

---

## Phase 7: P5 — Governance & Resilience (US1 + US7)

**Goal**: Policy engine, approvals, bounded retry with fallback, safe-stop, rollback, re-plan with stale propagation.

**Independent Test**: Reach approval gate → reject (blocked) → approve; seeded transient failure retries then falls back; rollback keeps history; upstream change marks only dependents stale. Covers 001-FR-011–017, 019.

### Tests for P5

- [X] T108 [P] [US1] `{test}/governance/PolicyEngineTest.java` — secrets, unsafe schemes, destructive commands, prod deploy denied; failing tests block release without audited override (`001-FR-013,014`)
- [X] T109 [P] [US1] `{test}/governance/ApprovalServiceTest.java` — record fields complete, rejection blocks, agent cannot approve own work, duplicate/stale approvals refused (`001-FR-011,012`)
- [X] T110 [P] [US1] `{test}/orchestrator/RetryPolicyTest.java` — bounded attempts/backoff, transient only, validation/policy failures not retried, fallback labeled simulated (`001-FR-010,015`)
- [X] T111 [P] [US1] `{test}/artifacts/RollbackTest.java` — restore prior approved version, history/audit intact, unapproved version refused (`001-FR-016`)
- [X] T112 [P] [US1] `{test}/orchestrator/ReplanTest.java` — downstream closure stale, unaffected untouched, stale output blocked, plan version incremented, only affected re-run (`001-FR-017`)
- [X] T113 [US1] `{test}/governance/GovernanceFlowIT.java` — approval gate blocks downstream until approved; reject-then-approve path; pause/resume; manual retry (`001-FR-007,011,012`)
- [X] T114 [P] [US7] `frontend/tests/ApprovalsPage.test.tsx` — pending queue, approve/reject with comment, error states, labeled comment field, keyboard-operable buttons (`@req 001-FR-021`, `002-FR-017`)

### Implementation for P5

- [X] T115 [US1] Flyway `{res}/db/migration/V5__governance.sql` (approval, policy_decision, artifact status/plan_version columns)
- [X] T116 [P] [US1] `{main}/governance/PolicyEngine.java`, `PolicyDecision.java`, `PolicyRules.java` (the six minimum policies)
- [ ] T156 [US1] Actor identity model (do before T117, after T115): `{main}/governance/Actor.java` (type `HUMAN`/`AGENT`, id), demo-user resolution from the `X-Demo-User` header, default `demo-user`, always `HUMAN` (agent actors get 403) in `{main}/governance/ActorResolver.java`; agent actors can never be approvers; approval and audit records store the actor (`001-FR-012`; extend T109 to assert it)
- [X] T117 [P] [US1] `{main}/governance/Approval.java`, `ApprovalService.java`, `ApprovalController.java` (`POST /api/agent-runs/{id}/approvals`)
- [X] T118 [US1] Add `WAITING_FOR_APPROVAL`/`BLOCKED`/`RETRYING` handling to `{main}/orchestrator/Scheduler.java`; approval-gated stages (implementation apply, release readiness), and release of an evaluation-held (`BLOCKED`) output only through a recorded human approval
- [X] T119 [P] [US1] `{main}/orchestrator/RetryPolicy.java`, `TransientFailureException.java`, fallback to mock provider; seeded failure injector `{main}/orchestrator/FailureInjector.java` (config-driven for demo)
- [X] T120 [P] [US1] `{main}/artifacts/RollbackService.java` and `POST /api/agent-runs/{id}/rollback`
- [X] T121 [US1] `{main}/orchestrator/ReplanService.java` (downstream closure, stale marking, new plan version) and `POST /api/agent-runs/{id}/replan`, `POST .../nodes/{nodeId}/retry`
- [X] T122 [US7] `frontend/src/pages/ApprovalsPage.tsx`; add pause/resume/retry/re-plan controls and stale/blocked/waiting status styles to `frontend/src/pages/RunsPage.tsx`

### Gate block for P5

- [X] T123 [P] [US3] `{test}/security/GovernanceSecurityTest.java` (`@Tag("security")`): no bypass of approval via API ordering, replay, or forged approver; no repository write path exists even after approval unless demo switch set (`@Requirement("001-FR-014")`, `@Requirement("001-FR-011")`, `@Requirement("001-FR-012")`)
- [X] T124 [P] [US5] Extend `eval/` sets with governance cases (retry-then-fallback output labeled simulated; rejected proposal not consumed); re-run evaluator
- [X] T125 [US2] Run `scripts/gate-tests.ps1 --phase 5`
- [X] T126 [US3] Run `scripts/gate-security.ps1 --phase 5`
- [ ] T127 [US5] Run `scripts/gate-eval.ps1 --phase 5`
- [X] T128 [US4] Review P5; write `docs/reviews/phase-5.md` and a timed first-use walkthrough `docs/walkthroughs/phase-5.md` (T161 template)
- [X] T129 [US6] Run `scripts/gate-perf.ps1 --phase 5`
- [ ] T130 [US8] Run `scripts/gate-all.ps1 --phase 5` then `scripts/publish-phase.ps1 5`

**Checkpoint**: P5 pushed. All governance behaviors demonstrable.

---

## Phase 8: P6 — Scenarios, Metrics, Summary, Docs (US1 + US7)

**Goal**: Three repeatable presets, metrics, final engineering summary, complete documentation.

**Independent Test**: Run each scenario end to end with mock agents; metrics match audit events; summary exports. Covers 001-FR-018–022, 030.

### Tests for P6

- [ ] T131 [P] [US1] `{test}/scenarios/GreenfieldScenarioIT.java` — reaches release readiness through approvals; runs the scenario 3 times and asserts identical stages, outcomes and approval prompts (001 SC-002) (`001-FR-018`)
- [ ] T132 [P] [US1] `{test}/scenarios/BrownfieldScenarioIT.java` — impacted fixture files, data flow, idempotency proposal, compatibility risks (`001-FR-018`)
- [ ] T133 [P] [US1] `{test}/scenarios/AmbiguousScenarioIT.java` — clarification-needed pause, open questions/assumptions, resumes only after human decision (`001-FR-019`)
- [ ] T134 [P] [US1] `{test}/scenarios/ProviderUnavailableIT.java` — mock fallback completes a run (`001-FR-010`)
- [ ] T135 [P] [US1] `{test}/metrics/MetricsServiceTest.java` — success rate, retries, rollbacks, durations, stage latency, failed/blocked counts equal audit events (`001-FR-020`)
- [ ] T136 [P] [US1] `{test}/summary/EngineeringSummaryTest.java` — summary contains plan, rationale, artifacts, assumptions, risks, validation results, limitations (`001-FR-022`)
- [ ] T137 [P] [US7] `frontend/tests/MetricsPage.test.tsx` — metrics, scenario buttons, summary download, accessible table/headings (`@req 001-FR-021,022`, `002-FR-017`)

### Implementation for P6

- [ ] T164 [P] [US2] `{test}/delivery/DeliverablesTest.java` (`@Requirement("001-FR-030")`): `README.md` has prerequisites, copy-paste start commands and the no-API-key demo mode; `.env.example` and `docker-compose.yml` exist and `.env.example` contains no real secrets; `docs/architecture.md`, `docs/risks-and-tradeoffs.md`, `docs/demo-script.md`, `docs/api.md`, `docs/scenarios.md` exist and are non-empty; backend and frontend test directories contain tests; `docs/demo-script.md` covers all three scenarios, approval, retry, safe-stop, re-plan, rollback, metrics and link creation (write first; it fails until T143 is done)
- [ ] T138 [US1] `{main}/scenarios/ScenarioPreset.java`, `ScenarioService.java` with seeded requirements for Greenfield/Brownfield/Ambiguous and `POST /api/agent-runs` `scenario` parameter
- [ ] T139 [P] [US1] Brownfield analysis in `{main}/agents/mock/BrownfieldAnalyzer.java` grounded in `fixtures/brownfield/`; ambiguity handling (`NEEDS_CLARIFICATION`) in `RequirementsAnalystAgent`
- [ ] T140 [P] [US1] `{main}/metrics/MetricsService.java`, `MetricsController.java` (`/api/agent-runs/{id}/metrics`, `/api/metrics`) derived from audit/attempt data
- [ ] T141 [P] [US1] `{main}/summary/EngineeringSummaryService.java` + `GET /api/agent-runs/{id}/summary` (markdown export, stored as artifact)
- [ ] T157 [P] [US1] Optional provider (`001-FR-010`, constitution III): `{main}/agents/llm/LlmAgentProvider.java` configured only via env (`AGENT_PROVIDER`, `LLM_API_KEY`, `LLM_ENDPOINT`), validates responses against the agent contract and the evaluator, falls back to mock on error/invalid output (output labeled simulated); test `{test}/agents/LlmAgentProviderTest.java` with a stubbed endpoint covering valid, invalid, and unavailable cases; never logs the key
- [ ] T142 [US7] `frontend/src/pages/MetricsPage.tsx`, scenario launch buttons on `RunsPage.tsx`, summary download link, nav bar in `frontend/src/App.tsx`
- [ ] T143 [P] [US1] Documentation: `docs/architecture.md`, `docs/api.md` (generated OpenAPI export), `docs/scenarios.md`, `docs/risks-and-tradeoffs.md`, `docs/demo-script.md` (root plan.md §15) — must make T164 pass (`001-FR-030`), update `README.md` with copy-paste setup and phase status

### Gate block for P6

- [ ] T144 [P] [US6] `{test}/perf/ScenarioDemoPerfIT.java` (`@Tag("perf")`): all three scenarios finish < 2 min total (`002-FR-014`, SC-007)
- [ ] T145 [P] [US5] Final evaluation run over all three scenarios plus provider-unavailable case
- [ ] T146 [US2] Run `scripts/gate-tests.ps1 --phase 6`; confirm `docs/traceability.md` shows 100% of 001 and 002 requirements mapped and passing (SC-001)
- [ ] T147 [US3] Run `scripts/gate-security.ps1 --phase 6` (full)
- [ ] T148 [US5] Run `scripts/gate-eval.ps1 --phase 6`
- [ ] T149 [US4] Final review; write `docs/reviews/phase-6.md` and a timed first-use walkthrough `docs/walkthroughs/phase-6.md` (T161 template)
- [ ] T150 [US6] Run `scripts/gate-perf.ps1 --phase 6`
- [ ] T151 [US8] Run `scripts/gate-all.ps1 --phase 6` then `scripts/publish-phase.ps1 6`

**Checkpoint**: P6 pushed. Full system, evidence, and documentation published.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T152 [P] Run `quickstart.md` end to end on a clean clone and fix README gaps (SC-001 of 001: < 5 minutes to Greenfield; `001-FR-030`)
- [ ] T153 [P] Frontend accessibility pass (labels, focus order, keyboard use) across all pages; fix issues (`002-FR-017`)
- [ ] T154 [P] Run `scripts/tests/gate-selftest.ps1` and record results in `docs/gates/selftest.md`
- [ ] T163 [US7] Timed end-to-end rehearsal of `docs/demo-script.md` (001 SC-012: < 20 min) and a first-use run per frontend page without instructions (002 SC-009); record results in `docs/walkthroughs/final.md`
- [ ] T155 Write final engineering summary `docs/final-summary.md` (plan, rationale, artifacts, assumptions, risks, validation results, limitations, next steps) and tag `v1.0`

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2** strictly; **Phase 2 blocks everything** (gates + plumbing). T022 (owner supplies GitHub repo) must be done before the first `publish-phase` (T043); coding may continue without it.
- **Delivery phases are sequential**: P1 → P2 → P3 → P4 → P5 → P6 (each builds on the previous; P1–P2 are independent of orchestration).
- Within a phase: tests first (must fail) → migration → entities → services → controllers → frontend → gate block (tests → security → eval → review → perf → publish).
- Phase 9 after P6.

### Parallel opportunities
- Phase 1: T003–T008 together. Phase 2: T009–T014, T017–T020 together (distinct files).
- Within each delivery phase: all `[P]` test tasks together; entity/validator/generator tasks together; frontend client and tests parallel to backend once the contract (contracts/api-by-phase.md) is fixed.
- Gate block: security-test and perf-test authoring in parallel; gate runs themselves are sequential.

## Implementation Strategy

- **MVP**: Phases 1–3 (through P1). Publish P1 and demo the working shortener.
- **Incremental**: Add one phase at a time; never start phase N+1 until phase N's gates are green and published (or the owner explicitly defers publishing).
- **Time-box** (one-day build): if behind, thin P6 (skip optional graph visualization; keep presets, metrics, summary) rather than skipping gates.
- Do not mark a task done without its test passing; do not claim a requirement satisfied unless `docs/traceability.md` shows it PASS.
