# BDD Feature Implementation Standards

Implementation rules for Example E2E naming, JSON data, snippet composition, element repositories, Java glue, and verification. Use [test layering](test-layering-standards.md) for approved validation targets and [feature generation](bdd-feature-generation-standards.md) for business Gherkin and traceability.

Repository alignment checked on 2026-10-23 against the current workspace, including uncommitted changes. Runtime facts below come from source/configuration, not stale comments. Authoring rules apply to new or changed behavior; existing legacy patterns are not automatically approved for expansion.

## Current Baseline

| Concern | Current implementation / authority |
|---|---|
| Build/runtime | Maven, Java 21; `pom.xml` |
| Framework | Genie `5.0.0-RC7-SNAPSHOT`, genie-playwright `1.0.0-SNAPSHOT`; `pom.xml` |
| Cucumber | Legacy `cucumber.api` with JUnit/Genie; Cucumber version managed by Genie |
| Execution layers | `@api` uses genie-rest without a browser; `@playwright` uses genie-playwright |
| Browser configuration | `genie.properties` configures Microsoft Edge; inspect the active environment properties, not just this file |
| Runner | `src/test/java/com/example/module/oreo/OreoTest.java`; currently excludes `@wip` and selects `@ADO#trade_creation`, with no fixed `@api` tag |
| Active runner properties | `@GenieOptions(properties = "src/test/resources/qa.properties")` in the current runner |
| Data | `TestDataRepository`, `CaseId`, `ProductDatFiles`, `ExpectedResponses` in `com.example.module.oreo.testdata` |

The current runner selects trade creation across eligible API/UI scenarios, **not the full suite**. Runner/configuration can change independently of these docs: inspect the actual declaration before interpreting selection or an empty run. Some `CLAUDE.md` descriptions and source comments still refer to older runner/data/Surefire behavior; source/configuration wins for those facts.

## 1. Naming Conventions

### 1.1 Event Binding, Not Three-Stem Binding

The event and channel in the case ID determine the case-data file:

```text
[TC-TRADE-CREATION-FX_TRF-API-001] in the Scenario name
  → channel: api; event: trade_creation; product: FX_TRF
  → data/api/trade_creation.json
  → data/api/expected/trade_creation/TC-TRADE-CREATION-FX_TRF-API-001.json
```

- Feature filename stem must agree with the event for offline feature/data validation.
- Business/module tags select coverage; they are not loader keys. `@ADO#trade_lifecycle` can classify multiple lifecycle events.
- Snippets bind by public step text, not by matching filenames. Reuse common/domain snippets even when their stem differs from the feature.
- Existing example: `user_preference_config.feature` uses `flows/user_preference.snippet`; trade approval/rejection reuses common trade actions and other flow/page snippets.

### 1.2 `featureName` / `featureTag` / `featureModule`

| Name | Rule | Example |
|---|---|---|
| `featureName` | `snake_case`: `{primary_object}_{primary_action}` | `trade_creation`, `trade_cancellation` |
| `featureTag` | bare business stem; emit `@ADO#{featureTag}` | `trade_creation` → `@ADO#trade_creation` |
| `featureModule` | uppercase event business stem, `_` → `-`; exclude `@` and `ADO#` | `TRADE-CREATION` |

Preserve existing broad selection tags; do not derive every event's ID prefix from a broad tag such as `trade_lifecycle`.

### 1.3 File Names

| Artifact | Convention | Example |
|---|---|---|
| Feature file | `snake_case.feature` | `trade_allocation.feature` |
| Case data | `{event}.json` | `data/api/trade_allocation.json` |
| Optional shared data | channel-local `common.json` containing `shared` blocks | `data/api/common.json` |
| Setup data | `{setup-name}.json`, lowercase letters/digits/hyphens | `data/setup/trade-create-stepin-full.json` |
| API expected response | `{case-id}.json` under its event directory | `data/api/expected/trade_creation/TC-TRADE-CREATION-FX_TRF-API-001.json` |
| Flow/API snippet | `snake_case.snippet`, focused business scope; may be shared | `common_trade_action.snippet` |
| Page snippet file | `snake_case.snippet` (page-oriented stem) | `trade_portal_page.snippet` |
| Component snippet file | `snake_case.snippet` (component-oriented stem) | `allocation_dialog_component.snippet` |
| Element JSON (page) | `snake_case.json` (has URL) | `login_page.json` |
| Element JSON (component) | `snake_case.json` (no URL) | `allocation_dialog.json` |

