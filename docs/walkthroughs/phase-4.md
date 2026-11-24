# Walkthrough - Phase 4

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: run the Greenfield, Brownfield and Ambiguous scenarios and inspect the versioned artifacts and audit trail
- Seconds: 6   <!-- measured against the running app: three runs, artifacts and events fetched, about 5.6 s -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker, no API key). Greenfield and Brownfield runs each finished
SUCCEEDED with 8 artifacts (one per stage) and 35 audit events. The Ambiguous run stopped at WAITING_FOR_APPROVAL after the
requirements analysis with 1 artifact and 8 audit events, as designed. The artifact viewer, version diff, "Proposal - not applied"
banner and audit timeline are covered by `frontend/tests/ArtifactsPage.test.tsx`.

## Accessibility checks
- [x] Every input has a visible label (version selector and paging buttons labeled; asserted by test)
- [x] The whole task can be done with the keyboard only (selector and paging buttons are native controls; asserted)
- [x] Focus moves to errors; focus is visible (asserted by test)
- [x] Status is shown as text, not color alone (CURRENT/STALE/SUPERSEDED and diff +/- prefixes are text)

## Notes
- Not done: a human using the artifact page against the live backend, and a screen-reader pass. Those need the owner.
