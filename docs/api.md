# API reference

A concise reference for every HTTP endpoint. The authoritative, always-current schema is the generated OpenAPI document at
`GET /v3/api-docs` (JSON), browsable in Swagger UI at `/swagger-ui.html` (backend default `http://localhost:8080`).

## Conventions

* JSON in and out (`Content-Type: application/json`). Timestamps are ISO-8601 UTC instants. Null fields are omitted.
* Every response carries an `X-Request-Id` header. A client may send its own (`[A-Za-z0-9._-]`, up to 64 characters); it is
  echoed, put in the logs and stored as the correlation id of audit events.
* **Errors** all use one shape; `details` is a list (possibly empty) and `requestId` matches the header:

  ```json
  { "error": { "code": "LINK_NOT_FOUND", "message": "Link not found", "details": [], "requestId": "..." } }
  ```

* **Pagination**: list endpoints take `page` (0-based, default 0) and `size` and return
  `{ "items": [...], "page", "size", "totalItems", "totalPages" }`.
* **Identity**: run-control and approval endpoints read the acting user from the `X-Demo-User` header (letters, digits and
  `. _ : @ -`, up to 64 characters; default `demo-user`). An id beginning with `agent:` is treated as an agent. This is
  demo-grade identity, not authentication; there is no login.
* **Rate limits** (per client address, in memory): `POST /api/links` 30 per minute and `GET /api/analytics/**` 120 per minute by
  default; exceeding them returns 429 `RATE_LIMITED` with a `Retry-After` header.

### Errors common to every endpoint

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_ERROR` | Bean validation failed (`details` lists `field: message`). |
| 400 | `BAD_REQUEST` | Malformed JSON, wrong parameter type, missing required header. |
| 404 | `NOT_FOUND` | No such route or resource. |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method. |
| 429 | `RATE_LIMITED` | Rate limit exceeded (limited endpoints only). |
| 500 | `INTERNAL_ERROR` | Unexpected error; no internal detail is returned. |

---

## 1. Short links

### `POST /api/links`
Create a short link.

Request: `{ "url": "https://example.com/page", "alias": "my-alias", "expiresAt": "2027-01-01T00:00:00Z" }`
(`url` required, max 2048; `alias` optional, 3 to 32 of `[A-Za-z0-9_-]`; `expiresAt` optional, must be in the future and within
5 years). Optional header `Idempotency-Key` (1 to 128 characters, keys are remembered for 24 hours).

Response `201` with `Location: /api/links/{code}`, or `200` with `Idempotent-Replay: true` when the key matched an identical earlier
request:

```json
{ "code": "aB3xK9q", "shortUrl": "http://localhost:8080/aB3xK9q", "originalUrl": "https://example.com/page",
  "status": "ACTIVE", "createdAt": "...", "expiresAt": null, "clicks": 0 }
```

Errors: 400 `INVALID_URL`, `INVALID_ALIAS`, `INVALID_EXPIRY`, `INVALID_IDEMPOTENCY_KEY`; 409 `ALIAS_TAKEN`,
`IDEMPOTENCY_KEY_REUSED` (same key, different payload); 503 `CODE_GENERATION_FAILED` (bounded collision retries exhausted); 429.

### `GET /api/links`
Paginated list (`page`, `size`) of link objects as above.

### `GET /api/links/{code}`
Metadata for one link. Errors: 404 `LINK_NOT_FOUND`.

### `DELETE /api/links/{code}`
Deactivate a link (it is not hard-deleted). `204` with no body. Errors: 404 `LINK_NOT_FOUND`.

### `GET /{code}`
Redirect. `302` with `Location` set to the stored, validated URL and `Cache-Control: no-store`; a sanitized click event is
recorded (a recording failure never breaks the redirect). Errors: 404 `LINK_NOT_FOUND` (unknown or inactive), 410 `LINK_EXPIRED`.

---

## 2. Analytics

### `GET /api/analytics/summary`
Query: `page`, `size` (default 10) for the recent-events page. Response:

```json
{ "totalLinks": 3, "totalClicks": 12,
  "clicksOverTime": [ { "date": "2026-11-24", "clicks": 12 } ],
  "topLinks": [ { "code": "aB3xK9q", "clicks": 9 } ],
  "recentEvents": { "items": [ ... ], "page": 0, "size": 10, "totalItems": 12, "totalPages": 2 } }
