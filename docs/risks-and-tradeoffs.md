# Risks, trade-offs and known limitations

This is an honest list of what the prototype does *not* do, why those choices were made, and which guardrail limits the damage.
The system is a demonstration of governed orchestration, not a production service. Section references point to
[architecture.md](architecture.md).

## 1. Risks and the guardrail for each

| # | Risk | Why it exists | Guardrail in this build | Residual exposure |
|---|------|---------------|-------------------------|-------------------|
| R1 | **Single-instance scheduler.** Run locks, the queued-node set and the concurrency semaphore live in one JVM's memory. | A distributed lock or job queue would add infrastructure that a one-day, single-process prototype does not need. | Every state change goes through one validated transition path (`RunTransitions`) inside a DB transaction, and `run_node (run_id, node_id)` is unique. Restart recovery pauses interrupted runs instead of re-running them. | Two backend instances against one database would both schedule the same run and could execute a stage twice. Run exactly one instance. |
| R2 | **Demo-header identity, not authentication.** The approver is whatever the caller puts in `X-Demo-User` (default `demo-user`). | Enterprise identity was out of scope; the header keeps the system runnable in one command while still recording who decided. | Agents (`agent:` prefix) and an artifact's own author cannot approve (403 `APPROVAL_FORBIDDEN`); decisions are immutable once recorded; every decision is audited with actor, comment, artifact version and policy reason. | Anyone who can reach the port can start runs, approve, roll back or re-plan, and can forge any human identity. Bind to localhost or put it behind real authentication before any shared use. |
| R3 | **Rule-based evaluation, not an LLM judge.** The six criteria check structure, labeling, determinism and unsafe patterns. | Rules are deterministic, free and repeatable, which suits a demo with no API key. | Output that fails any criterion is held (`BLOCKED`/`HELD`, never stored as `CURRENT`); the degraded fixtures prove each criterion can fail. | Rules cannot judge whether a design is *good*, only whether it is well-formed and safe-looking. A future real provider would need semantic review by a human. |
| R4 | **Mock agents.** All stage content is produced by deterministic templates, not reasoning. | Guarantees a reliable demo and testable contracts. | Every output starts with `[SIMULATED]` and `simulated=true`; confidence is a fixed heuristic and says so; the code diff has `applied=false`. | The content demonstrates workflow, not engineering quality. The mock `TestAgent` always reports 12 passed / 0 failed, which are estimates, not results. |
| R5 | **In-memory rate limiter.** Token buckets per client address are kept in a `ConcurrentHashMap`. | Simple local implementation was acceptable per the brief. | 30 link creations and 120 analytics reads per minute per peer address by default; `X-Forwarded-For` is not trusted; the map is cleared above 10,000 tracked clients so memory is bounded. | Limits reset on restart, are per instance, and apply to the TCP peer (all clients behind one proxy share a bucket). Only link creation and analytics are limited; run, approval and redirect endpoints are not. |
| R6 | **Spring Framework CVEs suppressed.** The dependency scan reports CVEs in Spring Framework 6.2.0 to 6.2.19, and 6.2.19 is the latest 6.2.x with no fixed release. | No patched release exists yet. | Owner-approved waiver recorded in `docs/security/residual-risk.md`; each affected feature (XSLT views, WebFlux, RSocket, SSE, SpEL on user input, bean property binding) was checked as unused; the suppression in `backend/dependency-check-suppressions.xml` **expires on 2026-12-31**, after which the security gate fails again. | Real exposure if the application starts using any listed feature. Action: move to the fixed 6.2.x release (or Spring Boot 4) as soon as it exists, rerun `scripts/gate-security.ps1` and delete the suppression. |
| R7 | **No real deployment, ever.** The system proposes patches and release checklists but never applies, executes or deploys. | Autonomous deployment is out of scope and unsafe for a prototype. | `IMPLEMENTATION.applied` must be `false` (evaluator), the policy engine denies `DEPLOY_PRODUCTION`, release readiness always needs a human, and no code path writes to a repository or runs a command. | Nothing validates that a proposed diff compiles or passes tests. "Release ready" means "a human may now decide". |
| R8 | **Rollback is artifact-level.** Rollback restores a prior artifact version as `CURRENT`. | No code is ever applied, so there is no code to roll back. | Nothing is deleted; the old version becomes `SUPERSEDED`; `ARTIFACT_ROLLBACK` is audited; `STALE` versions cannot be restored. | Rolling back an upstream artifact does not change node status or downstream artifacts; a human must re-plan to refresh them. |
| R9 | **Audit trail is append-only by convention.** | Entity columns are `updatable = false` and the API is read-only, but there is no database trigger or restricted DB role. | No update or delete code path exists in the application. | Anyone with direct database access can alter or delete events. A production deployment would use a write-only role or a WORM store. |
| R10 | **Cooperative safe-stop only.** Pause stops *new* scheduling; a stage already executing (including its retry loop) finishes. | Interrupting an arbitrary agent call safely is hard. | Paused runs keep `READY` nodes unstarted until resumed; restart recovery pauses the run and blocks interrupted stages. | A long stage cannot be aborted mid-flight; there is no cancel endpoint. |
| R11 | **Restart recovery is conservative.** | Re-running an interrupted stage could duplicate side effects. | Interrupted `RUNNING`/`RETRYING` nodes become `BLOCKED` (`INTERRUPTED`) and the run `PAUSED` until an operator starts it. | Needs a human after every crash; only runs marked `RUNNING` are examined. |
| R12 | **Unbounded growth.** | No retention jobs were built. | Click user agent and referrer are truncated and minimized; idempotency records carry a 24-hour `expires_at`. | No scheduled cleanup exists for click events, expired idempotency records, audit events or old artifact versions. |
| R13 | **AI reliability if a real provider is enabled (Phase 6 additions).** An optional LLM provider may return invalid, unsafe or unavailable output. | Real models are non-deterministic. | The same `AgentResult` contract, six-criterion evaluation, policy text checks, bounded retries and mock fallback apply; output stays a proposal pending approval; the key is read from the environment only and never committed (`.env.example` is blank). | Rule-based evaluation does not detect plausible but wrong content. Demo mode is unaffected and needs no key. |

