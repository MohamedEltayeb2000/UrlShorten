# Walkthrough - Phase 5

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: run Greenfield through both approval gates, try the forbidden and invalid decisions, then retry, re-plan and roll back
- Seconds: 8   <!-- measured against the running app: the whole sequence took about 7.4 s -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker) and one seeded transient failure for the TESTING stage.
The run stopped at WAITING_FOR_APPROVAL for the IMPLEMENTATION patch proposal. An agent identity approving it got 403; rejecting
without a comment got 400; a human approval recorded `demo-user` and released the next stage. The release-readiness gate then
waited; approving it finished the run SUCCEEDED. TESTING showed 2 attempts (one injected transient failure, then success). A
re-plan from IMPLEMENTATION_PLAN created plan v2: only that stage and its dependents were marked stale and re-run (the design
artifact stayed v1 CURRENT; the plan went v1 STALE, v2 CURRENT) and the approval gate applied again. Restoring the stale v1
returned 409. The run accumulated 64 audit events. The approvals page, retry / re-plan controls and restore button are covered by
`frontend/tests/ApprovalsPage.test.tsx` and `RunControls.test.tsx`.

## Accessibility checks
- [x] Every input has a visible label (acting-as field, comment textarea and re-plan textarea are labeled; asserted by test)
- [x] The whole task can be done with the keyboard only (native buttons, textareas and links; asserted)
- [x] Focus moves to errors; focus is visible (asserted by test, including the missing-comment alert)
- [x] Status is shown as text, not color alone (APPROVED / REJECTED / WAITING_FOR_APPROVAL words; confirmation text for restore)

## Notes
- Not done: a human using the approvals page against the live backend, and a screen-reader pass. Those need the owner.
- The approver identity is a demo header, not authentication (see review finding R1).
