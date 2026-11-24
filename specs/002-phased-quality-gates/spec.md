# Feature Specification: Phased Delivery with Quality Gates

**Feature Branch**: `002-phased-quality-gates`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "make it phase by phase for simple functionality but in your consideration security testing and code review and AI evaluation criteria and performance and simple frontend"

**Relationship to 001**: Feature `001-agentic-url-shortener` defines the full product scope. This feature defines *how it is delivered*: in small, simple, phase-sized increments, each of which must pass security, review, AI-evaluation, performance and test gates before it counts as done, with a deliberately simple frontend.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deliver one simple, working phase at a time (Priority: P1)

The project owner wants the system built in small phases, starting with the simplest useful functionality (shortening and redirecting links) and adding orchestration capabilities one slice at a time. After each phase the system runs end to end, the phase's tests pass, and the phase is recorded as complete with evidence.

**Why this priority**: Phase-by-phase simplicity is the user's central request and de-risks everything else.

**Independent Test**: Take any single phase in isolation: run its checks and confirm the system still starts and the phase's capability works, without later phases existing.

**Acceptance Scenarios**:

1. **Given** the phase plan, **When** a phase is started, **Then** it has a short list of simple capabilities, explicit exit criteria, and the requirements it satisfies.
2. **Given** a phase's capabilities are built, **When** its exit checks run, **Then** the phase is marked complete only if every check passes.
3. **Given** a completed phase, **When** the next phase begins, **Then** earlier phases' checks still pass (no regression).

---

### User Story 2 - Prove requirements with traceable tests (Priority: P1)

A reviewer opens a traceability view and sees, for every requirement of feature 001, which test(s) prove it and whether they currently pass. A phase cannot be declared complete while any requirement assigned to it lacks a passing test.

**Why this priority**: The user asked to make sure requirements are satisfied through test cases.

**Independent Test**: Pick any requirement, follow the trace to its test, run it, and see a pass/fail result that matches the view.

**Acceptance Scenarios**:

1. **Given** the requirement list, **When** the traceability view is generated, **Then** every requirement shows at least one linked test and a current result.
2. **Given** a requirement with no test, **When** its phase is evaluated, **Then** the phase is reported incomplete and the gap is named.

---

### User Story 3 - Security gate per phase (Priority: P1)

Before a phase is complete, a security gate runs: unsafe input handling (malicious URL schemes, injection attempts, oversized input), secrets scanning, dependency vulnerability checks, rate-limit and abuse checks, and verification that nothing is applied or executed without approval. Findings above an agreed severity block the phase.

**Why this priority**: The product accepts untrusted URLs and redirects users; security failures are the highest-impact risk.

**Independent Test**: Feed the security gate a set of known-bad inputs and a planted fake secret; confirm all are rejected or flagged and the phase is blocked until resolved.

**Acceptance Scenarios**:

1. **Given** malicious inputs (unsafe URL schemes, script/SQL-style payloads, oversized values), **When** submitted, **Then** each is rejected with a clear error and none is stored or executed.
2. **Given** a fake credential in code or output, **When** the secrets check runs, **Then** it is flagged and blocks the phase.
3. **Given** a known-vulnerable dependency, **When** the dependency check runs, **Then** it is reported with severity and blocks the phase if at or above the agreed threshold.

---

### User Story 4 - Code review gate per phase (Priority: P2)

Each phase's changes go through a structured review against a written checklist (correctness, security, simplicity, test quality, readability, constitution compliance). Review findings are recorded, and blocking findings must be resolved before the phase is pushed as complete.

**Why this priority**: Ensures quality and explainability, but depends on having a phase's changes to review.

**Independent Test**: Submit a phase's change set for review and confirm a findings record exists with each blocking item resolved or explicitly waived with justification.

**Acceptance Scenarios**:

1. **Given** a completed phase, **When** review is performed, **Then** a review record lists findings by severity against the checklist.
2. **Given** a blocking finding, **When** the phase is evaluated, **Then** it cannot be marked complete until the finding is fixed or waived with a recorded reason.

---

### User Story 5 - AI evaluation criteria for agent output (Priority: P2)

The owner defines how agent output is judged. Every agent output is scored against explicit criteria (schema validity, completeness for its stage, consistency with upstream artifacts, absence of unsafe content, correct labeling as simulated or AI-generated, and reproducibility for the deterministic agents). A fixed evaluation set per scenario (Greenfield, Brownfield, Ambiguous) is run each phase, and results are reported with a pass threshold.

**Why this priority**: The AI behavior is the assessed theme; evaluation makes quality measurable rather than anecdotal.

**Independent Test**: Run the evaluation set against the deterministic agents; confirm a score report is produced and a deliberately degraded output (e.g., missing required field) is scored as failing.

**Acceptance Scenarios**:

1. **Given** an agent output, **When** evaluated, **Then** each criterion yields pass/fail with a reason and an overall score.
2. **Given** an output below threshold, **When** evaluated, **Then** it is flagged, recorded in the audit trail, and not used downstream without approval.
3. **Given** the Ambiguous scenario, **When** evaluated, **Then** the output is only passing if it records open questions and assumptions rather than guessing silently.
4. **Given** repeated runs of the deterministic agents on the same input, **When** compared, **Then** results are identical.

---

### User Story 6 - Performance gate per phase (Priority: P2)

Each phase has simple, stated performance expectations (e.g., link creation and redirect feel instant, dashboard loads quickly, the scenario demo finishes within a short time, concurrent independent stages actually save time). A lightweight performance check runs per phase and reports results against budgets.

**Why this priority**: Performance claims must be measured, but correctness and security come first.

**Independent Test**: Run the performance check on a phase and compare reported figures to the stated budgets.

**Acceptance Scenarios**:

1. **Given** a phase with performance budgets, **When** the check runs, **Then** it reports measured values and a pass/fail against each budget.
2. **Given** two independent stages that each take time, **When** run together, **Then** total time is measurably less than running them in sequence.
3. **Given** a burst of redirect requests, **When** load is applied, **Then** responses stay within budget and no request errors occur.

---

### User Story 7 - Simple frontend (Priority: P2)

A user gets a deliberately simple web interface: a form to shorten a link and see its result, a list of links with click counts, and — as phases progress — a plain page to launch a scenario run, see stage statuses, approve or reject pending items, and see basic metrics. It favors clarity, readable states (loading, empty, error, waiting for approval) and keyboard/screen-reader basics over visual polish.

**Why this priority**: The user asked for a simple frontend; it makes each phase demonstrable.

**Independent Test**: With only the phase's backend capabilities present, a user can complete that phase's primary task through the interface without instructions.

**Acceptance Scenarios**:

1. **Given** the shortener phase, **When** a user enters a valid URL and submits, **Then** the short link appears with a copy action; an invalid URL shows a clear message.
2. **Given** a run in progress, **When** the user views it, **Then** each stage shows a plain, distinct status and updates without a manual reload.
3. **Given** a pending approval, **When** the user opens the approvals page, **Then** they can approve or reject with a comment.
4. **Given** loading, empty or error conditions, **When** they occur, **Then** a clear message is shown instead of a blank screen.

---

### User Story 8 - Push each completed phase to GitHub (Priority: P3)

When a phase passes all gates, the owner's GitHub repository receives it as a clearly labeled commit/branch update, including the phase's tests, review record, evaluation and performance results, and updated traceability, so progress is visible phase by phase.

**Why this priority**: Delivers visibility, but depends on the gates existing and the owner confirming the target repository.

**Independent Test**: After a phase passes, view the repository and confirm a labeled phase update exists with its evidence files and no secrets.

**Acceptance Scenarios**:

1. **Given** a phase that passed all gates, **When** it is published, **Then** the repository shows a labeled update containing code, tests and evidence.
2. **Given** a phase with any failing gate, **When** publishing is attempted, **Then** it is refused with the failing gate named.
3. **Given** no confirmed repository target, **When** publishing is attempted, **Then** the owner is asked to confirm it first.

---

### Edge Cases

- A phase's requirement set is empty or a requirement is split across phases: each part must still map to a test in its own phase.
- A gate tool is unavailable (e.g., no network for dependency checks): the gate reports "not run" and the phase cannot be completed silently.
- A reviewer waives a blocking finding: the waiver, reason and approver are recorded and visible.
- Evaluation criteria change between phases: prior scores are kept and labeled with the criteria version.
- Performance varies by machine: budgets use relative or generous thresholds, and results record the environment.
- A later phase breaks an earlier phase's tests: the later phase is blocked until the regression is fixed.
- Test data or fixtures contain look-alike secrets: they are clearly marked and allow-listed, not hidden.

## Requirements *(mandatory)*

### Functional Requirements

**Phasing**

- **FR-001**: The project MUST define an ordered set of phases, each with a short list of simple capabilities, the requirements (from feature 001) it covers, and explicit exit criteria.
- **FR-002**: Each phase MUST leave the system runnable end to end and MUST NOT break earlier phases' checks.
- **FR-003**: The first phase MUST deliver only the simplest useful capability (create a short link and redirect), independent of the agent system.

**Traceability and tests**

- **FR-004**: Every requirement of feature 001 MUST be mapped to at least one automated test in a traceability record showing the latest pass/fail result.
- **FR-005**: A phase MUST be reported incomplete if any requirement assigned to it has no test or a failing test, naming the gap.

**Security gate**

