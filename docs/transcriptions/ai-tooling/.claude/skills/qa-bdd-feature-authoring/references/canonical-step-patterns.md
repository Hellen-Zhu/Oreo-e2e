<!-- Transcribed from photos; overlapping passages merged. Archival source text only. -->

# Canonical Step Patterns

Use this reference when deciding how API or UI/E2E scenarios should be shaped at the step level, and when choosing stable, reusable business wording.

## Purpose

This reference does **not** replace the core Gherkin content rules. Instead, it supplements them with layer-specific scenario-selection guidance and repeatable step wording patterns.

Use it to answer questions such as:

- what kind of API or UI/E2E behavior belongs in a scenario
- how to break a scenario into business-readable Given/When/Then steps
- how to express actor roles consistently
- which wording patterns should be preferred before inventing new phrasing

## API Scenario Selection Guidance

API scenarios should focus on business behavior at the service boundary while still using business language. Do not expose transport or contract details in Gherkin.

Prefer scenarios that prove one of these business outcomes:

- a valid request succeeds and persists the expected business state
- an invalid request is rejected with a business-meaningful error
- an unauthorized or forbidden action is blocked
- a status-transition rule is enforced
- a duplicate or idempotent action behaves correctly

When the exact response matters, the visible `Then` step may remain business-level while the exact request and response values are validated from YAML data. See `detail-sinking-and-validation-placement.md`.

## UI/E2E Scenario Selection Guidance

UI/E2E scenarios should focus on user journeys and visible outcomes. Do not expose locator, component, or implementation details in Gherkin.

Prefer scenarios that prove one of these business outcomes:

- the user can complete the critical workflow
- page state, status, and navigation reflect the business outcome
- role-specific actions are visible or blocked
- list/detail consistency is proven after a real action

When the precise setup or validation path is important but would make the scenario brittle, keep the steps business-visible and move the lower-level nuance into `# handoff:` or downstream automation data.

## API Step Patterns

For API scenarios, separate the business flow into these concerns:

- business precondition
- actor data readiness
- business action execution
- business outcome

This does not mean every scenario must use all four as separate steps. It means the step wording should make those concerns clear when they matter.

Common API step shapes:

- `Given a live trade exists`
- `When maker has valid data for Cancellation event`
- `And performs the cancellation via api`
- `Then the trade is cancelled successfully`

## UI/E2E Step Patterns

For UI/E2E scenarios, describe:

- the visible starting point
- the logged-in actor, when relevant
- the business action from a business area
- the visible business outcome

Common UI/E2E step shapes:

- `Given a draft trade is available to the maker`
- `And maker is logged in to the trade portal`
- `When the maker submits the trade for approval from the trade portal`
- `Then the trade is submitted successfully`
- `And the trade status is shown as Pending Approval`

## Canonical Step Wording Patterns

Use stable, business-readable step wording so similar scenarios are expressed consistently across stories and modules.

Use these patterns unless the approved test point clearly requires a different business expression.

### Role Usage Rules

**Only allowed actor names:** `maker`, `checker`, `admin`, `user`.

Use role labels such as `maker` and `checker` only in actor data-readiness steps or explicit login/setup preconditions.

Preferred role-usage locations:

- API actor data readiness:
  - `When maker has valid data for Cancellation event`
  - `When checker has invalid data for approval`
- UI login/setup preconditions:
  - `And maker is logged in to the trade portal`
  - `And checker is logged in to the trade portal`

Do not repeat the role label in later action or assertion steps when the acting role is already clear from the preceding readiness or login step. Only include the role label again when omitting it would create real ambiguity about who is acting.

### Wording Consistency Rules

- Use `has valid data for ...` and `has invalid data for ...` for data-validation-driven API scenarios.
- Do not use `valid/invalid data` wording for permission, duplicate, or invalid-state scenarios unless the failure is truly caused by input data.
- Prefer `performs the {event} via api` for API action execution steps.
- Prefer `performs {event} ... from the trade portal` or `attempts to perform {event} ...` for UI action steps.
- Use consistent actor labels across the feature file, for example `maker`, `checker`, or `user`, and do not switch terminology within the same feature unless the business meaning changes.
- Reuse approved wording patterns before inventing new phrasing.

## Authoring Reminder

Keep these patterns subordinate to the approved business scope. Do not invent extra validations or split one approved case into multiple scenarios unless revision instructions explicitly require it.