### 1.4 TC-ID Format

| Layer | Format | Example |
|---|---|---|
| API | `TC-{FEATURE_MODULE}[-{PRODUCT}]-API-{NNN}` | `TC-TRADE-CREATION-FX_TRF-API-001` |
| UI | `TC-{FEATURE_MODULE}[-{PRODUCT}]-UI-{NNN}` | `TC-TRADE-CREATION-FX_TRF-UI-001` |

- `-API-` / `-UI-` is mandatory. Product-independent cases omit `{PRODUCT}`, e.g. `TC-REASON-MANAGEMENT-API-001`.
- `CaseId.fromScenarioName` extracts `[TC-...]` from the expanded Scenario name, not a Cucumber tag. Never add plain `@TC-...` tags; select individual cases using `--name`.
- Product outlines may use `<product_type>` in the name and row ID. Numbered outlines may use `<case>` in the name, but their JSON rows contain concrete expanded numeric IDs.
- Use three-digit sequences for new cases; the parser also accepts existing numeric sequences of other lengths.

### 1.5 Case Rows and Setup Names

- Every entry in `cases` has a resolvable, unique `id`. One concrete case may match at most one row: a product-template row plus a concrete row for the same case is invalid.
- Put product differences in `byProduct`, not duplicate matching rows. Search both API and UI data before assigning IDs or setup names.
- A case can resolve from event defaults without a row; a missing event file yields empty runtime case data. This is not permission to omit required request/expected data or ignore validation warnings.
- Setup names are filename stems such as `trade-create-stepin-full`, not `API-SETUP-*` / `UI-SETUP-*` keys. Reuse an existing setup before adding another.

### 1.6 Snippet Step Names (Business Intent)

```text
# ✅ Business intent
approves the pending trade
# ❌ Implementation detail
checker logs in and approves the pending trade
```

Rules:

- For new UI language, role names belong only in login steps; non-login action/assertion steps are role-agnostic.
- API actor/permission steps may name the actor. Existing UI provisioning/trigger wording may also do so; preserve required public contracts without introducing aliases or expanding that legacy pattern.
- Flow `@Then` names must express business outcomes, not notification mechanics. Preferred outcome wording:
- `completed successfully` for lifecycle/business event completion.
- `approved successfully` / `rejected successfully` for checker decisions.
- `submitted successfully` only for explicit submission into pending approval.
- `request is rejected with an error` for negative execution outcomes.

### 1.7 Java Step Class Names

| Layer | Convention | Example |
|---|---|---|
| UI glue | `<Domain>Steps` | `AllocationSteps`, `TradePortalSteps` |
| API glue | `<Domain>ApiSteps` | `AllocationApiSteps`, `TradeApiSteps` |
| Framework (UI) | `PlaywrightServiceResolver` | reuse resolver; `from(scenario)` creates a context bound to the current scenario |
| Framework (API) | `ApiRequestSteps`, `ApiResponseAssertionSteps`, `RestServiceResolver` | reuse |

### 1.8 Element Locator Names

Format: `<page_or_component>.<element>` using snake_case on both sides.

```json
[
  { "name": "trade_portal.search_input" },
  { "name": "login.submit" },
  { "name": "allocation_dialog.confirm_btn" }
]
```

### 1.9 `ctx` Runtime Keys

- Key format: `camelCase` (e.g., `createdTradeId`, `createdTaskId`).
- For new reusable glue, pass business context key names as step parameters rather than hardcoding them.
- Report useful non-sensitive stored values with `ctx.write()` after `ctx.set()`. Never log credentials, tokens, or sensitive payloads to satisfy a reporting rule.
- Existing provisioning defaults such as `createdTradeId` / `createdTaskId` and framework `_tc.*` keys are established contracts, not a reason to duplicate glue.

## 2. File Layout

