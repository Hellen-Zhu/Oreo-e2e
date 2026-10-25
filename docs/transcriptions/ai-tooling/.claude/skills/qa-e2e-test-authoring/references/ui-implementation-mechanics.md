<!-- Transcribed from IMG_6434–IMG_6439; overlapping passages merged. Company package prefix and sample business identifiers anonymized. Archival source text only. -->

# UI Implementation Mechanics

Use this reference when the implementation path is already understood and you need concrete locator, YAML data, wait, and Java-glue rules. When UI mechanics, snippets, or locators are being created or revised, browser discovery is a required precursor, not an optional extra.

## Locator Repository Rules

Locators are stored in:

- `src/test/resources/elements/pages/*.json`
- `src/test/resources/elements/components/*.json`

Use one file per page/component domain.

### JSON Schema

Use this exact structure:

```json
{
  "name": "trade_portal.new_trade_btn",
  "lookupDetails": {
    "findBy": "testId",
    "value": "trades-new-trade-btn"
  }
}
```

- `name` format: `page_or_component.element_name`
- `lookupDetails.findBy`: typically `testId`, fallback `locator`
- `lookupDetails.value`: selector value

Do not use an alternate schema such as `strategy/value`.

### File Placement

Decision rule:

- has its own URL/route -> `elements/pages/<page>.json`
- overlay/dialog/component without route -> `elements/components/<component>.json`

Examples:

- `trade_portal_page.json` (page)
- `new_trade_page.json` (page)
- `dynamic_action_dialog.json` (component)
- `toast.json` (component)

### Selector Priority

1. `findBy: "testId"`
2. `findBy: "locator"`

Prefer stable test IDs. If no testId exists, use a resilient locator expression.

### Java Locator Access Rule

Top-level element access in glue should use element repo keys:

```java
ctx.service().getLocator("trade_portal.new_trade_btn")
```

Avoid top-level direct `getPage().getByTestId(...)` calls in step methods.

### Browser Discovery Workflow

Use Playwright MCP browser tooling before changing snippet mechanics, locator JSON, or UI Java glue.

1. Read `playwright.base.url` from `genie.properties`.
2. Navigate to the impacted route, dialog, or component flow.
3. Interact far enough to reproduce the actual UI state that the scenario depends on.
4. Use browser snapshots to inspect the accessibility tree and confirm visible controls, labels, roles, and state transitions.
5. Prefer stable `data-testid` values first; if none exist, derive a resilient locator from the rendered DOM.
6. Record which route, dialog, or conditional state was checked so the later snippet placement and locator choices are traceable.
7. Only then add or update JSON entries with `name + lookupDetails` and revise snippets or Java if required.

Typical Playwright MCP sequence:

- `browser_navigate` to the target route
- `browser_click` / `browser_type` / `browser_select_option` to reach the needed state
- `browser_wait_for` to let semantic UI transitions complete
- `browser_snapshot` to confirm the rendered state before writing assets

Do not create or revise snippet mechanics solely from existing JSON, legacy snippet text, or feature wording.

## YAML Data Rules

### Required Structure

Define `request:` and `response:` only when the scenario actually uses them. If a scenario needs only input data, keep only `request:`. If it needs only expected response data, keep only `response:`. If it needs both, define both.

When present, `request:` and `response:` should hold JSON text using YAML block-scalar style (`|`). Do not rewrite these payloads as nested YAML maps.

```yaml
TC-TRADE-CREATION-UI-002:
  request: |
    {
      "basic": {
        "portfolioId": "EXAMPLE_PORTFOLIO",
        "counterpartyFmId": "100000001",
        "counterpartyName": "Example Counterparty"
      }
    }

TC-TRADE-CREATION-UI-003:
  response: |
    {
      "status": 200,
      "code": "SUCCESS"
    }

TC-TRADE-CREATION-UI-004:
  request: |
    {
      "basic": {
        "portfolioId": "EXAMPLE_PORTFOLIO"
      }
    }
  response: |
    {
      "status": 200
    }
```

### Key Rules

- YAML key = bare TC-ID matching the `@TC-xxx` scenario tag exactly
- key must include the `-API-` or `-UI-` infix, otherwise same-module keys collide at startup
- unique string fields should embed the TC-ID when repeated reruns could otherwise collide

### Field Value Conventions

