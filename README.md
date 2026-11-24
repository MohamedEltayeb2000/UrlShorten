# URL Shortener + Agentic Software Engineering System

A URL shortener (the sample product) and, in later phases, a governed agent workflow engine that takes a
requirement through analysis, design, implementation proposal, testing, documentation and release readiness.
Delivered phase by phase; every phase must pass tests, security, code review, performance (and, from P4,
AI-evaluation) gates before it is published. See `specs/002-phased-quality-gates/` for the plan.

**Demo mode needs no AI API key.** Agents are deterministic mocks by default.

## Prerequisites
- JDK 21+ (built and tested on JDK 25), Maven 3.9+
- Node 20+ (tested on Node 24), npm
- Docker (PostgreSQL) — optional, see "Run without Docker"
- PowerShell (Windows PowerShell 5.1 or `pwsh`) for the gate scripts

## Run (with PostgreSQL)
```powershell
docker compose up -d db
cd backend; mvn spring-boot:run          # http://localhost:8080  (Swagger UI: /swagger-ui.html)
cd frontend; npm install; npm run dev    # http://localhost:5173
```

## Run without Docker (embedded H2, file-based)
```powershell
cd backend; mvn spring-boot:run "-Dspring-boot.run.profiles=local"
cd frontend; npm install; npm run dev
```

## Test
```powershell
cd backend;  mvn test
cd frontend; npm test -- --run
```

## Phase gates
```powershell
scripts/gate-all.ps1 -Phase 1                 # tests, security, review, performance, evaluation
scripts/publish-phase.ps1 -Phase 1 -DryRun    # verify gates, preview publish
scripts/publish-phase.ps1 -Phase 1            # commit + tag; pushes only after the owner confirms repo + branch
```
Evidence lives in `docs/`: `traceability.md`, `gates/`, `reviews/`, `walkthroughs/`, `perf/`, `evals/`, `publish-log.md`.

The first security run downloads the NVD vulnerability database and can take hours without an API key:
set `NVD_API_KEY` (free from nvd.nist.gov) to speed it up. Gitleaks is downloaded into `.tools/` (git-ignored).

## Phase status
| Phase | Scope | Status |
|-------|-------|--------|
| P1 | Shortener core: create, redirect, validation, simple UI | in progress |
| P2 | Expiry, analytics, idempotency, rate limiting | planned |
| P3 | Orchestration core (graph, scheduler, parallel + join) | planned |
| P4 | Agents, artifacts, audit, AI evaluation | planned |
| P5 | Policy, approvals, retry/fallback, rollback, re-plan | planned |
| P6 | Scenarios, metrics, summary, docs | planned |

Generated code and patches are proposals only; nothing is applied, executed or deployed automatically.