```text
src/
├── main/
│   ├── resources/snippet/
│   │   ├── api/*.snippet
│   │   └── ui/
│   │       ├── flows/*.snippet
│   │       ├── pages/*.snippet
│   │       └── components/*.snippet
│   └── java/com/example/module/oreo/
│       ├── glue/
│       │   ├── hooks/
│       │   ├── api/
│       │   └── ui/
│       └── testdata/                         # not glue/testdata
└── test/resources/
    ├── features/{layer}/{businessDomain}/{featureName}.feature
    ├── data/
    │   ├── api/{event}.json
    │   ├── api/expected/{event}/{case-id}.json
    │   ├── ui/{event}.json
    │   ├── setup/{setup-name}.json
    │   └── dat/{PRODUCT}/*.dat
    └── elements/
        ├── pages/{page_name}.json
        └── components/{component_name}.json
```

Here `{layer}` is `api` or `ui` (not `playwright`). Actual feature domains are listed in the generation standard. Do not infer a new domain solely from the artifact type.

## 3. Snippet Rules

### 3.1 Reuse Before Adding Code

Use this order:

1. Reuse an existing flow, page, component, or API snippet.
2. Check Genie built-ins in [REST catalog](../genie_rest_glue_catalog.md) and [Playwright catalog](../genie_playwright_glue_catalog.md).
3. Reuse existing focused Java glue/infrastructure in the appropriate API/UI package.
4. Add or extend a focused reusable snippet.
5. Only add Java when built-ins, snippets, the element repository, and existing glue cannot express the requirement.

### 3.2 Placement Decision Rule

```text
1) Page-level atomic op/assertion?
   Yes → snippet/ui/pages/<page_name>_page.snippet
2) Reusable cross-page component behavior?
   Yes → snippet/ui/components/<component_name>_component.snippet
3) Business workflow action/assertion used by scenarios?
   Yes → snippet/ui/flows/<featureName>.snippet
4) API behavior?
   Yes → snippet/api/<featureName>.snippet (or existing common API snippet)
Else → re-evaluate Java glue under hard-stop constraints
```

| Step type | Where |
|---|---|
| Page click/type/select/visible/hidden assertion | `snippet/ui/pages/*.snippet` |
| Dynamic action dialog/toast/confirmation behavior | `snippet/ui/components/*.snippet` |
| Cancellation/PartialTermination/PortfolioReassignment business flow | `snippet/ui/flows/*.snippet` |
| API create/approve/provision flow | `snippet/api/*.snippet` |

### 3.3 UI Boundary Rules

- Scenario layer calls `snippet/ui/flows/*.snippet` only.
- Flow snippets orchestrate business actions/assertions; UI mechanics delegated to page/component snippets.
- Flow snippets must not expose locator keys or primitive UI operations (`click` / `type` / `fill` / `select` /visible/hidden assertions).
- Page/component snippets own locator-level details and UI mechanics.
- Page/component snippets must not call flow snippets.
- A `@playwright` scenario must not call `snippet/api/`. UI API-based setup goes through the dedicated UI provisioning flow/glue.

### 3.4 Phase Intent and Call Graph

- `Given` establishes a precondition, `When` performs the business action, `Then` verifies observable evidence.
- For new snippets, keep action and assertion responsibilities distinct; do not hide business state changes in `Then`.
- Provisioning `Given` may internally perform API actions and assert setup success. It is data setup, not the behavior under test.
- Read-only evidence collection, refresh/search needed to observe state, and setup checks can require internal mixed keywords. Existing snippets already do this; strict keyword purity is not enforced repository-wide. Prefer focused composition, not extra wrappers solely to change a keyword.

```text
Page snippet: page-scoped atomics/assertions
Component snippet: reusable cross-page component behavior
Flow snippet: business orchestration (no locator/UI primitives)
```

Allowed call graph:

- Scenario (`.feature`) → Flow snippet only.
- Flow snippet → Flow/Page/Component snippets.
- Page snippet → Component snippets / built-ins / focused Java glue.
- Component snippet → built-ins / focused Java glue; no reverse call to business Flow.

This is the UI composition graph. API scenarios use business API snippets and supported API glue. `new_trade_page.snippet` already composes a confirmation component, so Page → Component is supported.

### 3.5 HTTP Actions Stay Behind Business Steps

