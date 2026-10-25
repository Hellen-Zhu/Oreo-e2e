# BDD Feature Implementation Standard

How a generated feature is made executable in oreo-e2e: test data, snippets, element repository, Java glue and
verification. What the scenarios say is decided by the [generation standard](bdd-feature-generation-standards.md);
the test data format in full is in [docs/test-data.md](../test-data.md). Where this document and the code disagree, the
code wins and this document is fixed.

## 1. How a Scenario Runs

```text
Scenario: [TC-TRADE-CREATION-FX_TRF-API-002] ...
   │ case ID → channel api, event trade_creation, product FX_TRF
   ▼
ScenarioLifecycleHooks (@Before)  ── TestData.forCase(id) merges data/api/trade_creation.json:
   │                                  defaults → byProduct[FX_TRF] → the case's row → the row's byProduct[FX_TRF]
   │ puts _tc.key, _tc.request, _tc.request.<field>, flat leaves (basic.counterpartyName), _tc.datFile into ctx
   ▼
feature step → snippet (business step) → snippets / Genie built-ins / focused Java glue
   ▼
API: the '<object>' should be '<action>' successfully
     → status code + strict comparison with data/api/expected/trade_creation/<case ID>.json
```

Three consequences shape everything below:

- The **case ID** is the only link between a scenario and its data. The feature file name must equal the event (the
  data file name); `TestDataTool check` enforces it.
- **Steps never carry data.** Request bodies, dat files, dates and expected responses live in JSON; steps read them
  from ctx.
- **Snippets bind by step text**, not by file name: a feature may use any snippet file.

## 2. Where a New Step Goes

Work down the list and stop at the first that fits:

1. An existing flow / API snippet already expresses the step → reuse it.
2. A Genie built-in does it → use it inside a snippet ([REST catalog](../genie_rest_glue_catalog.md),
   [Playwright catalog](../genie_playwright_glue_catalog.md)).
3. Existing project glue does it (§4.4) → call it from a snippet.
4. Compose a new snippet from the above.
5. Only then write Java, for one capability none of the above has.

| The step is... | It goes into |
|---|---|
| A business action / assertion a scenario calls (UI) | `snippet/ui/flows/<feature or shared>.snippet` |
| A page's atomic action / assertion | `snippet/ui/pages/<page>_page.snippet` |
| Behavior of a dialog, toast or panel used on several pages | `snippet/ui/components/<component>_component.snippet` |
| An API business action / assertion | `snippet/api/<feature or common_*>.snippet` |

## 3. Snippets

### 3.1 UI layering (hard rules)

```text
.feature ──► Flow ──► Flow / Page / Component ──► Component / built-ins / focused glue
```

- A scenario calls flow steps only; flows orchestrate business actions and never contain element keys or UI primitives
  (`click`, `type`, `fill`, `select`, visible/hidden checks).
- Page and component snippets own element keys and UI mechanics; they never call a flow.
- A `@playwright` scenario never calls `snippet/api/`. Its API-based setup goes through the UI provisioning steps
  (§3.3).

### 3.2 API snippets

- HTTP stays behind business steps: `post/get/delete to path ...` never appears in a feature. Endpoints come from
  configuration, bodies from JSON.
- An assertion never changes state. It may read evidence (an API read, a database query) when that evidence is what
  the requirement asks for.

### 3.3 Reuse provisioning and outcomes

| Need | Use | Notes |
|---|---|---|
| UI precondition | `a Live / Draft Pending Approval / Dead / Draft '<product_type>' trade exists in the blotter` (`flows/common_trade_action.snippet`) | Provisions through the API via `TradeProvisioningSteps`; does not log in or navigate; stores `createdTradeId` / `createdTaskId` |
| API precondition | `a live / draft / dead '<product_type>' trade is created via api`, `a '<product_type>' trade is submitted to pending approval via api`, and their `... stored as '<key>'` variants (`api/common_trade_action.snippet`) | Never inline create + approve: headers, task IDs and ctx keys are part of the contract |
| API outcome | `the '<object>' should be '<action>' successfully` (200), `failed to '<action>' '<object>'` (400), `... with status '<status>'` (`api/common_api.snippet`) | Status + strict comparison with the recorded response |

Use a domain-specific `Then` when the requirement needs evidence beyond the response (state, audit, persistence).

### 3.4 Shape of a snippet file

- Sections: flow `PRECONDITIONS / BUSINESS ACTIONS / BUSINESS ASSERTIONS`; page and component `ACTIONS / ASSERTIONS`.
- Keep actions and assertions apart; a `Then` never performs the business action. Read-only steps an assertion needs
  (refresh, search) are fine inside it.
