# Step wording and detail placement

How oreo-e2e scenarios read, and where the precision a scenario needs goes instead of its steps. Complements
`docs/standards/bdd-feature-generation-standards.md` §6-§7. The generated step index
(`target/ai/{api,ui}-step-index.md`) is the vocabulary; the patterns below are its common shapes, quoted from the
existing snippets.

## API scenarios

Concerns, in order; not every scenario needs every one:

| Concern | Existing shape |
|---|---|
| Precondition (trade state) | `Given a live '<product_type>' trade is created via api`; `a draft ...`, `a dead ...`, `a '<product_type>' trade is submitted to pending approval via api` |
| Actor data readiness | lifecycle event: `When maker has valid data for the 'EarlyTermination' event`; `... has invalid data with 'an empty sub-trade list' for the 'Allocation' event`; `... for the '<event>' event on the trade stored as '<key>'`; `maker has data for the '<event>' event on a trade that does not exist`. Other: the feature's own, e.g. `maker has valid data for Trade Amendment` |
| Business action | lifecycle event: `And performs the 'EarlyTermination' event via api`. Other: the feature's own, e.g. `performs the Trade Amendment action via api` |
| Outcome (response) | lifecycle event: `Then the 'EarlyTermination' event is executed successfully`; `... is rejected with a validation error` (400) / `... with permission denied` (403) / `... as not found` (404) / `... with a conflict` (409). Other: `the '<subject>' is retrieved / created / updated / ... successfully` (200); `... is saved / published / re-published / imported successfully` (201); `the '<subject>' request is rejected with a validation error` (and the same other outcomes). The word follows the status the system answers, never a synonym |
| Outcome (state) | `maker retrieves the trade`, then `the trade basic status is '<status>'` / `the trade basic event status is '<status>'` |
| Outcome (trades the event created) | the event's own business sentence, e.g. `And the allocated trades carry the requested notional amounts`; the implementer builds it on `the new trades of the event have '<path>' as in the request's '<data key>'` |

- The response outcomes already check the status and compare the whole response with the case's recorded file: never
  add a step for a status code or a response field. A lifecycle event's success is recorded as the trade retrieved
  after it, so `is executed successfully` already proves the trade's new terms.
- Never invent a synonym of an outcome (`should be retrieved`, `is accepted`, `fails with a business rule violation`)
  or a domain step that only renames one: the subject in quotes is what varies, the outcome words do not. An outcome
  the vocabulary lacks is listed under "New steps" for the implementer, worded like its neighbours.
- The data and the call are two steps, so that the scenarios of one feature differ in their steps, not only in their
  titles. The event name is the `eventType` of the case data (`EarlyTermination`, `PartialNovationRemaining`); the word
  before `data` says what the case row holds: `valid`, `invalid` or `malformed` (the failure is caused by the input),
  `duplicate` (the same event again). When two scenarios of a feature would still read the same, `with '<what>'` says
  what sets the row apart (`valid data with 'a 3-way unequal split'`, `invalid data with 'an empty sub-trade list'`).
  The row itself, not the words, is what is sent.
- A forbidden state, a role or a duplicate also needs the precondition that creates it (`a dead ...`, an earlier
  `has valid data` + `performs` as `Given` / `And`); the data is then `valid`.
- Every `trigger-event` event uses these steps; an event's own `api/<feature>.snippet` holds only its preconditions and
  the evidence beyond the response.
- Evidence beyond the response is a business `Then` of its own, written only when the TP's evidence asks for it.

## UI scenarios

| Concern | Existing shape |
|---|---|
| Precondition | `Given a Live '<product_type>' trade exists in the blotter` (also `Draft`, `Dead`, `Draft Pending Approval`, `saved Draft`: never submitted, unlike `Draft`); provisions through the API, does not log in or navigate |
| Actor | `And maker is logged in to the trade portal` (`checker`, `admin`, `user`); a second login switches the actor |
| Business action | `When perform the Early Termination event on the '<product_type>' trade` |
| Visible outcome | `Then the early termination is completed successfully`, `And the trade is closed as Terminated`, `the trade has status 'Live' and event status 'New' in the blotter` |

- Role names appear only in login steps; later steps act as the last logged-in user.
- Outcome words: `completed successfully` (lifecycle event), `approved successfully` / `rejected successfully`
  (checker), `submitted successfully` (into pending approval), `request is rejected with an error` (negative).
- Assert the stable end state (blotter columns, detail page), never a toast or spinner.

## Actors

Only `maker`, `checker`, `admin`, `user`. One label per concept within a feature.

## Where the detail goes

| Detail | Home | Not in |
|---|---|---|
| Request values, IDs, amounts, dates, counterparty, dat file | case data `data/{api,ui}/<event>.json` (`defaults`, `byProduct`, a case row) | steps, `Examples` columns |
| Expected response | the recording `data/api/expected/<event>/<case ID>.json`, masked by `dynamicFields` rules | steps, comments |
| Data that prepares a precondition | `data/setup/<name>.json` | the case data |
| Endpoint, headers, HTTP verb | API snippets and configuration | feature files |
| Selector, page or component mechanics | element repository and page / component snippets | feature files, flows |
| Why a broad sentence was chosen, what the implementer must not miss | `# handoff:` (below) | the step text |

When a step starts to name fields, values or mechanics, compress it back to a business sentence and move the specifics
down: `Then the reason is created with the expected response` (+ recording), not one step per field.

## Comments above a scenario

Trace comments are required and permanent (standard §7): `# tp:`, `# validationTarget:`, `# observableEvidence:`,
`# testDataIntent:`.

`# handoff:` is optional and temporary: one short line for the implementer when the Gherkin alone leaves real ambiguity
about setup, the evidence to collect or reliability. It is removed once the automation runs.

- Use it for: the case data the scenario needs (`a row with an empty counterparty`), the evidence the outcome must
  include (`read the trade back; status alone is not enough`), a precondition no step provides yet, async behavior
  (`the blotter refreshes after the websocket event`), a new step with no reusable snippet.
- Not for: payloads, SQL, code, selectors, click sequences, anything durable (that is a plain comment or the story).
- Place it directly above the scenario's tags, or above the one step it concerns. Never a shared feature-level block.

```gherkin
  # tp: TP-002 | ac: AC-2
  # validationTarget: early termination of a dead trade is rejected
  # observableEvidence: rejection with reason (1)
  # testDataIntent: a dead trade; default termination data
  # handoff: the rejection message names the trade status; mask nothing in it
  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-EARLY-TERMINATION-FX_TRF-API-004] Early termination of a dead trade is rejected
    Given a dead 'FX_TRF' trade is created via api
    When maker has valid data for the 'EarlyTermination' event
    And performs the 'EarlyTermination' event via api
    Then the 'EarlyTermination' event is rejected with a validation error
```