- `When post/get/delete to path ...` must never appear in `.feature` files.
- Put protocol actions in API snippets or supported request/provisioning infrastructure. Keep bodies in JSON and endpoint resolution in configuration.
- Do not mutate business state inside an assertion. A persistence/audit assertion may collect read-only API/database evidence when that evidence is part of the approved requirement.

### 3.6 Aggregate Assertion Pattern

- If 3+ `Then` lines assert properties of the same business object, aggregate into one `@Then` snippet.
- Inside aggregated snippet, prefer built-in assertions (`assert that 'x' is 'y'`).

### 3.7 Snippet File Organization

Recommended sections by layer:

```text
Page:      # — PAGE ACTIONS / # — PAGE ASSERTIONS
Component: # — COMPONENT ACTIONS / # — COMPONENT ASSERTIONS
Flow:      # — PRECONDITIONS / # — BUSINESS ACTIONS / # — BUSINESS ASSERTIONS / # — CLEANUP(optional)
```

Rules:

- Do not mix layer responsibilities in one file.
- Keep `@Then` in assertion sections; do not hide actions in assertion snippets.
- Flow snippets should not contain locator keys or raw UI primitives.
- `CLEANUP` belongs in flow/API snippets only when needed.

### 3.8 Reuse Trade Provisioning

Use `snippet/ui/flows/common_trade_action.snippet` for browser-test preconditions:

| Public precondition | Result |
|---|---|
| `a Live '<product_type>' trade exists in the blotter` | Live |
| `a Draft Pending Approval '<product_type>' trade exists in the blotter` | Pending Approval |
| `a Dead '<product_type>' trade exists in the blotter` | Dead |
| `a Draft '<product_type>' trade exists in the blotter` | Draft |

These delegate to `TradeProvisioningSteps` and create data through API helpers; they **do not** log in or navigate. Use a login flow separately. UI provisioning currently uses fixed `createdTradeId` / `createdTaskId` context keys; it does not expose the API flow's custom-key variants. `a saved Draft ...` also exists but uses a different provisioning path; do not assume it is synonymous with the Draft precondition.

Use `snippet/api/common_trade_action.snippet` for API preconditions:

- `a '<product_type>' trade is submitted to pending approval via api`
- `a live '<product_type>' trade is created via api`
- `a draft '<product_type>' trade is created via api`
- `a dead '<product_type>' trade is created via api`

Reuse the API `with ... stored as` variants where provided when custom keys are needed. Do not inline create + approve logic: headers, task IDs, and context propagation are part of the existing setup contract.

### 3.9 Shared API Outcomes

`snippet/api/common_api.snippet` provides:

- `the '<object>' should be '<action>' successfully`: status 200 + current-case expected response.
- `failed to '<action>' '<object>'`: status 400 + current-case expected response.
- `failed to '<action>' '<object>' with status '<status>'`: explicit status + current-case expected response.

Use a domain-specific assertion when approved evidence requires more than this response contract (for example persisted audit data). A successful response must not be described as persistence or downstream-event proof unless those are actually checked.

## 4. Locator Rules

### 4.1 Element Repository Rule (Hard Rule)

All element interactions go through the element repo. `getPage()` is only for page-level API calls.

| Pattern | Valid? |
|---|---|
| `ctx.service().getLocator("element_name")` | ✅ |
| `ctx.service().getLocator("parent").getByRole(...)` | ✅ (scoped chain from registered container) |
| `ctx.service().getPage().waitForResponse(...)` | ✅ (page-level API) |
| `ctx.service().getPage().getByTestId("btn").click()` | ❌ (register in JSON repo first) |

### 4.2 Element JSON Placement

Decision rule: can this surface be reached by a distinct URL?

- Yes → `elements/pages/<page_name>.json`
- No → `elements/components/<component_name>.json` Examples:
- Trade Portal (`/trades`) → page JSON.
- Login (`/login`) → page JSON.
- Allocation dialog/date picker overlays → component JSON.

### 4.3 No Dynamic Locator Concatenation (Hard Rule)

