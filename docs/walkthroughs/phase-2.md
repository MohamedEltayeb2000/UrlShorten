# Walkthrough - Phase 2

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet - the owner should repeat this once)
- Primary task: create a link with an expiry and an idempotency key, follow it twice, read its analytics, deactivate it, and hit the creation rate limit
- Seconds: 11   <!-- measured against the running app: whole sequence including 40 rate-limit probes took about 10.6 s -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker). Create with `Idempotency-Key` returned 201; replaying
the same request returned 200 with `Idempotent-Replay: true`; two redirects returned 302; the per-link analytics reported
`totalClicks: 2`; `DELETE` returned 204 and the short link then returned 404; 40 rapid creations produced 7 responses with
429 once the 30-per-minute bucket was used up. The UI behavior (clicks column, expiry field, deactivate button, analytics
panel, error states) is covered by component tests in `frontend/tests/ClickCounts.test.tsx` and `ShortenPage.test.tsx`.

## Accessibility checks
- [x] Every input has a visible label (including the new "Expires at (optional)" field; asserted by test)
- [x] The whole task can be done with the keyboard only (native controls; deactivate button focusable and activated with Enter, asserted)
- [x] Focus moves to errors; focus is visible (asserted by test; `:focus-visible` outline in CSS)
- [x] Status is shown as text, not color alone (status column text, "Analytics unavailable: ..." message)

## Notes
- Not done: a human using the browser UI against the live backend, and a screen-reader pass. Those need the owner.