```

`clicksOverTime` covers the last 30 days; `topLinks` returns up to 5. Rate limited (429).

### `GET /api/analytics/links/{code}`
Same idea for one link: `{ "code", "totalClicks", "clicksOverTime", "recentEvents" }`. Errors: 404 `LINK_NOT_FOUND`. Rate limited.

---

## 3. Agent runs

### `POST /api/agent-runs`
Create a run (all stages `PENDING`, status `CREATED`).

Request: `{ "requirement": "Build a URL shortener API ...", "scenario": "GREENFIELD" }`. `requirement` is required (max 4000
characters, no control characters except newline, tab and CR); `scenario` is optional and one of `GREENFIELD`, `BROWNFIELD`,
`AMBIGUOUS` (case-insensitive).

Response `201` with `Location: /api/agent-runs/{id}` and a **run detail**:

```json
{ "id": 1, "scenario": "GREENFIELD", "requirement": "...", "status": "CREATED", "createdAt": "...",
  "planVersion": 1, "startedAt": null, "completedAt": null,
  "nodes": [ { "id": "REQUIREMENTS_ANALYSIS", "name": "Requirements analysis", "status": "PENDING",
               "dependsOn": [], "level": 0, "attempts": 0, "startedAt": null, "completedAt": null, "errorSummary": null } ] }
```

Run `status`: `CREATED, RUNNING, PAUSED, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED`. Node `status`: `PENDING, READY, RUNNING,
SUCCEEDED, FAILED, BLOCKED, WAITING_FOR_APPROVAL, RETRYING, CANCELLED, SKIPPED`. Errors: 400 `INVALID_REQUIREMENT`,
`INVALID_SCENARIO`.

### `GET /api/agent-runs`
Paginated list, newest first; items are `{ id, scenario, requirement, status, createdAt }`. `size` is capped at 100.

### `GET /api/agent-runs/{id}`
Run detail (shape above). Errors: 404 `RUN_NOT_FOUND`.

### `POST /api/agent-runs/{id}/start`
Start a `CREATED` run or resume a `PAUSED` one (interrupted stages become `READY` again). Idempotent while `RUNNING`.
Returns the run detail. Errors: 404 `RUN_NOT_FOUND`; 409 `RUN_NOT_STARTABLE` (run is `SUCCEEDED`, `FAILED` or
`WAITING_FOR_APPROVAL`).

### `POST /api/agent-runs/{id}/pause`
Safe-stop: no new stage starts; running stages finish. Returns the run detail; no-op if already `PAUSED`. Errors: 404
`RUN_NOT_FOUND`; 409 `RUN_NOT_PAUSABLE` (run is not `RUNNING`).

### `POST /api/agent-runs/{id}/nodes/{nodeId}/retry`
Manually re-queue a `FAILED` or `BLOCKED` stage (and stages skipped because of it); the stage is regenerated as a new artifact
version. Header `X-Demo-User` is recorded in the audit trail. Returns the run detail. Errors: 404 `RUN_NOT_FOUND`,
`NODE_NOT_FOUND`; 409 `NODE_NOT_RETRYABLE`.

### `POST /api/agent-runs/{id}/replan`
Re-plan from a changed input: the stage and everything downstream are marked stale and re-queued, `planVersion` is
incremented, other stages keep their results.

Request: `{ "nodeId": "TASK_DECOMPOSITION", "requirement": "optional changed requirement text" }`. Returns the run detail.
Errors: 404 `RUN_NOT_FOUND`, `NODE_NOT_FOUND`; 400 `INVALID_REQUIREMENT`; 409 `REPLAN_REQUIRES_PAUSE` (an affected stage is
`RUNNING`, `RETRYING` or `READY`).

### `POST /api/agent-runs/{id}/rollback`
Restore a prior artifact version as `CURRENT`; the previously current version becomes `SUPERSEDED`; nothing is deleted.

Request: `{ "nodeId": "IMPLEMENTATION", "artifactType": "CODE_PROPOSAL", "version": 2 }`. Response: the restored **artifact**
(shape in section 4). Errors: 404 `RUN_NOT_FOUND`, `ARTIFACT_VERSION_NOT_FOUND`; 409 `ARTIFACT_STALE`.

---

## 4. Artifacts

### `GET /api/agent-runs/{id}/artifacts`
All artifact versions of a run, oldest first (an unknown run id yields an empty list):

```json
[ { "id": 12, "nodeId": "IMPLEMENTATION", "type": "CODE_PROPOSAL", "version": 1, "status": "CURRENT",
    "checksum": "sha256 hex", "parentIds": [9, 10], "inputRefs": ["ARCHITECTURE_DESIGN:v1", "IMPLEMENTATION_PLAN:v1"],
    "createdBy": "ImplementerAgent", "createdAt": "...", "content": "{ ...serialized agent result... }" } ]