- Never construct testId strings inline in Java from runtime values.
- First reuse registered/parameterized locators and existing resolver helpers. Top-level locators belong in JSON, not a Java step body.
- If an unsupported dynamic child truly requires focused Java, resolve its registered parent first and keep the scoped child selector in a named constant; filter by runtime text/value rather than concatenating test IDs. Do not use this exception to bypass the repository for ordinary controls.

### 4.4 Selector Priority

| Priority | Method | Notes |
|---|---|---|
| 1 | `getByTestId()` | most stable; register the top-level locator in JSON |
| 2 | `getByRole()` | only when unique |
| 3 | `getByLabel()` | labeled form inputs |
| 4 | CSS selector | last resort; avoid positional/dynamic class selectors |
| ❌ | XPath | never |

`findBy: "role"` with bracket notation is unsupported (name filter dropped). Use `findBy: "locator"` with `button:has-text('Confirm')`.

### 4.5 Form Value Assertions

- Text-content assertions are appropriate for rendered text, not live form values.
- For `<input>` and `<textarea>`, prefer the existing value assertion backed by Playwright `hasValue`; a DOM `value` attribute or textarea text content need not reflect the current edited value.

```gherkin
And assert that 'product_edit.name_input' eventually has value 'FX Target Redemption Forward'
```

This is a page/component-level implementation step, not scenario wording. See `CommonE2EAssertion` and `PlaywrightServiceResolver.Context.assertHasValue`.

## 5. Java Glue Rules

### 5.1 Class Structure

- Step glue classes are plain POJOs: no `@Component`, no extending another glue/base class.
- Resolve scenario context in `@Before` through the appropriate resolver (`PlaywrightServiceResolver.from(scenario)` for UI; `RestServiceResolver` for API).
- This restriction is on glue classes, not framework context types: resolver `Context extends ScenarioContext` is existing infrastructure.

### 5.2 Step Granularity

- Keep new action and assertion steps focused on one responsibility; do not hide a business action inside an assertion.
- Necessary read-only observation/setup checks follow the phase-intent rules in §3.4; do not claim legacy mixed-phase steps have already been removed.
- One step = one concern.

### 5.3 `ctx` Key Contract

- Parameterize new business ctx keys and reuse established provisioning variants; do not duplicate fixed-key steps.
- Report non-sensitive stored values for useful diagnosis, following §1.9. Never expose secrets in reports.

### 5.4 Minimal Step Principle (Hard Rule)

- Java step must do exactly one thing that built-ins/snippets cannot do.
- If a method hovers + waits + clicks + waits, decompose it.
- Keep rare Java-only logic tiny; move assertions/actions back to snippet built-ins.

### 5.5 Wait Strategy — Prefer `CommonE2EAssertion`

Never add Java steps only to call `waitFor`.

- For async state, reuse `CommonE2EAssertion` (`assert that 'x' is eventually ...`) polling assertions rather than creating a delay/wait wrapper.
- `CommonE2EAssertion` is this repository's Java glue, **not** a Genie built-in. Check the catalogs for built-ins and the glue for project extensions.
- The eventual timeout comes from service configuration, with a resolver fallback of 50,000 ms; do not assume a fixed five-second wait from an older comment. Use:

```gherkin
Then assert that 'toast.success' is eventually visible
And  assert that 'dialog.panel' is eventually hidden
And  assert that 'save_btn' is eventually enabled
And  assert that 'readonly_input' is eventually disabled
```

Decision table:

| Situation | Use | Do not write Java for |
|---|---|---|
| Wait appear | `assert ... eventually visible` | `locator.waitFor(VISIBLE)` |
| Wait disappear | `assert ... eventually hidden` | `locator.waitFor(HIDDEN/DETACHED)` |
| Wait enabled | `assert ... eventually enabled` | `waitFor + isEnabled` |
| Wait disabled | `assert ... eventually disabled` | custom disabled assertion step |
| Wait response before UI update | `ctx.clickAndWaitForResponse(...)` | inline `page.waitForResponse(...)` |
| Wait navigation | `assert that current url contains ...` | inline `page.waitForURL(...)` |

No new `Thread.sleep()`. Prefer framework auto-wait, eventual assertions, or response-based waiting. Use existing response wait/capture helpers rather than duplicating low-level waiting logic.

