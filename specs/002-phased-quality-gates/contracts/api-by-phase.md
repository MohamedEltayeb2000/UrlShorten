# Contract: HTTP Endpoints Introduced per Phase

Authoritative detail lives in OpenAPI (generated); this table fixes *when* each capability appears.
All JSON errors use one shape: `{ "error": { "code", "message", "details?", "requestId" } }`.
Every response carries `X-Request-Id`. List endpoints accept `page` and `size` (size capped).

| Phase | Method & Path | Purpose |
|-------|---------------|---------|
| P1 | `POST /api/links` | Create link (`url`, optional `alias`) → 201 with code/short URL; 400 on invalid URL/alias; 409 alias taken |
| P1 | `GET /api/links`, `GET /api/links/{code}` | Paginated list, metadata |
| P1 | `GET /{code}` | 302 redirect; 404 unknown/inactive; 410 expired (P2) |
| P1 | `GET /actuator/health` | Health/readiness |
| P2 | `POST /api/links` (extended) | Optional `expiresAt`; header `Idempotency-Key`; 429 when rate limited |
| P2 | `DELETE /api/links/{code}` | Deactivate (no hard delete) |
| P2 | `GET /api/analytics/summary`, `GET /api/analytics/links/{code}` | Totals, clicks over time, top links, recent events (paginated) |
| P3 | `POST /api/agent-runs` | Create run (`requirement`, optional `scenario`) |
| P3 | `GET /api/agent-runs`, `GET /api/agent-runs/{id}` | List; detail with graph + node states |
| P3 | `POST /api/agent-runs/{id}/start`, `/pause` | Start/resume; safe-stop |
| P4 | `GET /api/agent-runs/{id}/artifacts`, `/events` | Versioned artifacts; audit events |
| P5 | `POST /api/agent-runs/{id}/approvals` | Approve/reject (`artifactVersion`, `decision`, `comment`); actor from `X-Demo-User` header (default `demo-user`, always `HUMAN`); an agent actor gets 403 |
| P5 | `POST /api/agent-runs/{id}/nodes/{nodeId}/retry` | Manual retry of eligible node |
| P5 | `POST /api/agent-runs/{id}/replan`, `/rollback` | Re-plan from changed input; restore prior version |
| P6 | `GET /api/agent-runs/{id}/metrics`, `GET /api/metrics` | Per-run and overall metrics |
| P6 | `GET /api/agent-runs/{id}/summary` | Final engineering summary |

## Frontend pages (simple, one added per phase)
| Phase | Page | Primary task |
|-------|------|--------------|
| P1 | `/` Shorten | Enter URL → see short link + copy; list links |
| P2 | `/` (extended) | Click counts; analytics panel |
| P3 | `/runs` | Launch scenario/requirement; see stage statuses (polling) |
| P4 | `/runs/:id/artifacts` | View artifact versions and inputs |
| P5 | `/approvals` | Approve/reject with comment; safe-stop, retry, re-plan controls |
| P6 | `/metrics` | Metrics + scenario launch buttons |

Every page shows loading, empty, error states; generated code is labeled "Proposal — not applied".
