<!-- Transcribed from IMG_6474–IMG_6477; overlapping passages merged. Company package prefix and example counterparty name anonymized. Archival source text only. -->

# API Implementation Mechanics

Use this reference when the implementation path is already understood and you need concrete contract, YAML, assertion, and Java-glue rules. When API automation is being created or revised, real API verification is a required precursor, not an optional extra.

## Contract Discovery

Use this priority order when determining the intended API contract:

1. Read the scenario handoff detail first when it is present, but treat handoff as intent discovery rather than proof of current runtime behavior.
2. Read `rest.base.url` from `{E2E_DIR}/src/test/resources/genie.properties` and verify the runtime contract at `{rest.base.url}/v3/api-docs` when reachable.
3. Where safe and practical, confirm real API behavior from the running target for method, resolved path, required headers/auth, required request fields, expected success status, and representative response-envelope or business-field shape.
4. Use existing project snippets, existing API glue, and the feature's existing naming family only as supporting evidence after runtime verification, not as the primary source of truth.
5. If the intended endpoint, request fields, or assertions still cannot be determined with confidence, stop for manual review and clearly list the missing details that must be provided.

Validation gate:

- if the intended endpoint or action cannot be verified from runtime contract evidence and available real API behavior, stop and surface the ambiguity
- if required fields in the contract are missing from the scenario data intent, list the gaps before writing YAML
- if real API verification cannot be completed, do not silently fall back to existing automation patterns and continue anyway
- if manual review is required, provide the exact missing request or response details that need confirmation

Minimum verification evidence to capture before editing assets:

- environment used for verification, including `rest.base.url` or the active properties file
- resolved method and path
- required headers, auth expectations, and request body/query essentials relevant to the scenario
- observed success status and response-envelope shape
- observed error status or validation behavior when the scenario depends on failure handling

Do not finalize request or response assertions purely from existing automation families.

## YAML Data Rules

### Required Structure

Define `request:` and `response:` only when the scenario actually uses them. If a scenario needs only input data, keep only `request:`. If it needs only expected response data, keep only `response:`. If it needs both, define both.

When present, `request:` and `response:` should hold JSON text using YAML block-scalar style (`|`). Do not rewrite these payloads as nested YAML maps.

```yaml
TC-REASON-MANAGEMENT-API-001:
  request: |
    {
      "type": "Early Termination",
      "subType": "Client",
      "reasonName": "TC-REASON-CREATE Automation",
      "description": "Created by YAML test data",
      "active": true
    }
  response: |
    {
      "code": 200,
      "status": "SUCCESS",
      "msg": "${json-unit.regex}.*",
      "data": {
        "id": "${json-unit.any-string}",
        "type": "Early Termination",
        "subType": "Client",
        "reasonName": "TC-REASON-CREATE Automation",
        "description": "Created by YAML test data",
        "active": true
      }
    }

TC-REASON-MANAGEMENT-API-002:
  request: |
    {
      "type": "Early Termination"
    }
```

### Key Rules

- YAML key = bare TC-ID matching the `@TC-xxx` scenario tag exactly
- key must include the `-API-` infix, otherwise same-module API and UI keys collide at startup
- unique string fields should embed the TC-ID when repeated reruns could otherwise collide
- provisioning and setup keys use `API-SETUP-{MODULE}-{ACTION}`

### Response Validation Rules

- Do not treat `code` and `status` as sufficient by themselves.
- Every API scenario should validate the expected response schema and the core business fields relevant to that scenario.
- Use exact literals for stable fields that should not vary across runs.
- Use `${json-unit.ignore}` or `${json-unit.regex}...` only for genuinely dynamic values such as generated IDs, timestamps, variable message text, or other runtime-generated content.
- If a field is contractually significant and stable, assert it explicitly instead of ignoring it.

### json-unit Placeholders

| Field type | Use |
|---|---|
| Server-generated UUID / timestamp | `"${json-unit.any-string}"` |
| Dynamic numeric count or ID | `"${json-unit.any-number}"` |
| Dynamic string with predictable pattern | `"${json-unit.regex}<pattern>"` |
| Error `msg` (variable wording) | `"${json-unit.ignore}"` |
| Envelope `code`, `status` | exact literal |
| Fixed enum or echoed request field | exact literal |
| Complex nested object not under test | `"${json-unit.ignore}"` |

Examples:

- `"msg": "${json-unit.regex}Submitted for checker approval\\. TaskId: .*"`
- `"msg": "${json-unit.regex}.*status 'Dead' does not permit 'Cancellation'.*"`
- `"id": "${json-unit.any-string}"`

Never use `${json-unit.ignore}` for the entire `data` node. Always assert the expected response schema shape, `code`, `status`, and the core business fields relevant to the scenario.

### ctx Injection

`ScenarioLifecycleHooks @Before(order=1)` parses the JSON body under `request:` and auto-flattens its fields into ctx before step 1 runs.

```text
YAML request JSON: { "basic": { "counterpartyName": "Example Counterparty", "portfolioId": "P001" } }
-> ctx["basic.counterpartyName"] = "Example Counterparty"
-> ctx["basic.portfolioId"] = "P001"

YAML request JSON: { "subTrades": [{ "portfolioId": "P1" }] }
-> ctx["subTrades[0].portfolioId"] = "P1"
```

If no `request:` block is defined for a scenario, nothing is injected for that scenario.

## Java Glue Rules

Use Java only when the implementation path has already escalated to `NEW_STEP`.

### File Placement

Place new or updated glue in:

`{E2E_DIR}/src/main/java/com/example/module/oreo/glue/api/<Domain>ApiSteps.java`

Prefer extending an existing domain-aligned step class before creating a new file.

### Coding Rules

- use the project's existing request/response helper model and import style (`cucumber.api...` for Cucumber 4.x)
- do not introduce RestAssured, OkHttp, WebClient, `HttpClient`, or other custom HTTP clients in project glue
- do not introduce `Thread.sleep`
- do not hardcode credentials or base URLs
- pair meaningful `ctx.set(...)` calls with `ctx.write(...)` for report visibility
- keep business payload data in YAML instead of Java string constants when YAML already owns that input

## Hard Stops

| Rule | Consequence |
|---|---|
| Missing `-API-` infix in key | Same-module API and UI keys collide at startup |
| Duplicate YAML key across files | `IllegalStateException` at startup |
| `datFile:` node in YAML | Dat paths belong in `ProductDatFiles` enum only |
| Response validation asserts only `code` and `status` | Contract regressions can pass undetected |
| `${json-unit.ignore}` on the entire `data` node | Business regressions silently pass |
| `${json-unit.ignore}` used for stable contract fields | Important schema or business regressions are masked |
| Inline field values remain in feature file steps | Data contract drifts away from YAML |
| Custom HTTP client introduced in glue | Breaks project abstraction and consistency |
| `Thread.sleep` introduced | Adds brittle, non-diagnostic timing behavior |