## 2. Failure scenarios

| Failure | What happens | Where to see it |
|---------|--------------|-----------------|
| Agent throws a transient failure | Node goes `RUNNING -> RETRYING -> RUNNING` up to 3 attempts (50 ms base, doubling, capped at 500 ms by default), each retry audited as `STAGE_RETRY`. | Run detail `attempts`; audit trail. |
| Retries exhausted on a transient failure | The deterministic fallback runs once, output is status `FALLBACK` and `[SIMULATED]`, attempt row has `fallback_used = true`, audit has `FALLBACK_USED`. The stage can still succeed. | Audit trail; artifact summary and status. |
| Validation or policy failure (non-transient) | Never retried. A failed evaluation or policy `DENY` holds the output: node `BLOCKED` with error code `HELD`, no artifact stored. A non-transient exception makes the node `FAILED`, dependants `SKIPPED` and the run `FAILED`. | Node `errorSummary`; `OUTPUT_FLAGGED` / `POLICY_DENIED` events. |
| Fallback itself fails | Node `FAILED`, dependants `SKIPPED`. Operator can call the retry endpoint (`FAILED` or `BLOCKED` only). | Run detail; `NODE_FAILED`. |
| Human rejects an approval | Stage `BLOCKED` (`REJECTED`) with the feedback saved; downstream stays `PENDING`. A retry regenerates the stage as a new artifact version and opens a new approval. | Approvals, audit `APPROVAL_REJECTED`. |
| Clarification needed | Stage waits in `WAITING_FOR_APPROVAL` (kind `CLARIFICATION`); nothing downstream starts. | Approvals page. |
| Approval for a version that has since changed | 409 `STALE_APPROVAL`; reviewer must look again. | `POST .../approvals`. |
| Application restarts mid-run | Interrupted stages `BLOCKED` (`INTERRUPTED`), run `PAUSED`; `start` resumes them. | Run detail after restart. |
| Re-plan while a stage is running | Refused with 409 `REPLAN_REQUIRES_PAUSE`; pause first and let running stages finish. | `POST .../replan`. |
| Failing tests reported (policy path) | Release policy returns `DENY` (`failing-tests-block-release`) which is raised as an approval; approving it is an audited explicit override. Unit-tested only; mock tests never fail. | Policy unit tests. |
| Duplicate create request | With `Idempotency-Key`, the original link is replayed (200, `Idempotent-Replay: true`); a different payload under the same key returns 409 `IDEMPOTENCY_KEY_REUSED`. | `POST /api/links`. |
| Click recording fails | Logged and ignored; the redirect still succeeds. | Application log. |

