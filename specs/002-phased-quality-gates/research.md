# Research: Phased Delivery with Quality Gates

The stack is fixed by `plan.md` and the constitution, so research covered only the quality-gate
tooling and process choices. No `NEEDS CLARIFICATION` items remain.

## R1 — Requirement-to-test traceability
- **Decision**: A custom `@Requirement("001-FR-024")` annotation (JUnit tag-style) on backend tests and a `// @req 001-FR-016` comment convention in frontend tests; a script scans both and regenerates `docs/traceability.md` with latest results from the test reports.
- **Rationale**: Cheap, keeps the link next to the test, and is checkable by script (a requirement owned by a phase with no passing tagged test fails the gate).
- **Alternatives**: Manual spreadsheet (drifts); Cucumber/BDD (heavy for a one-day build).

## R2 — Secrets scanning
- **Decision**: Gitleaks with a repo allow-list file for clearly marked fake fixtures.
- **Rationale**: Single binary, works pre-commit and in the gate; supports allow-list for planted fakes (spec edge case).
- **Alternatives**: TruffleHog (heavier); regex-only script (weaker coverage). Fallback if the binary is unavailable: built-in regex scanner script; gate reports "not run" for Gitleaks rather than silently passing.

## R3 — Dependency vulnerabilities
- **Decision**: OWASP Dependency-Check Maven plugin (fail on CVSS ≥ 7) and `npm audit --audit-level=high`.
- **Rationale**: Native to each build tool, no accounts needed. First run downloads the NVD data and can be slow; the gate documents an optional NVD API key via env var and reports "not run" if unreachable.
- **Alternatives**: Snyk/Trivy (account or extra install).

## R4 — Hostile-input security tests
- **Decision**: Parameterized JUnit suites with a shared corpus (`javascript:`, `data:`, `file:`, overlong URLs, control characters, CRLF injection in alias, SQL/script payloads, unicode lookalikes) asserting rejection with the standard error body and nothing persisted.
- **Rationale**: Deterministic and fast; covers FR-006 without a separate scanner.
- **Alternatives**: OWASP ZAP (heavier; optional manual run documented as a stretch).

## R5 — AI evaluation
- **Decision**: A rule-based `AgentOutputEvaluator` scoring six criteria (schema valid, stage-complete, upstream-consistent, no unsafe content/secrets, correctly labeled simulated, deterministic across two runs) over fixed JSON eval sets for Greenfield, Brownfield, Ambiguous plus a "degraded" set that must fail. Criteria carry a version string; reports written as JSON.
- **Rationale**: Repeatable with no API key (spec SC-005/006); same evaluator can score an optional LLM provider later. Ambiguous scenario passes only if open questions and assumptions are non-empty.
- **Alternatives**: LLM-as-judge (nondeterministic, needs key).

## R6 — Performance checks
- **Decision**: JUnit-driven checks: (a) concurrent vs. sequential stage execution using stages with a fixed simulated delay, asserting concurrent time < sequential × 0.7; (b) redirect burst using Java `HttpClient` with a few hundred requests across a thread pool, asserting p95 < 1 s and 0 errors; (c) scenario demo wall-clock < 2 min. Results written with OS/CPU/JDK info.
- **Rationale**: No extra tools; generous thresholds avoid flakiness across machines.
- **Alternatives**: k6/JMeter (extra install, overkill).

## R7 — Code review gate
- **Decision**: Per-phase markdown review record from a fixed checklist (correctness, security, simplicity, test quality, readability, constitution compliance), severity-tagged; produced by the coding agent's review pass and shown to the owner, who approves waivers.
- **Rationale**: Satisfies FR-008/009 with auditable evidence in the repo.
- **Alternatives**: GitHub PR review only (not available until remote exists).

## R8 — Frontend simplicity
- **Decision**: React + Vite + TypeScript, plain CSS, `fetch`-based typed client, polling hook (2 s), no state library, no component library; React Router with ≤5 pages. Workflow graph shown as a plain ordered/grouped stage list in P3; optional SVG graph in P6 only if time allows.
- **Rationale**: Matches "simple frontend"; accessibility basics via semantic HTML, labels, focus handling.
- **Alternatives**: React Flow (deferred; risk to timeline).

## R9 — GitHub publishing
- **Decision**: `publish-phase` script runs all gates, then `git add`, commits with a `phase-N:` message, and pushes the working branch plus a lightweight tag `phase-N` using the user's existing `git`/`gh` authentication. The script refuses to run until a remote named `origin` exists and the owner has confirmed repo and branch.
- **Rationale**: Uses the owner's own credentials; no tokens stored in the repo. The directory is not yet a git repository and has no remote, so initialization is a task for the first phase.
- **Alternatives**: Pushing via an API token (secret-handling risk).

## R10 — Phase sizing
- **Decision**: Six phases (see plan.md), each ≤ ~1–2 hours of work, ordered so the shortener works alone (P1–P2) before any orchestration exists.
- **Rationale**: Satisfies FR-001–003 and keeps the system runnable after each phase.