### 5.6 Reuse Focused Infrastructure Before New Java

There is no fixed six-case whitelist. New Java is the last resort after the reuse order in §3.1, and must implement a missing focused capability rather than wrap a built-in primitive.

| Existing capability | Reuse |
|---|---|
| Configured REST service/context | `RestServiceResolver` |
| Current-case/setup JSON, request parameters, runtime-variable/task-ID injection, response extraction | `ApiRequestSteps` |
| Product/multipart/dat request handling | `TradeApiSteps` |
| Expected responses, dynamic variables, focused business-field assertions | `ApiResponseAssertionSteps` |
| UI test data provisioning | `TradeProvisioningSteps` |
| Existing UI context/dynamic-element/input interaction and eventual assertions | `CommonE2EInteraction`, `CommonE2EAssertion` |
| Response wait/capture, mock routes, parameterized elements, configured page/context access | `PlaywrightServiceResolver.Context` |

For a new domain, first check these shared capabilities. If there is no established snippet/glue/data contract, report the gap rather than guessing a new architecture.

### 5.7 Anti-Patterns That Cause Java Step Proliferation

| Anti-pattern | Correct approach |
|---|---|
| One Java `@Then` per field | Aggregate in one snippet `@Then` using built-ins |
| Reimplement built-in action/assertion in Java | Use built-ins + snippet composition |
| URL check in Java | Use built-in URL assertion |
| `if/else` branching in Java step | Split into explicit scenarios/preconditions |
| Custom `waitFor` Java step | Use `CommonE2EAssertion` eventually steps |
| Locator keys directly in feature files | Keep locator refs in snippets/element JSON only |
| Feature→Java one-to-one, no snippet layer | Reuse business snippets; Java only for a genuinely missing capability |

Signal to stop: if new Java method only does `click`, `fill`, `inputValue`, `innerText`, `isVisible`, it should be built-in/snippet, not Java.

## 6. JSON Data Contracts

The loader is `TestDataRepository`, not a YAML `TestDataLoader`. Case request data, setup data, and API recorded responses are separate contracts; do not require every case row to have both `request` and `response`.

### 6.1 Case Data and Merge Order

| Location/node | Supported role |
|---|---|
| Optional `data/api/common.json` / `data/ui/common.json` | Channel-local `shared` blocks; not automatic request defaults |
| Event file top level | `shared`, `defaults`, `byProduct`, `cases` |
| `shared` block | `request`, `dynamicFields` |
| `defaults` / a product layer | `use`, `request`, `dynamicFields`, `expectedBase`, `datFile` |
| `cases` row | `id`, the layer fields above, and optional `byProduct` |

Resolution order, from least to most specific:

1. Event `defaults`.
2. Event `byProduct[product]`.
3. The single matching case row.
4. That row's `byProduct[product]`.

At each layer, apply its `use` block names in order, then the layer's own fields. Within a channel, an event's shared block names must not collide with common shared names; validation rejects collisions rather than overriding them. A missing referenced block is an error; common files are optional and need not exist in the current tree.

- `request` objects deep-merge; arrays replace as a whole; a field set to `null` deletes that field.
- `{"_append": [...]}` appends to an existing array, with duplicate `key` checks. It is not a general-purpose array replacement form.
- `dynamicFields` merges by path. A null rule removes that path's rule; `dynamicFields: null` clears the rules.
- `_comment` is stripped recursively before validation; only supported schema keys may otherwise be used.
- Date placeholders support `${date:T}`, `${date:T+N}`, and `${date:T-N}`.
- `dynamicFields` and `expectedBase` are API response metadata, not UI case fields. Keep product differences in `byProduct` and ensure each product resolves through `ProductDatFiles`.
- Keep uniqueness intent in JSON (including case IDs where appropriate), not in a request body embedded in Gherkin.

### 6.2 Hook Injection

`ScenarioLifecycleHooks @Before(order=1)` reads the ID from the Scenario name and resolves the case. It publishes `_tc.key`, optional `_tc.datFile`, non-empty `_tc.request`, and request values as top-level JsonNodes/leaf dot-path values in context. Reuse these injected values and existing request helpers; do not add a second loader in glue.

### 6.3 Setup Data