## 3. Trade-offs made

| Choice | Benefit | Cost |
|--------|---------|------|
| Modular monolith with an in-process scheduler | One process, easy to run, test and explain. | No horizontal scale (R1). |
| Persisted state tables plus an append-only audit log, not event sourcing | Efficient reads and a clear audit story. | State and events can in principle disagree; they are written in the same transaction for node changes only. |
| Mock-first agents with an interface | Reliable demo, testable contract. | Content is canned (R4). |
| Evaluate then store | Unsafe or malformed output never becomes `CURRENT`. | The agent runs twice per stage (determinism check), which is cheap only because agents are deterministic and offline. |
| Comma-separated `depends_on`, `parent_artifact_ids` and `input_refs` columns | Simple schema, no join tables. | Not queryable relationally; fine at 8 nodes per run. |
| Flat approval flow (one pending approval per stage, one decision) | Easy to reason about and audit. | No multi-party or quorum approvals. |
| Polling UI | No websocket infrastructure. | Up to a polling interval of lag in the dashboard. |
| H2 file database for the `local` profile | Runs without Docker. | H2 in PostgreSQL mode can differ from PostgreSQL in edge cases; the Docker path is the reference. |

## 4. Known limitations

| Area | Limitation |
|------|-----------|
| Authentication | None. Header identity only (R2). No CORS configuration; the dev UI uses the Vite proxy. |
| Scheduler | Single instance only (R1); no stage cancellation endpoint; `CANCELLED` exists in the state machine but nothing sets it. |
| Agents | Deterministic mocks only in the default build; with the default configuration any provider value other than `mock` fails startup. `referencesToInputs` always names upstream stages as `STAGE:v1` even if a later version is used; the real lineage is `parent_artifact_ids`. |
| Brownfield | Grounded only in `fixtures/brownfield/`; the analyzer parses Java and SQL structure with regex (no full parser), ignores comments, and assumes one primary type per file. |
| Ambiguity | The mock analyst treats a run as ambiguous because the `AMBIGUOUS` scenario label says so, not by analysing the text. |
| Policy | Command, database and deployment rules exist in the engine and are unit tested, but no stage proposes those actions, so they never fire in a run. Secret detection is pattern based (GitHub tokens, cloud keys, private key blocks, credential assignments) and can miss other secret formats. |
| Rollback | Artifact-level; does not refresh downstream stages or change node state. |
| Re-plan | Needs the affected stages to be idle (not running, retrying or ready); plan history is a counter (`plan_version`) plus stale artifacts and audit events, not stored plan snapshots. |
| Data | No retention or cleanup jobs for clicks, idempotency records, audit events or artifacts (R12). Artifact content is limited to 100,000 characters; requirements to 4,000. |
| Audit | Append-only by convention only (R9). |
| Analytics | Click events are recorded synchronously in the redirect path (failures are swallowed); counts are not de-duplicated per visitor; no IP address is stored. |
| Dependencies | Spring Framework CVE suppression expires 2026-12-31 (R6). |
| Deployment | No application container image, no production configuration, no TLS or reverse proxy. |

## 5. Phase 6 additions (still being merged)

The following Phase 6 pieces add observability and reporting rather than new risk surface, but each has its own caveats:

* **Metrics** (`GET /api/metrics`, `GET /api/agent-runs/{id}/metrics`) are computed from the current database state. They cover
  success rate, retries, rollbacks, fallbacks, end-to-end duration, stage latency, failed and blocked counts and MTTR. Because
  mock stages take milliseconds, latencies are tiny unless failures are injected, and rollback and retry counts only reflect
  what was recorded in the audit and attempt tables. Treat MTTR as indicative on a demo database.
* **Engineering summary** (`GET /api/agent-runs/{id}/summary`) is generated from stored artifacts and events; it inherits the
  simulated nature of the mock content and states its limitations.
* **Scenario presets** (`GET /api/scenarios`) make runs repeatable; they do not change agent behavior.
* **Optional LLM provider** is the only change that can introduce real model output. See R13.