- **FR-006**: Each phase MUST run a security gate covering hostile-input rejection, secrets scanning, dependency vulnerability checking, abuse/rate-limit behavior, and confirmation that nothing is applied, executed or deployed without recorded approval.
- **FR-007**: Findings at or above a defined severity threshold MUST block phase completion; a gate that could not run MUST be reported as "not run" and also block completion.

**Code review gate**

- **FR-008**: Each phase MUST have a recorded structured review against a written checklist (correctness, security, simplicity, test quality, readability, constitution compliance).
- **FR-009**: Blocking review findings MUST be resolved, or waived with recorded reason and approver, before phase completion.

**AI evaluation**

- **FR-010**: The project MUST define explicit evaluation criteria for agent output covering schema validity, stage completeness, consistency with upstream artifacts, absence of unsafe content, correct simulated/AI labeling, and determinism for simulated agents.
- **FR-011**: The project MUST include a fixed evaluation set for the Greenfield, Brownfield and Ambiguous scenarios, run each phase that touches agent behavior, producing per-criterion and overall scores against a pass threshold.
- **FR-012**: Agent output scoring below threshold MUST be flagged in the audit trail and MUST NOT be consumed downstream without approval.
- **FR-013**: Evaluation results MUST record the criteria version so results remain comparable over time.

**Performance**

- **FR-014**: Each phase MUST define simple performance budgets and run a lightweight check reporting measured values against them, including the environment used.
- **FR-015**: The check MUST demonstrate that independent stages run faster concurrently than sequentially, and that link redirects remain within budget under a burst of requests.

**Simple frontend**

- **FR-016**: The frontend MUST provide pages, introduced phase by phase, for: shortening a link and listing links with click counts; launching a scenario run and viewing stage statuses; approving or rejecting pending items; and viewing basic metrics.
- **FR-017**: The frontend MUST show clear loading, empty, error and approval-pending states, label generated code as an unapplied proposal, and meet basic keyboard and screen-reader usability.
- **FR-018**: The frontend MUST prioritize simplicity (plain layout, minimal interactions) over visual polish.

**Publishing**

- **FR-019**: A phase MUST be published to the owner's GitHub repository only after all gates pass, as a clearly labeled update including code, tests, review record, evaluation and performance results, and traceability, with no secrets.
- **FR-020**: The target repository and branch MUST be confirmed by the owner before the first publish.

### Key Entities

- **Phase**: Name, ordered position, capabilities, covered requirements, exit criteria, status, evidence links.
- **Requirement Trace**: Requirement ID, linked tests, latest result, owning phase.
- **Gate Result**: Gate type (tests, security, review, AI evaluation, performance), phase, outcome (pass, fail, not run), findings, timestamp.
- **Review Record**: Phase, checklist items, findings with severity, resolutions or waivers with approver and reason.
- **Evaluation Criteria / Evaluation Report**: Versioned criteria; per-scenario, per-criterion scores and overall result.
- **Performance Budget / Result**: Metric, budget, measured value, environment.
- **Publish Record**: Phase, repository, branch, timestamp, gates passed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of requirements in feature 001 are mapped to at least one automated test, and 100% of those tests pass before the final phase is published.
- **SC-002**: Each phase can be demonstrated end to end by a first-time user in under 3 minutes after starting the system.
- **SC-003**: 100% of the defined hostile-input cases are rejected and 100% of planted fake secrets are detected in every phase's security gate.
- **SC-004**: 0 phases are marked complete with an unresolved blocking security or review finding.
- **SC-005**: The deterministic agents score at or above the pass threshold on 100% of the evaluation set, and deliberately degraded outputs are caught in 100% of cases.
- **SC-006**: Repeated evaluation runs on identical input produce identical scores.
- **SC-007**: Link creation and redirects feel instant to users (under 1 second in 95% of requests in the performance check), and the scenario demo completes in under 2 minutes.
- **SC-008**: Running two independent stages concurrently takes measurably less total time than running them back to back.
- **SC-009**: A new user completes the primary task of each frontend page on the first attempt without instructions, and every page shows a clear message in loading, empty and error conditions.
- **SC-010**: Every published phase appears in the GitHub repository with its evidence files and 0 committed secrets.

## Assumptions

- The owner supplies or confirms the GitHub repository and branch before the first publish; none is configured yet. Until then, completed phases are committed and tagged locally and marked as awaiting publication, and are pushed once the target is confirmed.
- Feature 001 remains the source of truth for product requirements; this feature adds delivery, quality and process requirements only.
- Severity thresholds for blocking findings default to "high and above" unless the owner specifies otherwise.
- AI evaluation of deterministic agents is rule-based and repeatable; any optional external AI provider is evaluated with the same criteria but is not required.
- Performance budgets are modest, set for a single local machine, and recorded with the environment because absolute numbers vary.
- "Simple frontend" means plain, functional pages; visual design polish, theming and animations are out of scope.
- Review is performed by the coding agent and presented to the owner, who remains the approver for waivers.
