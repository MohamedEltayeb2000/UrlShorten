# Demo script (about 15 minutes)

A timed walk through the whole system: the governed orchestration engine first, the URL shortener last. Every step gives the UI
path and the equivalent `curl` command with the result to expect. No AI API key is needed; all agents are deterministic mocks and
everything they produce is labeled `[SIMULATED]`.

| # | Step | Time |
|---|------|------|
| 1 | Start the app and check health | 0:00 |
| 2 | Greenfield run: concurrent branches and the join | 1:00 |
| 3 | Open an artifact: version, provenance, inputs | 3:00 |
| 4 | Seeded transient failure, bounded retry, fallback | 4:00 |
| 5 | Pause (safe-stop) and resume | 5:30 |
| 6 | Re-plan from a changed requirement, stale stages | 6:30 |
| 7 | Approval gate: one rejection, then approval | 7:45 |
| 8 | Rollback to a prior artifact version | 9:00 |
| 9 | Brownfield scenario | 10:00 |
| 10 | Ambiguous scenario and its human decision | 11:15 |
| 11 | Metrics (Phase 6 additions) | 12:30 |
| 12 | Short link, redirect, click analytics | 13:15 |

## Before you start

* Commands below use bash syntax with `curl`. In Windows PowerShell use Git Bash, or call `curl.exe` (not the `curl` alias) and
  put JSON bodies in files (`-d @body.json`).
* Set `BASE=http://localhost:8080`. Run ids are assigned by the database; on a fresh database the Greenfield run is `1`,
  Brownfield `2`, Ambiguous `3`. Use the `id` the API returns and `export ID=1`.
* **Phase 6 additions:** steps 2, 9 and 10 spell out the requirement text. With the Phase 6 scenario presets
  (`GET /api/scenarios`, or `POST /api/agent-runs` with only `{"scenario":"GREENFIELD"}`) you can launch the same runs without
  typing it. Step 11 (metrics and summary) also depends on Phase 6 endpoints.
* Mock stages finish in milliseconds. Concurrency is visible in the stage `startedAt` times and the `NODE_*` audit events, and
  the retry and pause steps use a slowed-down retry backoff so there is a window to act in.

---

## 1. Start the app and check health (0:00, 1 min)

Start the backend with the demo options. They seed transient failures for the `TESTING` stage (the first 3 attempts of each
run fail, see `FailureInjector`) and slow the retry backoff to 5 s then 10 s:

```powershell
# Without Docker (embedded H2 database)
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--shortener.orchestrator.inject-failures=TESTING:3 --shortener.orchestrator.retry.base-ms=5000 --shortener.orchestrator.retry.max-ms=10000"

# Or with PostgreSQL: docker compose up -d db   (from the repo root), then the same command without the profile option
```

In a second terminal start the UI: `cd frontend; npm install; npm run dev` and open http://localhost:5173.

```bash
curl -s $BASE/actuator/health
```

Expect `{"status":"UP", ...}` (HTTP 200). Optional: Swagger UI at http://localhost:8080/swagger-ui.html and the OpenAPI document at
`/v3/api-docs`. Say: demo mode, no API key, agents are deterministic mocks.

---

## 2. Greenfield: concurrent branches and the join (1:00, 2 min)

**UI:** Runs page, requirement
`Build a URL shortener API with link creation, redirects, expiration, and click analytics.`, scenario **Greenfield**, **Launch
run**, then **Start**.

```bash
curl -s -X POST $BASE/api/agent-runs -H 'Content-Type: application/json' \
  -d '{"requirement":"Build a URL shortener API with link creation, redirects, expiration, and click analytics.","scenario":"GREENFIELD"}'
export ID=1
curl -s -X POST $BASE/api/agent-runs/$ID/start
sleep 2
curl -s $BASE/api/agent-runs/$ID
curl -s "$BASE/api/agent-runs/$ID/events?size=100"
```

