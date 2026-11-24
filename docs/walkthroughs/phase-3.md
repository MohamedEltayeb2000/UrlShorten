# Walkthrough - Phase 3

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: launch the Greenfield scenario, start it, watch stages run (two in parallel), and see the run finish
- Seconds: 6   <!-- measured against the running app: create + start + two status polls + completion took about 5 s -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker). `POST /api/agent-runs` returned a CREATED run with 8
PENDING stages; `POST .../start` returned RUNNING; a poll shortly after showed IMPLEMENTATION running with DOCUMENTATION
ready (the parallel branch); a later poll showed the run SUCCEEDED with every stage SUCCEEDED. The Runs page behavior
(launcher, stage list grouped by level with "Parallel stages", start/pause, polling, error states) is covered by
`frontend/tests/RunsPage.test.tsx`.

## Accessibility checks
- [x] Every input has a visible label (requirement textarea and scenario select; asserted by test)
- [x] The whole task can be done with the keyboard only (native form controls and buttons)
- [x] Focus moves to errors; focus is visible (asserted by test)
- [x] Status is shown as text, not color alone (each stage shows its status word; colour is extra)

## Notes
- Not done: a human using the Runs page against the live backend, and a screen-reader pass. Those need the owner.
