# Walkthrough - Phase 6

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: launch a scenario from its preset, approve the gates, read the run metrics and download the engineering summary
- Seconds: 6   <!-- measured against the running app: about 5.4 s for the whole sequence -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker, no API key) and one seeded transient failure for TESTING.
`GET /api/scenarios` returned the three presets. `POST /api/agent-runs` with only `{"scenario":"GREENFIELD"}` created a run with
the seeded requirement. After approving the two gates the run was SUCCEEDED with 8 stages, 1 retry, 0 fallbacks, MTTR 97 ms and
2 approvals. The overall metrics showed 1 run, success rate 1.0. `GET .../summary` returned `text/markdown` as an attachment with
the 8 required sections. The metrics page, scenario cards and summary download are covered by `frontend/tests/MetricsPage.test.tsx`.

## Accessibility checks
- [x] Every input has a visible label (run selector and Launch buttons carry accessible names; asserted by test)
- [x] The whole task can be done with the keyboard only (native select and buttons; asserted)
- [x] Focus moves to errors; focus is visible (asserted by test for each section's error alert)
- [x] Status is shown as text, not color alone (metric values, percentages and "n/a" are text)

## Notes
- Not done: a human using the UI against the live backend, and a screen-reader pass. Those need the owner.
