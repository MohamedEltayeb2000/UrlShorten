# Contract: Gate Scripts

Each gate is a PowerShell script in `scripts/` (`.ps1` only for this build; no `.sh` mirrors) with this contract.

## Invocation
```
scripts/gate-<name> --phase <1-6>
```
`<name>` ∈ `tests | security | eval | perf`. Review is a document gate checked by `gate-review`.
`scripts/gate-all --phase N` runs all applicable gates; `scripts/publish-phase N` calls `gate-all` first.

## Exit codes
| Code | Meaning |
|------|---------|
| 0 | `PASS` |
| 1 | `FAIL` (findings written) |
| 2 | `NOT_RUN` (tool missing/unreachable) — treated as blocking |
| 3 | `SKIPPED` (gate not applicable to this phase, e.g. eval before P4) — non-blocking, recorded |

## Output
Writes `docs/gates/phase-N.json` (merge-updated per gate):
```json
{
  "phase": 1,
  "gates": {
    "TESTS":    { "outcome": "PASS", "findings": [], "timestamp": "ISO-8601" },
    "SECURITY": { "outcome": "PASS", "findings": [], "toolVersions": { "gitleaks": "x" } }
  }
}
```
Finding: `{ "severity": "HIGH", "summary": "...", "location": "path:line" }`.

## Per-gate rules
| Gate | PASS when | Extra outputs |
|------|-----------|---------------|
| tests | All backend + frontend tests pass; every requirement owned by the phase has ≥1 passing tagged test; prior phases' tests still pass | `docs/traceability.md` |
| security | Self-test passes first (a planted fake key in a scratch file MUST be detected, else the gate is `FAIL`); hostile-input suite passes; Gitleaks clean (allow-list only for marked fakes); Dependency-Check and `npm audit` have no finding ≥ HIGH; approval-bypass tests pass | — |
| review | `docs/walkthroughs/phase-N.md` exists with a timed first-use run under 3 minutes and an accessibility check; `docs/reviews/phase-N.md` exists, all checklist items answered, no open BLOCKER/HIGH without a waiver (reason + approver + date) | — |
| eval (P4+) | Overall score ≥ threshold on all three scenario sets; degraded set fails as expected; repeat run yields identical scores | `docs/evals/phase-N.json` |
| perf | All budgets in `docs/perf/budgets.json` met, including frontend production bundle size and page render time | `docs/perf/phase-N.md` |

## Publish contract
`scripts/publish-phase N [-DryRun]` MUST: verify every applicable gate is `PASS` or `SKIPPED`
(`-DryRun` performs all checks and reports what would be pushed, without committing or pushing); if
`origin` or the owner-confirmed branch (`docs/publish-config.json`) is missing, commit and tag locally and
record `PUBLISH_PENDING` (never push); otherwise verify `origin` exists and the branch matches; run Gitleaks on the staged diff; commit as
`phase-N: <phase name>` with the repository's attribution trailer; push branch and tag `phase-N`;
append to `docs/publish-log.md`. It MUST refuse (non-zero exit, named reason) otherwise.