```

`status` is `CURRENT`, `SUPERSEDED` or `STALE`. `content` is a JSON string of the agent result (`summary`, `structuredOutput`,
`assumptions`, `risks`, `openQuestions`, `confidence`, `simulated`, ...). Types: `REQUIREMENT, TASK_PLAN, DESIGN,
IMPLEMENTATION_PLAN, CODE_PROPOSAL, TEST_REPORT, DOCUMENTATION, RELEASE_CHECKLIST`.

---

## 5. Audit events

### `GET /api/agent-runs/{id}/events`
Paginated (`page`, `size` default 20, capped at 100), oldest first:

```json
{ "items": [ { "id": 1, "actorType": "HUMAN", "actorId": "demo-user", "eventType": "RUN_CREATED",
               "occurredAt": "...", "details": "workflow greenfield-sdlc, scenario GREENFIELD", "correlationId": "..." } ],
  "page": 0, "size": 20, "totalItems": 40, "totalPages": 2 }
```

`actorType` is `HUMAN`, `AGENT` or `SYSTEM`. Common `eventType` values: `RUN_CREATED`, `RUN_STARTED`, `RUN_PAUSED`,
`RUN_WAITING_FOR_APPROVAL`, `RUN_SUCCEEDED`, `RUN_FAILED`, `RUN_REPLANNED`, `NODE_<STATUS>`, `STAGE_RETRY`, `FALLBACK_USED`,
`NODE_RETRY_REQUESTED`, `ARTIFACT_CREATED`, `ARTIFACT_ROLLBACK`, `OUTPUT_FLAGGED`, `POLICY_*`, `CLARIFICATION_NEEDED`,
`APPROVAL_REQUESTED`, `APPROVAL_APPROVED`, `APPROVAL_REJECTED`, `RELEASE_OVERRIDE_APPROVED`.

---

## 6. Approvals

### `GET /api/approvals/pending`
Approvals waiting for a human across all runs, oldest first:

```json
[ { "approvalId": 4, "runId": 1, "nodeId": "IMPLEMENTATION", "nodeName": "Implementation", "kind": "HIGH_IMPACT_ACTION",
    "reason": "generated-output-is-proposal: Generated output is a proposal; applying it requires human approval",
    "artifactType": "CODE_PROPOSAL", "artifactVersion": 1, "summary": "[SIMULATED] ...",
    "openQuestions": [], "assumptions": ["..."], "policyReason": "...", "createdAt": "..." } ]
```

`kind` is `CLARIFICATION`, `HIGH_IMPACT_ACTION` or `HELD_OUTPUT`.

### `GET /api/agent-runs/{id}/approvals`
Every approval of the run (pending and decided):
`[ { "id", "nodeId", "status", "actorId", "comment", "policyReason", "artifactVersion", "createdAt", "decidedAt" } ]` where
`status` is `PENDING`, `APPROVED`, `REJECTED` or `SUPERSEDED` (a re-plan replaced it).

### `POST /api/agent-runs/{id}/approvals`
Record a decision on the stage waiting for approval.

Request: `{ "nodeId": "IMPLEMENTATION", "decision": "APPROVE", "comment": "reviewed", "artifactVersion": 1 }`.
`decision` is `APPROVE` or `REJECT` (case-insensitive); `comment` is **required for REJECT**; `artifactVersion` is optional
and, when given, must be the current version. Header `X-Demo-User` identifies the approver.

Response: the approval view (same shape as the list above, with `status` `APPROVED` or `REJECTED`). Approve moves the stage to
`SUCCEEDED` and continues the run; reject moves it to `BLOCKED` with the feedback recorded.

Errors: 400 `INVALID_APPROVAL`, `COMMENT_REQUIRED`, `INVALID_ACTOR`; 403 `APPROVAL_FORBIDDEN` (the actor is an agent, or the
author of the artifact); 409 `NO_PENDING_APPROVAL`, `STALE_APPROVAL`, `NODE_NOT_WAITING`.

---

## 7. Platform

| Method and path | Purpose |
|-----------------|---------|
| `GET /actuator/health` | Health with liveness and readiness probes (`/actuator/health/liveness`, `/actuator/health/readiness`); details hidden. |
| `GET /actuator/info` | Application info. |
| `GET /v3/api-docs` | OpenAPI 3 document. |
| `GET /swagger-ui.html` | Swagger UI. |

---

## 8. Phase 6 additions

These endpoints are Phase 6 deliverables being merged alongside this documentation. Check `/v3/api-docs` for the exact
response schemas.

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/metrics` | Overall metrics across runs: success rate, retries, rollbacks, fallbacks, end-to-end duration, stage latency, failed and blocked counts, MTTR. |
| `GET /api/agent-runs/{id}/metrics` | The same metrics for one run. |
| `GET /api/agent-runs/{id}/summary` | Markdown engineering summary of a run (plan, artifacts, assumptions, risks, validation, limitations). |
| `GET /api/scenarios` | The Greenfield, Brownfield and Ambiguous presets. |
| `POST /api/agent-runs` with only `{ "scenario": "BROWNFIELD" }` | Create a run from a preset; the preset supplies the requirement text. |
