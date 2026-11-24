# Walkthrough — Phase 1

- Date: 2026-11-24
- Tester: coding agent (automated; no human first-use test has been done yet — the owner should repeat this once)
- Primary task: shorten `https://example.com/walkthrough`, follow the short link, and see invalid input rejected
- Seconds: 2   <!-- measured: create + redirect + two rejected inputs against the running app took about 1.3 s of API time -->
- Completed without instructions: Y

How it was run: backend started with the `local` profile (no Docker), then the primary task was exercised over HTTP:
the link was created (201), the short link returned 302 to the original URL, `javascript:alert(1)` and a port-99999 URL were
rejected with the standard error body, and `/actuator/health` and `/swagger-ui.html` responded.
The UI flow is covered by component tests (`frontend/tests/ShortenPage.test.tsx`).

## Accessibility checks
- [x] Every input has a visible label (asserted by test: `getByLabelText`)
- [x] The whole task can be done with the keyboard only (native form + button; first Tab focuses the URL field, asserted)
- [x] Focus moves to errors; focus is visible (asserted by test; `:focus-visible` outline in CSS)
- [x] Status is shown as text, not color alone (`role="alert"` / `role="status"` text messages)

## Notes
- Not done: a human using the browser UI against the live backend, and a screen-reader pass. Those need the owner.
