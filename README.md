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
cd frontend; npx playwright install chromium   # once
cd frontend; npm run e2e                        # real-browser journeys (starts backend + frontend; ports 8080 and 5173 must be free)
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

## The seven phases
1. **Shortener core** - create and redirect short links with strict URL validation, plus a simple shorten page.
2. **Hardening and analytics** - link expiry, deactivation, click analytics, idempotent creation and rate limiting.
3. **Orchestration core** - a persisted workflow graph with parallel stages, joins, pause/resume and restart recovery.
4. **Agents, artifacts, audit, evaluation** - simulated agents for each stage, versioned artifacts, an audit trail and AI output evaluation.
5. **Governance and resilience** - approval gates, policy checks, retries with fallback, re-plan and rollback.
6. **Scenarios, metrics, summary** - Greenfield, Brownfield and Ambiguous demos, run metrics and the final engineering summary.
7. **Brief conformance** - explicit stage entry/exit gates, cooperative safe-stop, automatic re-plan when an upstream artifact changes, change control on approvals, text-driven requirement analysis, structural brownfield analysis and real-browser end-to-end tests.

Generated code and patches are proposals only; nothing is applied, executed or deployed automatically.