- Three or more `Then` lines on the same business object become one aggregated `@Then` built from built-in assertions.
- Step names state business intent (`approves the pending trade`), not mechanics; for new UI wording, role names only in
  login steps.

## 4. Java Glue

### 4.1 Element repository (hard rule)

Every element is registered in JSON and reached through it:

```json
{"name": "allocation_dialog.allocate_btn", "lookupDetails": {"findBy": "testId", "value": "allocation-allocate-btn"}}
```

- `elements/pages/<page>.json` for a surface with its own URL; `elements/components/<component>.json` for dialogs,
  overlays and panels. Name: `<page_or_component>.<element>`, snake_case.
- Selector priority: `testId` → role (only when unique) → label → CSS as a last resort; never XPath. For a button by
  its text use `findBy: "locator"` with `button:has-text('Confirm')` (a role with a name filter is not supported).
- In Java: `ctx.service().getLocator("<name>")`, or a scoped chain from a registered parent. `getPage()` only for
  page-level calls (`waitForResponse`). Never build a test ID from runtime values; use the parameterized locators.

### 4.2 Step classes

- Plain POJOs: no `@Component`, no base class. Resolve the context in `@Before`:
  `PlaywrightServiceResolver.from(scenario)` (UI) or `RestServiceResolver.from(scenario)` (API).
- Names: `<Domain>Steps` (UI), `<Domain>ApiSteps` (API).
- One step, one concern. A Java step that only clicks, fills, reads text or checks visibility is a built-in in a
  snippet, not Java. No `if/else` on business variants: split scenarios or preconditions instead.

### 4.3 Waiting and ctx

- No `Thread.sleep()`. Wait with Playwright auto-wait, the eventual assertions of `CommonE2EAssertion`
  (`assert that '<element>' is eventually visible`, `... eventually has value '...'`; its timeout comes from the
  service configuration, default 50 s), or the response helpers (`ctx.clickAndWaitForResponse(...)`).
- Form values are asserted with `eventually has value` (Playwright `hasValue`), not text content.
- ctx keys are camelCase (`createdTradeId`). New reusable glue takes the key as a step parameter. Write useful values to
  the report with `ctx.write()`; never credentials, tokens or full sensitive payloads.

### 4.4 Existing capabilities to reuse

| Capability | Where |
|---|---|
| REST service and context | `RestServiceResolver` |
| Current-case / setup JSON as request, path parameters, task IDs, response extraction | `ApiRequestSteps` |
| Product, multipart and dat file requests | `TradeApiSteps` |
| Recorded-response comparison, stored-variable and field assertions | `ApiResponseAssertionSteps` |
| UI trade provisioning | `TradeProvisioningSteps` |
| Dynamic elements, inputs, eventual assertions | `CommonE2EInteraction`, `CommonE2EAssertion` |
| Response wait / capture, mocked routes, parameterized locators | `PlaywrightServiceResolver.Context` |

A new domain starts from these. When no existing snippet, glue or data contract fits, report the gap before
designing a new one.

## 5. Test Data

Full rules: [docs/test-data.md](../test-data.md). The essentials:

### 5.1 Case data — `data/{api|ui}/{event}.json`

- Keys: `shared`, `defaults`, `byProduct`, `cases`; a layer has `use`, `request`, `dynamicFields`, `expectedBase`,
  `datFile`. Shared blocks come from the channel's `common.json` (applied only where `use` names them) or the file's own
  `shared`.
- A case without a row runs on the defaults: add a row only for a case that differs. One case matches at most one row;
  product differences go into `byProduct`.
- Objects merge, arrays and values replace whole, `null` removes; `${date:T±N}` for dates.
  `dynamicFields` and `expectedBase` are API only.
- Never write `basic.productId`: the step sets it from the product.

### 5.2 Setup data — `data/setup/{name}.json`

`request` and/or `response`, optional `extends`; names are lower case with `-`. Reuse before adding. API case data and
UI setup data stay apart on purpose, so that an API case change never alters how a UI case prepares its trade.

### 5.3 Products — `ProductDatFiles`

- A product is an enum constant with its capabilities and `data/dat/<P>/<P>.dat`; a variant dat file is case data
  (`datFile`), never a new product.
- Product Outlines' `Examples` are written by `TestDataTool sync-examples`, never by hand. To leave a product out, take
  the capability off it in the registry.
- Adding a product: skill `onboarding-e2e-product`.

### 5.4 Recorded responses — `data/api/expected/{event}/{case ID}.json`

- First run of a new API case records the response and fails once: review the file **against the requirement**, then
  commit. A recording is not a requirement.
