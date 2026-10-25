<!-- Transcribed from photos; overlapping passages merged. Anything after the Quick Review Checklist was not supplied. Archival source text only. -->

# Handoff Annotation Reference

Use this reference when authoring or reviewing `# handoff:` comments in `.feature` files.

## Core Principle

`# handoff:` comments are **temporary implementation-bridging notes**.

They exist to help downstream automation implementation when business-readable Gherkin alone would leave important ambiguity about setup, validation focus, or execution reliability.

They are not permanent documentation. If a note has lasting value for business understanding or long-term scenario intent, keep it as a plain comment or move it to external documentation or story comments instead of labeling it as `# handoff:`.

## When To Add `# handoff:`

Add a handoff note only when it materially improves one or more of the following:

- automation accuracy
- automation reliability
- scenario traceability
- downstream implementation efficiency

Typical reasons to add a handoff note:

- the acceptance criteria imply hidden setup that the scenario should not spell out in business steps
- the key validation focus is easy to miss without a brief note
- the scenario relies on a specific data dependency, retrieval sequence, or async behavior
- the scenario uses new wording because no reusable snippet existed

If the scenario is already clear and implementable without extra explanation, do not add a handoff note.

## What `# handoff:` Is For

Use `# handoff:` for temporary details such as:

- request route or UI entry point hints needed for initial automation
- a reminder that exact request or response values are YAML-driven and intentionally omitted from the scenario text
- required pre-seeded data or lookup dependencies
- critical validation focus that automation should not miss
- whether a business-readable step should be backed by DB reconciliation, count validation, or preserved-state verification
- async propagation or refresh behavior that affects reliability
- special implementation notes caused by missing reusable snippets

## What `# handoff:` Is Not For

Do not use `# handoff:` for:

- durable business explanation that belongs in plain comments
- feature-level documentation that applies to every scenario
- Java glue, code snippets, SQL scripts, or large payload bodies
- low-level locator maps, click-by-click UI instructions, or page object detail unless absolutely necessary to prevent ambiguity
- repeated notes that have already been fully absorbed into snippets, YAML, glue, or stable test utilities

Prefer a short note such as `validate exact response from YAML` over pasting the response body into the handoff comment.

## Placement Rules

- `# handoff:` comments are scenario-scoped by default.
- Place the note directly above the `Scenario` or `Scenario Outline` it supports.
- If the note applies only to one step, place it immediately above that step.
- Do not combine multiple scenario-specific notes into one shared feature-level handoff block.

## API Handoff Guidance

For API scenarios, include only the minimum detail needed for reliable downstream automation.

Useful API handoff content may include:

- request route pattern, HTTP method, required headers, or important payload fields
- path/query parameters that matter to the scenario intent
- expected response validation focus at business level
- a reminder that exact request/response values are maintained in YAML rather than in step text
- required side-effect checks such as DB writes, audit records, or downstream notifications
- whether list size, filtered count, or preserved business state should be reconciled with DB facts
- duplicate, idempotency, or optimistic-lock expectations
- prerequisite seeded state, reference data, or entitlement conditions

For negative API scenarios, include when relevant:

- expected business error code or message family
- whether persistence must not occur
- whether prior business state must remain unchanged

For search or list API scenarios, add DB-comparison handoff notes only when the matching rule is stable and safely reproducible, for example exact status filters or `active=true`. If the matching logic is fuzzy, multi-field, or implementation-sensitive, use handoff to emphasize business-condition validation instead of forcing a brittle DB count.

## UI Handoff Guidance

For UI/E2E scenarios, include only the minimum detail needed for reliable downstream automation without making the feature brittle.

Useful UI handoff content may include:

- starting area such as blotter, detail page, dashboard, or task inbox
- navigation path only when it matters to the business flow
- critical dialogs, drawers, tabs, or confirmation steps
- mandatory or conditionally required user inputs
- stable locator hints only when they materially reduce ambiguity
- async waiting concerns such as grid refresh, websocket update, or delayed status propagation
- business-visible consistency checks across pages or components

## Example Handoff Shapes

```
# handoff: exact request and response values are YAML-driven; validate the full response contract from test data.
```

```
# handoff: reconcile returned active-marketer count with DB records where the active business flag is true.
```

```
# handoff: validate that returned rows satisfy the keyword match rule; add DB count comparison only if the search rule is exact and stable.
```

For full scenario-pattern examples, see `detail-sinking-and-validation-placement.md`.

## Quick Review Checklist

Before finalizing feature content or before commit, verify that:

- `# handoff:` is present only when necessary
- each handoff note is temporary and implementation-bridging in nature
- durable notes are not labeled as handoff
- handoff notes are scoped to the correct scenario or step
- stale handoff notes are removed after automation is validated