Expect, in the run detail:

* `REQUIREMENTS_ANALYSIS`, `TASK_DECOMPOSITION`, `ARCHITECTURE_DESIGN`, `IMPLEMENTATION_PLAN` and `DOCUMENTATION` are `SUCCEEDED`.
  `level` values 0, 1, 2, 2 and 3 show the graph layers.
* `ARCHITECTURE_DESIGN` and `IMPLEMENTATION_PLAN` (both level 2) have `startedAt` within milliseconds of each other: they ran
  **concurrently**. In the events, two `NODE_RUNNING` entries for them appear back to back.
* `IMPLEMENTATION` is the **join** (`dependsOn` both). It only left `PENDING` after both parents succeeded (look for its
  `NODE_READY` event after both `NODE_SUCCEEDED` events) and is now `WAITING_FOR_APPROVAL`.
* `TESTING` and `RELEASE_READINESS` are `PENDING`; the run `status` is `WAITING_FOR_APPROVAL`; `GET /api/approvals/pending`
  lists one `IMPLEMENTATION` approval with the reason `generated-output-is-proposal`.

Say: the graph is stored in the database and the scheduler reads it; a node runs only when every dependency has succeeded.

---

## 3. Open an artifact: version, provenance, inputs (3:00, 1 min)

**UI:** Approvals page, **View artifacts for run 1** (or open `/runs/1/artifacts`), choose the `CODE_PROPOSAL` artifact.

```bash
curl -s $BASE/api/agent-runs/$ID/artifacts
```

Expect for `IMPLEMENTATION` / `CODE_PROPOSAL`: `version` 1, `status` `CURRENT`, a SHA-256 `checksum`, `createdBy`
`ImplementerAgent`, `parentIds` listing the artifact ids of `ARCHITECTURE_DESIGN` and `IMPLEMENTATION_PLAN` (the exact inputs),
`inputRefs` `["ARCHITECTURE_DESIGN:v1","IMPLEMENTATION_PLAN:v1"]`, and `content` whose summary starts with `[SIMULATED]` and whose
`structuredOutput.applied` is `false`: a proposal, never applied.

---

## 4. Seeded transient failure, bounded retry, fallback (4:00, 1.5 min)

Approve the gate (this releases `TESTING`, which has 3 injected failures):

```bash
curl -s -X POST $BASE/api/agent-runs/$ID/approvals -H 'Content-Type: application/json' -H 'X-Demo-User: alice' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"APPROVE","comment":"proposal reviewed"}'
curl -s $BASE/api/agent-runs/$ID        # repeat a few times
```

**UI:** Approvals page, **Acting as** `alice`, **Approve**; then watch the run on the Runs page (it polls).

Expect: `TESTING` is `RETRYING` after attempt 1 (about 5 s wait), then attempt 2 fails (about 10 s wait), then attempt 3 fails and
retries are exhausted. The audit trail has two `STAGE_RETRY` events ("injected transient failure 1/3 in TESTING (waiting 5000
ms)"), then `FALLBACK_USED`, and `TESTING` finishes `SUCCEEDED` with `attempts` 3 and the node reason "completed with simulated
fallback". Its `TEST_REPORT` artifact is labeled `[SIMULATED]` with status `FALLBACK`.

Say: only transient failures are retried, attempts are capped at 3 with exponential backoff, validation and policy failures are
never retried, and the fallback output is clearly marked as simulated.

---

## 5. Pause (safe-stop) and resume (5:30, 1 min)

Do this *during* step 4's backoff windows (right after approving):

```bash
curl -s -X POST $BASE/api/agent-runs/$ID/pause
sleep 20
curl -s $BASE/api/agent-runs/$ID
curl -s -X POST $BASE/api/agent-runs/$ID/start
```

**UI:** Runs page, **Pause** while `TESTING` shows `RETRYING`; later **Start**.