- Store setup files in `data/setup/{name}.json`; names match `[a-z0-9][a-z0-9-]*`.
- Supported top-level fields are `extends`, `request`, and `response`. Inheritance deep-merges payloads and rejects missing parents or cycles.
- Either payload may be absent; the helper requiring that payload fails when it is missing. Do not add unused empty nodes merely to satisfy an obsolete rule.
- Setup `response` is a setup assertion payload, not the recorded response for a feature case.
- Reuse an existing setup/provisioning entry point to preserve required headers and context behavior.

### 6.4 Products and Dat Variants

- `ProductDatFiles` owns product registration, default classpath paths, and capability membership. The currently registered products are `FX_TRF`, `FX_PSCRIPT`, `FX_PSCRIPT_FSKO`, and `FX_PSCRIPT_TRF`; commented-out enum entries are not supported products.
- Default dat path: `data/dat/{PRODUCT}/{PRODUCT}.dat`.
- JSON `datFile` **is supported** in defaults/product/case layers. It is a `.dat` filename within that product's folder, never a local path or directory. `null` resets the variant to the default.
- The resulting classpath file must exist. Existing FX_TRF creation cases already use variant files; do not move their filenames into feature steps.
- `<product_type>` outlines require exactly one supported `@capability#...` tag and a registry-backed, single-column `product_type` Examples table in registry order. Capability names are case-sensitive; inspect `Capability` / `ProductDatFiles`, not old comments, for eligibility.
- Use `SyncProductExamples` only for approved synchronization; it writes feature files. Do not add a new product/unsupported product scenario as a side effect of ordinary test authoring.
- Current UI provisioning boundary: `submitTrade()` honors `_tc.datFile`, while `saveTrade()` uses the product default. Do not claim all provisioning paths support variants.

### 6.5 API Expected Responses

Recorded responses live in `data/api/expected/{event}/{case-id}.json`, not a `response` node in case JSON.

- A file is either a complete response or an object containing `_base` (case ID) and `_patch` (JSON Patch operations).
- The base must be in the same event/channel, exist, and be a complete response; patches cannot chain.
- `expectedBase` in case data is compaction metadata, **not a runtime fallback** when the current case's expected file is missing. Compaction rejects unsuitable/chained bases and writes a patch only when it is smaller than the full response.
- In normal comparison mode, a missing expected file is recorded to the source data directory and the step fails once for review. A regular API run is therefore not guaranteed read-only.
- `OREO_EXPECTED=update` or `-Doreo.expected=update` overwrites recorded responses and passes the response-comparison step. This is a maintenance operation, not proof the new response is correct; review diffs against the approved requirement.
- `ResponseMasker` supports `type`, `type-tree`, `regex:...`, `ignore`, and `var:...` rules. Mask only genuinely dynamic fields; do not blanket-ignore business error messages or invent a YAML/json-unit contract.
- Dynamic fields must agree with the recorded expected values. Known current gap: runtime supports `var:`, but `TestDataValidationTest.checkHit` does not yet recognize it. Do not describe offline validation as complete support for every runtime masking rule.
- `trade_audit` uses dedicated database/audit evidence and is explicitly exempt from the expected-file requirement in offline validation.

## 7. Execution and Verification

Run the smallest relevant check first. Commands below are PowerShell-compatible and run from the repository root.

### 7.1 Offline Checks

```powershell
mvn -B clean compile
mvn -B test -Dtest=TestDataValidationTest
mvn -B test -Dtest=ExpectedResponsesTest
mvn -B test -Dtest=JsonProcessingTest
```

- Run `TestDataValidationTest` before committing test-data changes and review its expected-response/product-example warnings. It checks schema, feature/channel/event alignment, case resolution, orphan data/expected files, masking agreement, setup references, and product Examples.
- This validator is not a complete authoring linter: scenarios without a `[TC-...]` token can be skipped, and it does not enforce every title/tag/TP-comment rule. Review Gherkin separately.
- Current `pom.xml` has **no Surefire `<excludes>`** for data tests; ordinary `*Test` classes match the default scan. Plain `mvn test` can also run environment-dependent `OreoTest`, so it is not a safe shorthand for offline-only verification or for the full E2E suite.
- `TestDataRepositoryTest` can be selected with `mvn -B test -Dtest=TestDataRepositoryTest`, but currently references `testdata-fixtures/data/`, which is absent from this workspace. Check/restore the fixtures before treating that test as a passing verification gate.

