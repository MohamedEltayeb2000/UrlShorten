# Scenarios

Three repeatable scenarios show the engine on different kinds of input: **Greenfield**, **Brownfield** and **Ambiguous**.
All use the same eight-stage workflow graph (see [architecture.md](architecture.md)) and the deterministic mock agents, so
they work with no AI API key and give the same output every time. Output is always labeled `[SIMULATED]` and is a proposal:
nothing is applied, executed or deployed.

| | Greenfield | Brownfield | Ambiguous |
|---|-----------|------------|-----------|
| Shows mostly | Decomposition and orchestration (parallel branches, joins) | Grounded analysis of existing code | Validation, clarification and human decision |
| `scenario` value | `GREENFIELD` | `BROWNFIELD` | `AMBIGUOUS` |
| Needs a human before it finishes | Approve IMPLEMENTATION and RELEASE_READINESS | Same two approvals | Approve the clarification, then the same two approvals |
| Special input | none | `fixtures/brownfield/` (read-only) | none |

## How to run any scenario

In the UI open **Runs** (http://localhost:5173/runs), type or paste the requirement, pick the scenario in the **Scenario**
dropdown, press **Launch run**, then press **Start**. Approvals are handled on the **Approvals** page; artifacts at
`/runs/{id}/artifacts` (the Approvals page links there) and the audit timeline is shown with the run.

With curl (replace `ID` with the id returned by the first call):

```bash
curl -s -X POST localhost:8080/api/agent-runs -H 'Content-Type: application/json' \
  -d '{"requirement":"<requirement text>","scenario":"GREENFIELD"}'
curl -s -X POST localhost:8080/api/agent-runs/ID/start
curl -s localhost:8080/api/agent-runs/ID                       # stage states, levels, attempts
curl -s localhost:8080/api/agent-runs/ID/events?size=100       # audit trail
curl -s localhost:8080/api/agent-runs/ID/artifacts             # versioned artifacts
curl -s localhost:8080/api/approvals/pending                   # what is waiting for a human
curl -s -X POST localhost:8080/api/agent-runs/ID/approvals -H 'Content-Type: application/json' \
  -d '{"nodeId":"IMPLEMENTATION","decision":"APPROVE","comment":"looks right"}'
```

Mock stages finish in milliseconds, so the run races ahead to its first human gate almost immediately. To *see* the engine
work, read the stage `startedAt` / `completedAt` timestamps in the run detail and the `NODE_*` events in the audit trail.

Repeating a scenario is just creating another run with the same requirement text: output is deterministic, so the stage
outputs are identical each time (ids, timestamps and artifact ids differ).

> **Phase 6 additions.** `GET /api/scenarios` lists the three presets, and a run can be created with only
> `{"scenario":"GREENFIELD"}` (the preset supplies the requirement text). A one-click preset launcher in the dashboard and a
> markdown engineering summary at `GET /api/agent-runs/{id}/summary` are part of the same Phase 6 work. Until those are merged
> the requirement text below must be typed or sent explicitly, as in the examples.

---

## Scenario A: Greenfield

**Requirement text**

> Build a URL shortener API with link creation, redirects, expiration, and click analytics.

`scenario`: `GREENFIELD`.

### What it shows

* **Decomposition.** `RequirementsAnalystAgent` normalizes the requirement and produces four goals (create a short link,
  redirect, reject unsafe URLs, count visits) plus constraints. `PlannerAgent` turns each goal into a task and adds a final
  "verify behavior and update documentation" task.
* **Orchestration.** After TASK_DECOMPOSITION, `ARCHITECTURE_DESIGN` and `IMPLEMENTATION_PLAN` are both ready at once and run
  concurrently. `DOCUMENTATION` starts as soon as the design is done, without waiting for the implementation plan.
  `IMPLEMENTATION` is a **join**: it waits for both. `RELEASE_READINESS` is a second join (TESTING + DOCUMENTATION).
* **Validation and governance.** Every output is scored on six criteria and scanned for secrets and unsafe URL schemes before
  it is stored. `IMPLEMENTATION` produces a text diff with `applied=false`.

### Exact expected behavior

1. Run is `CREATED` with eight `PENDING` stages (levels 0 to 5). After **Start** it becomes `RUNNING`.
2. `REQUIREMENTS_ANALYSIS` then `TASK_DECOMPOSITION` run in order.
3. `ARCHITECTURE_DESIGN` and `IMPLEMENTATION_PLAN` are both `READY` on the same scheduler pass and run in parallel (their
   `startedAt` values are within milliseconds of each other; two `NODE_RUNNING` events appear back to back).
   `DOCUMENTATION` runs after `ARCHITECTURE_DESIGN`.
4. `IMPLEMENTATION` becomes `READY` only after both of its dependencies are `SUCCEEDED`. It produces a `CODE_PROPOSAL`
   artifact (v1) and enters `WAITING_FOR_APPROVAL` (it is gated twice: the node requires approval and the policy rule
   `generated-output-is-proposal` requires approval for applying a patch). Once nothing else can run, the run status becomes
   `WAITING_FOR_APPROVAL`.
5. After you **Approve** `IMPLEMENTATION`: `TESTING` runs (a plan plus *simulated* results: 12 passed, 0 failed).
6. `RELEASE_READINESS` becomes ready when both `TESTING` and `DOCUMENTATION` have succeeded, produces a checklist with
   `ready = true` and no blockers, and waits for approval (policy rule `release-readiness-requires-approval`).
7. After the second approval the run ends `SUCCEEDED`. Eight artifacts exist, each at v1, all `CURRENT`.

### Where to see it

| What | UI | API |
|------|----|-----|
| Stage graph, levels, attempts | Runs, open the run | `GET /api/agent-runs/{id}` |
| Parallel start of the two branches | Stage start times in the run; Audit timeline `NODE_RUNNING` entries | `GET /api/agent-runs/{id}/events` |
| Join waiting | `IMPLEMENTATION` stays `PENDING` until both parents are `SUCCEEDED` | run detail `dependsOn` and `status` |
| Approval gates | Approvals page (cards for IMPLEMENTATION and RELEASE_READINESS) | `GET /api/approvals/pending`, `POST /api/agent-runs/{id}/approvals` |
| Artifact, version, provenance | `/runs/{id}/artifacts`: version, status, checksum, created by, inputs, parents | `GET /api/agent-runs/{id}/artifacts` |

### Repeat it

Create the run again with the same text and scenario. To also exercise retry and fallback on this scenario start the backend
with `--shortener.orchestrator.inject-failures=TESTING:3` (see [demo-script.md](demo-script.md)).

---

## Scenario B: Brownfield

**Requirement text**

> Add idempotent link creation and prevent duplicate links when clients retry requests.

`scenario`: `BROWNFIELD`.

### What it shows

Analysis grounded in the structure of a small seeded legacy service under `fixtures/brownfield/` (plain text, not compiled,
read-only). The analyzer scans `.java` and `.sql` files (comments are ignored), builds a class dependency graph (controller to
service to repository), and finds HTTP endpoints and SQL tables. Point it at another source directory with the
`shortener.brownfield.path` system property (or `SHORTENER_BROWNFIELD_PATH`). Its
seeded defect: `LinkService.createLink` inserts a new row on every call, so a retried `POST /links` creates a duplicate short
link. There is no idempotency key, request fingerprint, unique constraint on the target URL or idempotency table. The system
does **not** inspect arbitrary repositories.

### Exact expected behavior

1. `REQUIREMENTS_ANALYSIS` (confidence 0.85, `SUCCESS`) adds an extra goal ("make retried link creation idempotent without
   breaking existing clients") and records, from reading the fixture:
   * `impactedFiles`: files whose class or table name matches terms of the requirement (here `link`), plus everything on the
     dependency path from the controller to its tables (`LinkController.java`, `LinkService.java`, `LinkRepository.java`,
     `schema.sql`);
   * `dataFlow`: `POST /links -> LinkController.postLink -> LinkService.createLink -> LinkRepository.insert -> table links`;
   * `compatibilityRisks`, each derived from a finding: no uniqueness constraint on `target_url` although the insert writes
     it; `LinkService.createLink` inserts without checking for an existing row, so a retried `POST /links` creates a
     duplicate; the `POST /links` API contract must be kept (new inputs optional, same status and body shape); table `links`
     is touched so a schema migration must tolerate existing duplicates.
2. `TASK_DECOMPOSITION` yields one task per goal plus a verification task.
3. `ARCHITECTURE_DESIGN` copies the impacted files, fixture data flow and compatibility risks, and adds the decision "add
   idempotency to link creation using an Idempotency-Key header and a unique key". `IMPLEMENTATION_PLAN` lists the impacted
   files as `filesToChange`.
4. `IMPLEMENTATION` proposes a diff touching `LinkController.java` (read the optional header), `LinkService.java` (fingerprint
   and replay) and `schema.sql` (an `idempotency_keys` table with a unique key), `applied=false`. It waits for approval like
   Greenfield.
5. `TESTING` adds the two idempotency checks ("retrying with the same key returns the original link", "a different
   fingerprint under the same key is rejected") to its plan. `DOCUMENTATION` and `RELEASE_READINESS` follow as in
   Greenfield, with the same two approvals; the run ends `SUCCEEDED`.

### Where to see it

Artifact `REQUIREMENT` (stage `REQUIREMENTS_ANALYSIS`) shows `impactedFiles`, `dataFlow` and `compatibilityRisks` in its
content; `DESIGN` and `CODE_PROPOSAL` repeat them. Open them under `/runs/{id}/artifacts`, or
`GET /api/agent-runs/{id}/artifacts`.

### Repeat it

Edit a fixture file structurally (for example add a lookup of the existing link by target URL to `LinkService.createLink`)
and create a new run: the "without checking for an existing row" risk disappears, because risks come from the code
structure. Adding the word "idempotent" in a comment changes nothing. Revert the file afterwards.

---

## Scenario C: Ambiguous

**Requirement text**

> Make URL links safer and improve analytics.

`scenario`: `AMBIGUOUS`.

### What it shows

Validation and human-in-the-loop control: the analyst does not guess. It records open questions, states explicit assumptions,
classifies risks by severity, lowers its confidence, and the run does not proceed until a human decides.

### Exact expected behavior

1. `REQUIREMENTS_ANALYSIS` returns status `NEEDS_CLARIFICATION` with confidence 0.45 and a note explaining the low
   confidence. The requirement text is analyzed (`RequirementAnalysis`): vague, unmeasurable terms ("safer", "improve"),
   very short text, conflicting hints and undefined references raise an ambiguity score, and the stage also asks for
   clarification whenever the score reaches the threshold, even without the `AMBIGUOUS` label. Its content has:
   * at least three **open questions**, derived from the triggers: for this text, what "safer" means (blocking malicious
     URLs, preventing abuse, or protecting stored data) and which measurable outcome defines "improve", topped up with a
     generic question (who may create links, link expiry, redirect volume) to reach three;
   * three **safe assumptions** (links are public, only http and https targets, links do not expire unless set), carried into
     every later stage;
   * three **risks** with severity prefixes: `HIGH` the unmeasurable terms may be implemented differently than intended,
     `MEDIUM` undefined expiry and unbounded storage, `LOW` unknown traffic.
2. The artifact is stored (`REQUIREMENT` v1), the stage enters `WAITING_FOR_APPROVAL` (approval kind `CLARIFICATION`, reason
   "Clarification needed: 3 open question(s)"), audit shows `CLARIFICATION_NEEDED` then `APPROVAL_REQUESTED`, and the run
   becomes `WAITING_FOR_APPROVAL`. Nothing downstream starts.
3. On the **Approvals** page the card lists the open questions and assumptions. A human **Approve** (comment recommended,
   for example "proceed with the safe assumptions") continues the run; a **Reject** (comment required) blocks the stage so the
   requirement can be changed and the stage re-planned or retried.
4. After approval the pipeline proceeds on the safe assumptions with reduced confidence (0.7) in every later stage. As in the
   other scenarios, `IMPLEMENTATION` and `RELEASE_READINESS` need approval.
5. `RELEASE_READINESS` reports `ready = false` with the blocker "Open clarification questions have not been confirmed by a
   human", and a checklist item "Requirements confirmed" at `PENDING`. It still goes to a human for the final decision; the
   approver can see the blocker on the card before approving.

### Where to see it

| What | UI | API |
|------|----|-----|
| Clarification-needed state | Run shows `WAITING_FOR_APPROVAL`; stage badge on `REQUIREMENTS_ANALYSIS` | `GET /api/agent-runs/{id}` |
| Open questions and assumptions | Approvals page card (sections "Open questions" and "Assumptions") | `GET /api/approvals/pending` (`openQuestions`, `assumptions`) |
| Human decision recorded | Audit timeline `APPROVAL_APPROVED` with actor and comment | `GET /api/agent-runs/{id}/events`; `GET /api/agent-runs/{id}/approvals` |

### Repeat it

Create the run again with the same text. If you want to see rejection, reject the clarification with a comment, then use
re-plan on `REQUIREMENTS_ANALYSIS` with a clarified requirement (for example "Make URL links safer by rejecting non-http(s)
URLs, and add a daily click count per link"). The mock analyst reads the text, so a precise
requirement is analyzed as clear (`SUCCESS`) when sent without the `AMBIGUOUS` label; with the label it still asks questions.