- Values that change per run are masked by `dynamicFields` rules: `type` (presence and type; an array of any length
  becomes `${json-unit.any-array}`), `type-tree` (structure and leaf types), `regex:<p>`, `var:<name>` (equals a
  scenario variable), `ignore` (last resort). Mask only what is truly dynamic; never business messages.
- Never edit an expected file by hand: change the rule, record again (`OREO_EXPECTED=update`), then
  `TestDataTool compact <event>` turns variants into patches on their base.
- An API case that verifies other evidence (database, audit) instead of its response is tagged `@expected#none`; it has
  no expected file and `response matches current scenario` fails in it (`trade_audit`).

## 6. Verification

### 6.1 Offline — before every commit that touches data, features, the registry or setup names

```shell
mvn -Ptest-data process-test-classes
```

Runs the unit tests of the test data code, then `TestDataTool check`: data files load, every case resolves, every row
has its scenario, expected files and rules agree, setup names exist, product `Examples` match the registry. Other
commands: `"-Dexec.args=case <case ID>"` (what a case sends and is compared with), `sync-examples`,
`compact <event>|all`. Usage: skill `maintaining-e2e-test-data`.

### 6.2 Against an environment

The runner, `OreoTest`, is a Genie JUnit 4 runner (`@RunWith(Genie.class)`):

| Setting | Value | Consequence |
|---|---|---|
| `@CucumberOptions` features / glue | `classpath:features`; `com.example.module.oreo.glue` and `classpath:snippet` | Java glue and snippets are one step vocabulary |
| `@CucumberOptions` tags | `not @wip` and `@ADO#trade_creation` (all must match) | A plain `mvn test` runs trade creation only, **not the whole suite**; the default scope is changed in `OreoTest` |
| `@GenieOptions` properties | `src/test/resources/qa.properties` | The environment (addresses, accounts, browser) comes from that file |
| `@GenieOptions` forks | `forkMode = FEATURE`, `forkCount = 3`, `DISCRETE` working directories | Up to 3 features run at once in separate JVMs; scenarios of one feature run in order in one JVM |

Select scenarios with the legacy Cucumber option property `-Dcucumber.options`:

```shell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api --tags @ADO#smoke"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @capability#earlyTermination"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-\d+\].*$"
```

- `--tags` selects by layer, business, capability or suite tag (repeated `--tags` must all match); `--name` takes a
  regular expression on the scenario name, which starts with the case ID. Quote each `-D` argument whole (PowerShell
  included).
- Not `-Dcucumber.filter.tags` / `-Dcucumber.filter.name`: this project does not support them, and they may be
  ignored silently, running everything.
- A case selected by `--name` can still be excluded by the fixed tags of the runner: check `OreoTest` before reading
  an empty run as a pass.
- An API run books real trades in the target environment, and a first run writes expected files into `src`. Know what
  a filter selects before running it.
- Re-recording on purpose: add `-Doreo.expected=update` (or `OREO_EXPECTED=update`), then compact and review the diff.

### 6.3 Definition of done

- [ ] The offline check passes with no new warning.
- [ ] The new scenarios pass twice in a row against the environment (a pass that does not repeat is a flaky test).
- [ ] Each recorded response was read against the requirement; dynamic fields are masked by rules, nothing by hand.
- [ ] The diff contains no credentials, tokens, real trade data or environment addresses.
- [ ] No legacy pattern from §7 was copied into new code.

## 7. Hard Stops

| Never in new work | Instead |
|---|---|
| HTTP step, endpoint, body, dat path or expected response in a feature | Business step; details in snippets, configuration and JSON |
| `@playwright` scenario calling an API snippet | UI provisioning steps |
| Element key or UI primitive in a flow snippet or a feature | Page / component snippet |
| Java wrapper around a built-in (click, fill, text, visible, URL, wait) | Built-in in a snippet |
| Selector hard-coded in Java, test ID built from runtime values | Element repository, parameterized locator |
| `Thread.sleep()`, direct RestAssured, glue `@Component` or inheritance | Auto-wait / eventual assertion / response wait; Genie helpers; POJO + resolver |
| State change inside an assertion | Separate action and assertion |
| Two rows matching one case, a duplicated setup name, an unregistered product | One row with `byProduct`; reuse setup; register the product first |
| Hand-edited expected file or `Examples` row | Record + compact; `sync-examples` |
| `@TC-...` tag, feature-level suite/polarity tag, removing `@wip` to force a run | Case ID in the scenario name; scenario-level tags |

**Legacy.** Existing code still has UI primitive wrappers, direct JsonPath/RestAssured, two `Thread.sleep` calls,
locator-bearing flows, mixed-phase snippets, fixed provisioning keys and older wording. They are exceptions, not
templates; do not extend them, and do not refactor them as a side effect of a test change.