### 7.2 Environment E2E and Selection

```powershell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#smoke"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"
```

- Use legacy `-Dcucumber.options`, **not** `-Dcucumber.filter.tags`.
- Use `--tags` for execution/business/capability/suite selection; use `--name` for a case ID in the Scenario name.
- These commands express requested filters, not a guarantee of unrestricted coverage. Inspect fixed runner tags/name and the effective selected scenarios; a name filter can still be excluded by runner scope. Do not rewrite feature tags to compensate for an empty run.
- Check active runner/environment properties and required browser/access prerequisites before running against an environment. Do not copy credentials into docs, commands, or reports.

### 7.3 Explicit Write Operations

Use only for intentional expected-response/product maintenance, with a reviewed scenario scope:

```powershell
# Writes expected responses for the selected case; inspect effective runner scope first.
mvn -B test -Dtest=OreoTest "-Doreo.expected=update" "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"

# These maintenance classes do not match Surefire's default test naming patterns.
mvn -B test -Dtest=CompactExpectedFiles "-Doreo.compact=all"
mvn -B test -Dtest=SyncProductExamples "-Doreo.sync=all"
```

Review all source-data/feature diffs and rerun offline checks afterwards. Do not recommend the configured `exec:java` test-data CLI as an available alternative: `pom.xml` still references `testdata.tools.TestDataTool`, but that class is absent from the current source tree.

## 8. Hard Stops and Legacy Boundaries

| Stop condition for new work | Required response |
|---|---|
| New domain without an established glue/snippet/data contract | Reuse shared API/UI infrastructure first; report the unresolved contract before editing |
| Duplicate/multiply matching case IDs or setup-name collision | Search both API/UI data; use one case row with `byProduct` where appropriate |
| Missing product registration/dat file | Resolve `ProductDatFiles` and classpath data before adding coverage |
| Appending another Feature/top-level tag block/Background to an existing feature | Append scenario blocks only; preserve the existing header |
| Plain `@TC-...` tag or modern Cucumber filter property | Put the ID in the Scenario name; use supported legacy options |
| Feature-level `@ADO#smoke` / `@ADO#regression` or polarity | Keep suite/polarity scenario-level to avoid selecting the whole feature |
| Request body, endpoint, dat path, expected response, or raw HTTP step in a feature | Keep business intent in Gherkin and details in data/snippets/configuration |
| UI scenario calling API snippets | Use dedicated UI provisioning flow/glue for preconditions |
| Java wrapper for built-in click/type/fill/select/navigation/text/visible assertion | Use built-ins and page/component snippets |
| Top-level CSS/XPath/testId/role selector hardcoded in a Java step | Register/resolve it through the element repository |
| Glue `@Component` or glue/base-class inheritance | Use focused POJO step classes and resolver context |
| New direct RestAssured usage or `Thread.sleep()` | Use Genie request/response helpers and auto/eventual/response-based waits |
| Hidden business state changes inside assertions | Separate the action from evidence collection/assertion |
| Credentials, tokens, or sensitive generated traces in changes/reports | Remove them; do not commit or log sensitive artifacts |

Existing code still includes UI primitive wrappers, direct JsonPath/RestAssured usage, a delayed-mock sleep, locator-bearing flows, mixed-phase snippets, fixed provisioning keys, and older feature wording/tags. These are legacy exceptions, not templates for new work. Do not claim the repository is already free of them or refactor unrelated behavior as part of a test/data change. `@wip` is an exclusion marker; do not remove it merely to force a failing or incomplete case into a run.

Implementation anchors: `testdata/CaseId`, `TestDataRepository`, `JsonLayers`, `ProductDatFiles`, `ExpectedResponses`, `ExpectedCompaction`, `ResponseMasker`; `glue/hooks/ScenarioLifecycleHooks`; API/UI glue and common snippets named above; `testdata/TestDataValidationTest`, `ProductExamples`, `FeatureFile`; `OreoTest.java` and `pom.xml`.
