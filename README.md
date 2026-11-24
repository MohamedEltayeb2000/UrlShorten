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
Updated 2026-11-24. Gate columns: Tests / Security / Review / Performance / Eval. `PASS`, `WAIVED` (owner-approved, recorded in
`docs/reviews/phase-N.md`), `-` (not applicable or not run yet).

| Phase | Scope | Status | Gates (T / S / R / P / E) | Tests |
|-------|-------|--------|---------------------------|-------|
| P1 | Shortener core: create, redirect, validation, simple UI | Published (tag `phase-1`) | PASS / WAIVED / PASS / PASS / - | 48 backend, 9 frontend |
| P2 | Expiry, deactivation, click analytics, idempotency, rate limiting | Published (tag `phase-2`) | PASS / PASS (Spring Framework CVEs suppressed, owner waiver) / PASS / PASS / - | 79 backend, 14 frontend |
| P3 | Orchestration core (graph, scheduler, parallel + join) | Published (tag `phase-3`) | PASS / PASS / PASS / PASS / - | 129 backend, 26 frontend |
| P4 | Agents, artifacts, audit, AI evaluation | Built and tested; publishing | pending | 179 backend, 35 frontend |
| P5 | Policy, approvals, retry/fallback, rollback, re-plan | Planned | - | - |
| P6 | Scenarios, metrics, summary, docs | Planned | - | - |

Security note: the first full dependency scan (OWASP/NVD) finished after P1 was published and found high-severity CVEs in the
original dependency versions. Spring Boot, Tomcat, the PostgreSQL driver, log4j and Swagger UI were upgraded in P2 and the scan is
clean for them. Spring Framework 6.2.19 still has CVEs with no fixed release; they affect features this app does not use and are
suppressed until 2026-12-31 under an owner-approved waiver (`docs/security/residual-risk.md`).

Generated code and patches are proposals only; nothing is applied, executed or deployed automatically.
