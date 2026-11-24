# Code Review Checklist (per phase)

Copy into `docs/reviews/phase-N.md`, answer every item (`- [x]` = satisfied, or add a finding), and record findings in the table.

## Checklist
- [ ] Correctness: behavior matches the requirements owned by this phase; edge cases handled
- [ ] Security: untrusted input validated; no secrets; no execute/apply/deploy paths; errors leak nothing
- [ ] Simplicity: no unnecessary abstraction; smallest design that satisfies the phase
- [ ] Test quality: tests assert behavior, are tagged with requirement IDs, fail when the code is wrong
- [ ] Readability: names, structure, comments where the why is non-obvious
- [ ] Constitution compliance (Principles I–VII)

## Findings table format
| ID | Severity (BLOCKER/HIGH/MEDIUM/LOW) | Description | Status |
|----|------------------------------------|-------------|--------|
| R1 | MEDIUM | example | OPEN / FIXED / WAIVED (reason; approver: name; date: YYYY-MM-DD) |

Gate rule: no `OPEN` BLOCKER or HIGH finding; a `WAIVED` status must include `reason`, `approver` and `date`.
