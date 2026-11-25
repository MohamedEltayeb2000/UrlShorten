# Walkthrough - Phase 7

- Date: 2026-11-25
- Tester: Playwright driving a real Chromium browser against the running backend and frontend (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: shorten a link, follow and deactivate it; launch Greenfield and approve both gates with change records; launch Ambiguous and decide the clarification
- Seconds: 47   <!-- measured: all four browser journeys including server start-up reuse took 47 s -->
- Completed without instructions: Y

How it was run: `npx playwright test` in `frontend/` starts the backend (in-memory H2, no stage delay) and the Vite dev server, then
drives the UI by labels and roles only. Journeys: shorten + redirect (302) + click count + deactivate (404); an unsafe URL is rejected with
the alert focused; Greenfield from the Metrics scenario card through both approvals (change ticket and justification entered) to
SUCCEEDED, with "Entry gate: passed" visible and the "Proposal - not applied" banner on the artifacts page; Ambiguous stops with open
questions and resumes after the decision. A separate run against real PostgreSQL (Docker) applied all 6 migrations and completed a
Greenfield run (8 artifacts, 63 audit events, 2 approvals).

## Accessibility checks
- [x] Every input has a visible label (the tests locate all fields by label)
- [x] The whole task can be done with the keyboard only (native controls; unit tests assert it)
- [x] Focus moves to errors; focus is visible (the browser test asserts the alert is focused)
- [x] Status is shown as text, not color alone (stage, gate and approval states are text)

## Notes
- Not done: a human using the UI, and a screen-reader pass.
