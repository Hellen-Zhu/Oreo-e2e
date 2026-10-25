<!-- Transcribed from photos; overlapping passages merged. Archival source text only. -->

# Detail-Sinking And Validation Placement

Use this reference when deciding how much detail belongs in Gherkin versus YAML test data, downstream automation assets, or scenario-scoped `# handoff:` comments.

## Core Principle

Keep the `.feature` file at business-behavior level. If a detail helps automation more than business review, prefer to sink it below the scenario text.

The goal is not to remove precision. The goal is to place precision in the most maintainable layer:

- business intent in Gherkin
- exact data in YAML or downstream automation data
- short implementation-bridging guidance in `# handoff:`

## What Stays In Gherkin

Keep only the behavior that product, QA, and engineering should review directly:

- the business precondition or actor state
- the business action
- the business-visible outcome or rule being proven
- small business-readable examples when `Scenario Outline` is genuinely the clearest form

## What Moves Out Of Gherkin

Use the following sink strategy:

| Detail type | Preferred home | Notes |
|---|---|---|
| exact request values, IDs, keywords, payload bodies | YAML test data / downstream automation data | Do not hard-code exact values into scenario steps when the value is test data rather than business wording. |
| exact expected response body or response schema focus | YAML test data first; `# handoff:` only if a short validation-focus note is needed | Keep full response examples out of Gherkin. |
| hidden setup, seeded dependencies, transport hints, async caveats | `# handoff:` | Keep concise and scenario-scoped. |
| DB compare rule, count-check rationale, preserved-state rule | `# handoff:` | Mention what business fact must be compared, not raw SQL unless absolutely necessary. |
| locator/UI mechanics, Java/glue detail, large payload blobs, full SQL scripts | downstream automation assets, not Gherkin and not handoff unless a tiny hint is essential | Avoid brittle implementation leakage. |

## YAML vs `# handoff:` Decision Rule

Use YAML when the detail is **data**.

- exact request body fields
- exact response values
- seeded IDs, keywords, names, statuses
- canonical example payloads used for precise validation

Use `# handoff:` when the detail is **implementation-bridging guidance**.

- what downstream validation must not miss
- whether a list result should be compared with DB facts
- what hidden setup or reference lookup exists
- why a scenario uses a broad business sentence instead of exposing schema detail

If the note became a full payload, long SQL, or code fragment, it does **not** belong in `# handoff:`. Keep the payload in YAML and summarize the intent in one short handoff note if needed.

## Business-Step Compression Rule

When a scenario starts to expose response-field detail, request-field detail, or technical retrieval mechanics in the steps themselves, compress it back to a business sentence and move the supporting specifics down.

Prefer transformations like:

- `Then the marketer record is returned with the expected response` + YAML exact response
- `Then all active marketers are returned with a total count of <count>` + handoff note that the count should reconcile with active records in DB
- `Then the returned marketers satisfy the keyword search criteria` + handoff note describing the keyword-match focus and whether DB comparison is expected

Do not keep field-by-field value assertions in Gherkin when the same intent can be expressed more clearly as a business sentence backed by YAML data.

## API Response Validation Placement

For API scenarios, it is acceptable for the visible `Then` step to stay business-level while the exact response contract is validated from YAML data and a short `# handoff:` note clarifies the validation focus.

Prefer this shape when:

- the request and response values are stable test data
- exact values matter for correctness
- hard-coding values in steps would reduce reuse or maintainability

## UI/E2E Validation Placement

For UI/E2E scenarios, keep the visible steps focused on user journey, business state, and visible outcome. Move page-entry nuance, pre-seeded data detail, cross-page consistency checks, and async reliability hints below the scenario text.

Prefer this shape when:

- the journey should remain stable even if page structure or component composition changes
- lower-level setup is required but would make the scenario read like an automation script
- validation needs precise downstream checks such as grid/detail consistency, toast/status confirmation, or data seeded through API setup

Typical placement for UI/E2E detail:

- exact seeded trade or reference-data setup -> YAML test data or downstream automation data
- precise visible validation focus -> short `# handoff:` note
- navigation path or entry-area nuance -> `# handoff:` only when the business starting point would otherwise be ambiguous
- click-by-click mechanics, locators, or component structure -> downstream automation assets, not Gherkin

## Search / List Scenario Guidance

Search and list scenarios often need one of two validation shapes. Choose deliberately:

### 1. Business-condition validation only

Use when the matching logic is broad, fuzzy, multi-field, or not safely reproducible outside the service.

Example outcome style:

- `Then the returned marketers satisfy the keyword search criteria`

### 2. Business-condition validation plus DB count reconciliation

Use when the rule is stable, explicit, and straightforward to reproduce, such as:

- `active = true`
- exact-status filters
- clearly defined unique-key lookups

Keep the business sentence in Gherkin and record the reconciliation focus in `# handoff:`.

Do **not** force DB-count comparison for every search/list scenario. Use it only when the business rule is clear enough that the compare will be stable and meaningful.

For UI/E2E list scenarios, the equivalent decision is whether to prove:

1. visible business-condition matching only, or
2. visible matching plus cross-page or data-source consistency.

Use visible matching only when the UI search/filter behavior is broad or implementation-sensitive. Add stronger consistency checks only when the expected result set and comparison basis are stable enough to keep the scenario maintainable.

## Practical Example Patterns

### Pattern A — broad business assertion + YAML exact response

```gherkin
Scenario: [TC-MARKETER-MANAGEMENT-API-002] An existing marketer can be retrieved by id
  When retrieves the marketer by an existing id
  Then the marketer record is returned with the expected response
```

Supporting placement:

- request/response details in YAML
- optional handoff note: exact request and response are YAML-driven

### Pattern B — business count assertion + DB reconciliation

```gherkin
Scenario Outline: [TC-MARKETER-MANAGEMENT-API-001] Active marketers can be retrieved successfully
  When retrieves all active marketers
  Then all active marketers are returned with a total count of <count>
```

Supporting placement:

- response envelope contract in YAML
- count source in examples / downstream data
- handoff note that returned count should reconcile with DB `active=true` facts

### Pattern C — search semantics without brittle over-specification

```gherkin
Scenario: [TC-MARKETER-MANAGEMENT-API-004] Marketer search returns results that match the keyword criteria
  When searches marketers by keyword
  Then the marketer search results keep the current matching semantics
  And the returned marketers satisfy the keyword search criteria
```

Supporting placement:

- keyword and exact expected sample data in YAML
- handoff note clarifying whether DB comparison is required or intentionally omitted

### Pattern D — UI/E2E workflow outcome without leaking UI mechanics

```gherkin
Scenario: [TC-TRADE-LIFECYCLE-UI-001] A maker can submit a draft trade for approval
  Given a draft trade is available to the maker
  When the maker submits the trade for approval from the trade portal
  Then the trade is submitted successfully
  And the trade status is shown as Pending Approval
```

Supporting placement:

- seeded trade details in YAML or downstream setup data
- handoff note for starting area, hidden setup route, or async refresh risk if needed
- no click-by-click portal mechanics in the scenario text

### Pattern E — UI/E2E list-detail consistency with focused handoff

```gherkin
Scenario: [TC-TRADE-PORTAL-UI-002] Search results remain consistent with the selected trade details
  Given live trades are available in the trade portal
  When the user searches for a trade by reference
  Then the search results satisfy the reference criteria
  And the selected trade details match the search result
```

Supporting placement:

- exact search reference and expected trade data in YAML
- handoff note for whether detail consistency should be checked in grid + details panel, grid + portal page, or another visible pairing
- optional note for delayed grid refresh or portal propagation if reliability depends on it
