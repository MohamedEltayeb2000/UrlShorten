# Quickstart: Validate a Phase

Run-guide for proving a phase is complete. Contracts: [gates.md](contracts/gates.md),
[api-by-phase.md](contracts/api-by-phase.md). Data shapes: [data-model.md](data-model.md).

## Prerequisites
- JDK 21, Maven, Node 20, Docker (PostgreSQL), Git
- Gitleaks installed (otherwise the security gate reports `NOT_RUN` and blocks)
- No AI API key required

## 1. Start the system
```
docker compose up -d db
cd backend && mvn spring-boot:run        # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```
Expected: `/actuator/health` returns UP; the frontend loads the current phase's page.

## 2. Exercise the phase (smoke)
- **P1**: shorten `https://example.com` in the UI → short link appears; open it → redirected. Try `javascript:alert(1)` → clear error.
- **P2**: open the short link twice → click count 2; create with the same `Idempotency-Key` twice → one link.
- **P3**: launch Greenfield → stages run, two independent stages show `RUNNING` together; join stage waits.
- **P4**: open artifacts → versions with input references; evaluation report exists.
- **P5**: reach an approval → reject (blocked, comment shown) → approve; rollback an artifact; pause/resume.
- **P6**: run all three scenarios; metrics page matches events; summary downloadable.

## 3. Run the gates
```
scripts/gate-all --phase N
```
Expected: exit 0; `docs/gates/phase-N.json` shows every applicable gate `PASS`;
`docs/traceability.md` lists every requirement owned by phases ≤ N with a passing test.

## 4. Review
Complete `docs/reviews/phase-N.md` from `docs/review-checklist.md`; resolve or waive blockers.

## 5. Publish (after owner confirms repo + branch)
```
scripts/publish-phase N
```
Expected: commit `phase-N: …` and tag `phase-N` visible on GitHub with evidence files; no secrets.
If no remote/branch is confirmed yet, the script commits and tags locally and records `PUBLISH_PENDING`
(nothing is pushed); re-run after confirming the remote. Use `-DryRun` to preview.

Also complete `docs/walkthroughs/phase-N.md` (timed first-use run < 3 minutes, accessibility checks).

## Failure checks (prove the gates bite)
- Plant a fake key in a scratch file → security gate fails.
- Remove a tagged test → tests gate reports the requirement `MISSING`.
- Feed the degraded eval set → eval gate catches it.
- Set a tiny perf budget → perf gate fails.