| Situation | Value |
|---|---|
| Server-generated string (ID, timestamp) | `"${json-unit.any-string}"` |
| Server-generated number | `"${json-unit.any-number}"` |
| Error `msg` (variable wording) | `"${json-unit.ignore}"` |
| Envelope `code`, `status` | exact literals |
| Inline field values in feature steps | never — move them to YAML |

### ctx Injection

`ScenarioLifecycleHooks @Before(order=1)` parses the JSON body under `request:` and auto-flattens its fields into ctx before step 1 runs.

If no `request:` block is defined for a scenario, nothing is injected for that scenario.

```text
YAML request JSON: { "basic": { "counterpartyName": "Example Counterparty" } }
-> ctx["basic.counterpartyName"] = "Example Counterparty"

YAML request JSON: { "subTrades": [{ "portfolioId": "P1" }] }
-> ctx["subTrades[0].portfolioId"] = "P1"
```

Access in a snippet with `from stored variable 'basic.counterpartyName'`.

## Wait Strategy

The single most common source of E2E flake is wait misuse.

### Core Rule

Never add `Thread.sleep` or `waitForTimeout`. Always wait on a semantic UI or network condition.

### Why Use `CommonE2EAssertion`

Project class: `src/main/java/com/example/module/oreo/glue/ui/CommonE2EAssertion.java`

Preferred steps:

```gherkin
Then assert that 'toast.success' is eventually visible
And assert that 'product_edit.dialog_panel' is eventually hidden
And assert that 'product_edit.save_btn' is eventually enabled
And assert that 'product_edit.product_id_input' is eventually disabled
```

<!-- Source boundary: IMG_6437 cuts off below these steps; IMG_6438 resumes at Decision Table. Any intervening content is not visible in the supplied photographs. -->

### Decision Table

| Situation | Preferred approach | Avoid |
|---|---|---|
| Wait for element to appear | `assert that 'x' is eventually visible` | `Thread.sleep` + immediate assert |
| Wait for element to disappear | `assert that 'x' is eventually hidden` | manual polling loops |
| Wait for enable/disable transition | `...eventually enabled/disabled` | ad-hoc Java waits |
| Wait for backend-triggered UI refresh | `clickAndWaitForResponse(...)` helper in glue | click then blind timeout |
| Wait for navigation completion | URL assertion + eventual assertions | `waitForTimeout` |

## Java Glue Rules

Use Java only when the implementation path has already escalated to `NEW_STEP`.

### File Placement

Place new or updated glue in:

`{E2E_DIR}/src/main/java/com/example/module/oreo/glue/ui/<Domain>Steps.java`

Prefer extending an existing domain-aligned step class before creating a new file.

### Coding Rules

- use `ctx.service().getLocator("key")` for top-level locator access
- use `ctx.getLocatorWithParam(...)` when a parameterized locator is already supported by project utilities
- keep each step method single-responsibility
- use framework wait helpers and `CommonE2EAssertion` instead of custom sleep loops or ad-hoc polling
- keep selector ownership in locator JSON, not in Java string literals

### Shape Constraints

Glue classes should remain simple:

- plain POJO
- no `@Component`
- no step-class inheritance

### Preferred Wait Helpers

When a Java step must wait on server-driven UI behavior, prefer helpers already used by the project, such as:

- `ctx.clickAndWaitForResponse(locatorKey, urlFragment, method)`
- `ctx.doubleClickAndWaitForResponse(...)`
- `ctx.refreshAndWaitForResponse(...)`

## Hard Stops

| Rule | Consequence |
|---|---|
| Hardcoded top-level selectors in Java | Breaks central locator ownership |
| `Thread.sleep` or `waitForTimeout` style synchronization | Adds avoidable flakiness |
| Wrong JSON schema (`strategy/value`) | Not compatible with project loader |
| `featureTag` does not match the YAML stem | `TestDataLoader` returns null and scenarios fail at startup |
| Duplicate YAML key across files | `IllegalStateException` at startup |
| `datFile:` node appears in YAML | Dat paths belong in `ProductDatFiles` enum only |
| Feature file contains locator keys | Leaks implementation details into the business spec |
| Inline business values remain in feature steps instead of YAML | Data contract drifts away from runtime input source |
| New Java step that only wraps a click, type, or visible assertion | Should have been handled by snippet composition or built-ins |
| Creating a new glue class when an existing domain class is the natural home | Fragments step ownership |