Expect: pause returns `status` `PAUSED` and an audit event `RUN_PAUSED` ("no new stages will start"). The stage already in its
retry loop is allowed to finish (it ends `SUCCEEDED` via fallback) but `RELEASE_READINESS` stays `PENDING` while paused: nothing
new is scheduled. After **Start** (`RUN_STARTED`), `RELEASE_READINESS` runs, produces its checklist and the run returns to
`WAITING_FOR_APPROVAL` for the release approval. (If you missed the window the run is not `RUNNING`; pausing then returns 409
`RUN_NOT_PAUSABLE`, which is itself a useful thing to show.)

---

## 6. Re-plan from a changed requirement, stale downstream stages (6:30, 1.25 min)

The release approval is pending and nothing is running, so re-planning is allowed.

**UI:** Runs page, on `REQUIREMENTS_ANALYSIS` choose **Re-plan from this stage**, enter the changed text, **Re-plan**.

```bash
curl -s -X POST $BASE/api/agent-runs/$ID/replan -H 'Content-Type: application/json' \
  -d '{"nodeId":"REQUIREMENTS_ANALYSIS","requirement":"Build a URL shortener API with link creation, redirects, expiration, click analytics and link deactivation."}'
sleep 2
curl -s $BASE/api/agent-runs/$ID/artifacts
curl -s "$BASE/api/agent-runs/$ID/events?size=100"
```

Expect: `planVersion` 2; a `RUN_REPLANNED` event ("plan v2 from REQUIREMENTS_ANALYSIS; affected: [...]; stale artifacts: 8"); every
stage's previous artifact (v1) now has `status` `STALE` and is never read by later stages; the stages re-run and produce v2
artifacts (`CURRENT`); the old pending release approval is `SUPERSEDED`; the run stops again at the `IMPLEMENTATION` approval.
The old versions and all earlier events remain.

Say: re-planning from a later stage, for example `ARCHITECTURE_DESIGN`, would stale and re-run only that stage and its
downstream (five stages) and keep the requirement, task and implementation-plan results.

---

## 7. Approval gate: reject once, then approve (7:45, 1.25 min)

`IMPLEMENTATION` is waiting again (now `CODE_PROPOSAL` v2).

**UI:** Approvals page: press **Reject** with an empty comment (the form requires one), then enter a comment and **Reject**;
afterwards on the Runs page press **Retry** on `IMPLEMENTATION`, return to Approvals and **Approve**.

```bash
curl -s -X POST $BASE/api/agent-runs/$ID/approvals -H 'Content-Type: application/json' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"REJECT"}'                      # 400 COMMENT_REQUIRED
curl -s -X POST $BASE/api/agent-runs/$ID/approvals -H 'Content-Type: application/json' -H 'X-Demo-User: alice' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"REJECT","comment":"add rollback notes to the proposal"}'
curl -s -X POST $BASE/api/agent-runs/$ID/nodes/IMPLEMENTATION/retry
sleep 1
curl -s -X POST $BASE/api/agent-runs/$ID/approvals -H 'Content-Type: application/json' -H 'X-Demo-User: agent:bot' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"APPROVE"}'                     # 403 APPROVAL_FORBIDDEN
curl -s -X POST $BASE/api/agent-runs/$ID/approvals -H 'Content-Type: application/json' -H 'X-Demo-User: alice' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"APPROVE","comment":"approved after rework"}'
curl -s $BASE/api/agent-runs/$ID/approvals
```

Expect: the rejection without a comment is 400 `COMMENT_REQUIRED`. The rejection with a comment returns an approval with
`status` `REJECTED`; `IMPLEMENTATION` becomes `BLOCKED` (error `Rejected by alice: ...`). **Retry** regenerates it as
`CODE_PROPOSAL` v3 and opens a new approval. An agent identity is refused with 403. Alice's approval returns `APPROVED`;
`TESTING` now succeeds without failures (the seeded failures were already used for this run) and the run reaches
`RELEASE_READINESS`. Approve that too (`"nodeId":"RELEASE_READINESS"`) and the run ends `SUCCEEDED`. The approvals list shows the
history: a superseded approval, the rejection with its comment, and the approvals, each with approver and timestamp.

