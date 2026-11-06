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
| Business action (lifecycle event) | `When maker triggers the 'EarlyTermination' event via api`: the case's own request, whatever its data (valid, malformed, ...) |
| Business action (other) | the feature's own step, e.g. `When maker has valid data for Trade Amendment`, `And performs the Trade Amendment action via api` |
| Outcome (response) | lifecycle event: `Then the 'EarlyTermination' event is executed successfully`; `... is rejected with a validation error` / `... a business rule violation`. Other: `the '<object>' should be '<action>' successfully`; `failed to '<action>' '<object>'`; `failed to '<action>' '<object>' with status '<status>'` |
| Outcome (state) | `maker retrieves the trade`, then `the trade basic status is '<status>'` / `the trade basic event status is '<status>'` |

- The response outcomes already check the status and compare the whole response with the case's recorded file: never
  add a step for a status code or a response field.
- The event name is the `eventType` of the case data (`EarlyTermination`, `PartialNovationRemaining`). What makes the
  data valid or malformed is said by the scenario title and lives in the case row, not in the step. A forbidden state, a
  role or a duplicate comes from the precondition that creates it (`a dead ...`, an earlier `triggers` as a `Given`).
- Events not moved to `api/trade_lifecycle_event.snippet` yet still have their own `maker has ... data for X event` /
  `performs the X event via api` steps; a scenario appended to such a feature keeps the feature's existing wording.
- Evidence beyond the response is a business `Then` of its own, written only when the TP's evidence asks for it.

## UI scenarios

| Concern | Existing shape |
|---|---|
| Precondition | `Given a Live '<product_type>' trade exists in the blotter` (also `Draft`, `Dead`, `Draft Pending Approval`); provisions through the API, does not log in or navigate |
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
    When maker triggers the 'EarlyTermination' event via api
    Then the 'EarlyTermination' event is rejected with a business rule violation
```