---

## 8. Rollback with audit history intact (9:00, 1 min)

**UI:** `/runs/1/artifacts`, select `CODE_PROPOSAL` v2 (status `SUPERSEDED`), **Restore this version**, **Confirm restore**.

```bash
curl -s -X POST $BASE/api/agent-runs/$ID/rollback -H 'Content-Type: application/json' \
  -d '{"nodeId":"IMPLEMENTATION","artifactType":"CODE_PROPOSAL","version":2}'
curl -s -X POST $BASE/api/agent-runs/$ID/rollback -H 'Content-Type: application/json' \
  -d '{"nodeId":"IMPLEMENTATION","artifactType":"CODE_PROPOSAL","version":1}'    # 409 ARTIFACT_STALE
curl -s $BASE/api/agent-runs/$ID/artifacts
curl -s "$BASE/api/agent-runs/$ID/events?size=200"
```

Expect: the first call returns v2 with `status` `CURRENT`; v3 is now `SUPERSEDED`; nothing was deleted (all three versions are still
listed). v1 is `STALE` (its inputs changed in the re-plan), so restoring it is refused with 409. The events keep everything
from the beginning of the run and add `ARTIFACT_ROLLBACK` ("CODE_PROPOSAL of IMPLEMENTATION restored to v2"). Say: rollback is
artifact-level; nothing downstream is changed and no code was ever applied.

---

## 9. Brownfield scenario (10:00, 1.25 min)

Requirement: `Add idempotent link creation and prevent duplicate links when clients retry requests.`

```bash
curl -s -X POST $BASE/api/agent-runs -H 'Content-Type: application/json' \
  -d '{"requirement":"Add idempotent link creation and prevent duplicate links when clients retry requests.","scenario":"BROWNFIELD"}'
curl -s -X POST $BASE/api/agent-runs/2/start
sleep 2
curl -s $BASE/api/agent-runs/2/artifacts
```

**UI:** Runs page, scenario **Brownfield**, **Launch run**, **Start**; open the `REQUIREMENT` artifact under `/runs/2/artifacts`.

Expect: `REQUIREMENT` content contains `impactedFiles` (the four files under `fixtures/brownfield/src`), a `dataFlow` line per
file, and `compatibilityRisks`: a retried `POST /links` creates a duplicate short link, the `Idempotency-Key` header must stay
optional, and a replay must keep the original status and body. The `CODE_PROPOSAL` is a text diff over `LinkController`,
`LinkService` and `schema.sql` with `applied` `false`, and it waits for approval. Say: the analysis is grounded in the seeded fixture
(read-only text files), not an arbitrary repository. You do not need to finish this run.

---

## 10. Ambiguous scenario and its human decision (11:15, 1.25 min)

Requirement: `Make URL links safer and improve analytics.`

```bash
curl -s -X POST $BASE/api/agent-runs -H 'Content-Type: application/json' \
  -d '{"requirement":"Make URL links safer and improve analytics.","scenario":"AMBIGUOUS"}'
curl -s -X POST $BASE/api/agent-runs/3/start
sleep 1
curl -s $BASE/api/approvals/pending
curl -s -X POST $BASE/api/agent-runs/3/approvals -H 'Content-Type: application/json' -H 'X-Demo-User: alice' \
  -d '{"nodeId":"REQUIREMENTS_ANALYSIS","decision":"APPROVE","comment":"proceed with the safe assumptions"}'
```

**UI:** Runs page, scenario **Ambiguous**, **Launch run**, **Start**; Approvals page shows the card.

Expect: `REQUIREMENTS_ANALYSIS` is `WAITING_FOR_APPROVAL` and the run `WAITING_FOR_APPROVAL`; nothing downstream has started. The
pending approval has kind `CLARIFICATION`, three `openQuestions` (who may create links, expiry, redirect volume), three safe
`assumptions`, and the stored analysis has risks tagged HIGH, MEDIUM and LOW and low confidence (0.45). After the human approval
the pipeline continues on the stated assumptions (confidence 0.7) up to the `IMPLEMENTATION` gate, and `RELEASE_READINESS` will
report `ready` false with the blocker "Open clarification questions have not been confirmed by a human".

---

## 11. Metrics (12:30, 45 s): Phase 6 additions

These endpoints are Phase 6 deliverables; confirm they are present in `/v3/api-docs` of the build you are running.

```bash
curl -s $BASE/api/metrics
curl -s $BASE/api/agent-runs/1/metrics
curl -s $BASE/api/agent-runs/1/summary
```

**UI:** the metrics view of the dashboard, when merged.

Expect: overall success rate and counts across the runs created in this demo (run 1 succeeded; runs 2 and 3 are still waiting for
a human). For run 1 the retry, fallback and rollback figures should agree with the audit trail you just saw (2 `STAGE_RETRY`
events, 1 `FALLBACK_USED`, 1 `ARTIFACT_ROLLBACK`), alongside end-to-end duration, per-stage latency, failed and blocked counts and
an MTTR. The summary is a markdown report of the plan, artifacts, assumptions, risks, validation results and limitations.
Cross-check any figure against `GET /api/agent-runs/1/events`; the exact response shape is defined by the OpenAPI document.

---

## 12. Short link, redirect and click analytics (13:15, 1.5 min)

**UI:** Shorten page (http://localhost:5173/): paste `https://www.example.com/some/long/path`, optional alias `demo`, **Shorten**,
copy the short link, open it in a new tab, return and see the **Clicks** column and analytics panel update.

```bash
curl -s -X POST $BASE/api/links -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"url":"https://www.example.com/some/long/path","alias":"demo"}'                       # 201
curl -s -i -X POST $BASE/api/links -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"url":"https://www.example.com/some/long/path","alias":"demo"}'                       # 200 + Idempotent-Replay: true
curl -s -X POST $BASE/api/links -H 'Content-Type: application/json' -d '{"url":"javascript:alert(1)"}'   # 400 INVALID_URL
curl -s -i $BASE/demo                                                                         # 302, Location: the stored URL
curl -s $BASE/api/analytics/links/demo
curl -s $BASE/api/analytics/summary
```

Expect: the first create returns 201 with `code` `demo` and `shortUrl` `http://localhost:8080/demo`; the repeat with the same
`Idempotency-Key` returns 200 with the same link and `Idempotent-Replay: true` (no duplicate); the `javascript:` URL is rejected
with 400 `INVALID_URL`; `GET /demo` answers `302` with `Location: https://www.example.com/some/long/path` and `Cache-Control:
no-store`; per-link analytics shows `totalClicks` 1 and a recent event holding only a sanitized user agent and (when sent) an origin-only referrer, with no IP address;
the summary shows totals, clicks over the last 30 days and top links.

---

## If something goes wrong

| Symptom | Fix |
|---------|-----|
| `409 RUN_NOT_PAUSABLE` in step 5 | The run was not `RUNNING` (you were too slow or too early). Create another run and approve the gate, then pause within 10 seconds. |
| Step 4 shows no retries | The backend was not started with the demo arguments from step 1; restart it with them. |
| `409 REPLAN_REQUIRES_PAUSE` | A stage is still running; pause, wait a second and re-plan. |
| Port 8080 or 5173 busy | Stop the other process, or run the backend with `--server.port=...` and update the Vite proxy. |
| Want a clean slate | Stop the app; delete `backend/h2data` (H2 profile) or run `docker compose down -v` (PostgreSQL). |
