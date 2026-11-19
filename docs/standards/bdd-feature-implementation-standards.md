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
   │ puts _tc.key, _tc.request, _tc.request.<field>, flat leaves (basic.counterpartyName), _tc.datFile,
   │ _tc.productId into ctx
   ▼
feature step → snippet (business step) → snippets / Genie built-ins / focused Java glue
   ▼
API: the '<subject>' is retrieved successfully, the '<subject>' request is rejected with ...
     → status code + strict comparison with data/api/expected/trade_creation/<case ID>.json
```

Three consequences shape everything below:

- The **case ID** is the only link between a scenario and its data. The feature file name must equal the event (the
  data file name); `TestDataTool check` enforces it.
- **Steps never carry data.** Request bodies, dat files, dates and expected responses live in JSON; steps read them
  from ctx.
- **Snippets bind by step text**, not by file name: a feature may use any snippet file.

## 2. Where a New Step Goes

The reuse order (existing snippet → Genie built-in → existing glue (§4.4) → new snippet → new Java) and where each
kind of step goes are in [CLAUDE.md §3](../../CLAUDE.md#3-where-a-change-goes). Built-ins:
[REST catalog](../genie_rest_glue_catalog.md), [Playwright catalog](../genie_playwright_glue_catalog.md).

## 3. Snippets

### 3.1 UI layering (hard rules)

```text
.feature ──► Flow ──► Flow / Page / Component ──► Component / built-ins / focused glue
    └──────► UI provisioning (Java, PlaywrightTradeProvisioningSteps) ──► TradeApiClient
```

The layers keep UI mechanics out of the feature: what a scenario calls reads as business and hides every element key,
UI primitive and HTTP detail.

- A scenario calls business steps only: **flow** steps, and the **UI provisioning** steps
  (`a Live / Draft Pending Approval / Dead / Draft / saved Draft '<product_type>' trade exists in the blotter`,
  `a Live '<product_type>' trade with a pending Amendment / Cancellation exists in the blotter`, §3.3).
  Never a page or component step, a built-in, other Java glue, or `snippet/api/`.
- A provisioning step has no UI: it sets state through the API and neither logs in, navigates nor touches a page.
  It is Java worded as business, called by the feature directly; no flow only renames it. Blotter and product
  provisioning (`BlotterSteps`, `ComposeProductSteps`, `... is provisioned via playwright api`) is other Java: flows
  call it, with the names they generate.
- A precondition that also uses the UI (provision, then open a page) is a flow: it calls the provisioning step and the
  pages.
- The only Java a scenario calls directly is such a provisioning step, in `PlaywrightTradeProvisioningSteps`. Any other
  Java step is reached through a snippet.
- Flows orchestrate business actions and never contain element keys or UI primitives (`click`, `type`, `fill`,
  `select`, visible/hidden checks).
- Page and component snippets own element keys and UI mechanics; they never call a flow.

### 3.2 API snippets

- HTTP stays behind business steps: `post/get/delete to path ...` never appears in a feature. Endpoints come from
  configuration, bodies from JSON.
- An assertion never changes state. It may read evidence (an API read, a database query) when that evidence is what
  the requirement asks for.
- Every request starts with `start a new api request as user '<user>'` (`api/common_api.snippet`), never with the
  built-in `start building a new request` alone: it sets the headers every OREO API requires (`X-User-Id`, and
  `X-Request-Id`, a new UUID per request, stored as `requestId`). A header all requests need goes there; Java requests
  get the same headers from `RestApiUtil`.
- `<user>` is a role: `maker`, `checker`, `admin` or `user` (`config/TestUser`), never an e-mail. The e-mails are
  `oreo.user.<role>.email` (`OreoModuleConfiguration`, like `oreo.risk.engine.mode`): a default in `genie.properties`,
  overridden in an environment file; Java uses `TestUser.MAKER.email()`. The UI signs in with
  `<role> is logged in to the trade portal`; its password is `oreo.user.<role>.password` in `genie.properties`
  (the same in every environment), overridden by `-Doreo.user.<role>.password`.

### 3.3 Reuse provisioning and outcomes

Where each shared step lives and what it checks; how a feature words them is the
[generation standard §6](bdd-feature-generation-standards.md#6-step-language).

| Need | Use | Notes |
|---|---|---|
| UI precondition | `a Live / Draft Pending Approval / Dead / Draft / saved Draft '<product_type>' trade exists in the blotter`, `a Live '<product_type>' trade with a pending Amendment / Cancellation exists in the blotter`, `multiple Live '<product_type>' trades with a pending Cancellation exist in the blotter` (`PlaywrightTradeProvisioningSteps`, Java steps with this wording, no snippet in between) | Provisions through the API; does not log in or navigate; stores `createdTradeId` / `createdTaskId` |
| API precondition | `a live / draft / dead '<product_type>' trade is created via api`, `a '<product_type>' trade is submitted to pending approval via api`, and their `... stored as '<key>'` variants, a pending Cancellation / Amendment, an allocated trade (`ApiTradeProvisioningSteps`) | Never inline create + approve: headers, task IDs and ctx keys are part of the contract |
| API lifecycle event | `(maker\|checker) has valid / invalid / malformed / duplicate data [with '<what>'] for the '<event>' event [on the trade stored as '<key>']`, `... has data for the '<event>' event on a trade that does not exist`, `performs the '<event>' event via api`, `the '<event>' event is executed successfully` (`api/trade_lifecycle_event.snippet`), `the '<event>' event is rejected with a validation error / permission denied / ...` (the API outcomes) | A success checks the event's response lightly and compares the trade it ran on, retrieved after it, with the recording: its row uses `trade-response-parts` and `retrieved-trade`. A rejection compares the event's response. The trades an event creates are `childTradeId1..n`, checked in the event's own snippet against the request (`the new trades of the event have '<path>' as in the request's '<data key>'`). A new event adds case data, recordings and the feature. A new variant (another status, another trade source) is added there, for every event |
| API checker task | `(maker\|checker) has valid data for trade creation / amendment / cancellation approval / rejection / self-approval`, `performs the approval / rejection / bulk approval for the request`, `the '<kind> approval / rejection' request is processed successfully`, `the '<kind> self-approval' request is rejected with permission denied` (the API outcomes); provisioning `checker approves / rejects the pending task` (`api/trade_approval_rejection.snippet`) | A new kind of task adds its name to the alternations, not a step |
| Event amounts from the trade's notional | API: the case data gives `"50%"` or `"[60%, 30%, 10%]"`; `build json request from current scenario with trade id from stored variable '<var>' as user '<role>'` (`api/common_api.snippet`) sends the amounts. UI, in a flow before the form is filled: `the share '<key>' of the notional of trade '<trade var>' is stored as '<amount var>'[ if it is set]`, `the novated notional of trade '<trade var>' is computed from the remaining notional '<key>'` (`TradeLifecycleEventSteps`) | Never an amount per product in `byProduct`. The UI step stores the amount to type and `<amount var>.display` to compare with the page; `if it is set` for a value only some products give, typed with a component step that ends in `if it is set` too. Rules: [docs/test-data.md](../test-data.md), "Where a value goes" |
| API outcome | `the '<subject>' [request] is / are retrieved / created / updated / approved / processed / executed successfully` (200); `... is / are saved / published / re-published / imported successfully` (201); `the '<subject>' request / event is rejected with a validation error` (400), `with permission denied` (403), `as not found` (404), `with a conflict` (409) (`api/common_api.snippet`) | Status + strict comparison with the recorded response. One vocabulary: a domain `Then` exists only when it checks more than this; never a wrapper that renames an outcome. Another status (422, ...) is an outcome added here, with its own words |

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
  overlays and panels. Name: `<page_or_component>.<element>`, snake_case, with the prefix the file already uses
  (`dynamic_action.` in `dynamic_action_dialog.json`), not the file name. Add to the existing file of that page or
  component.
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
- A step class holds no endpoint, payload assembly or response path. A Java step that calls an endpoint, or waits for
  its response, takes the path and the request from the domain's API client (`utils/TradeApiClient`,
  `utils/ProductApiClient`).

### 4.3 Waiting and ctx

- No `Thread.sleep()`. Wait with Playwright auto-wait, the eventual assertions of `CommonE2EAssertion`
  (`assert that '<element>' is eventually visible`, `... eventually has value '...'`; its timeout comes from the
  service configuration, default 50 s), or the response helpers (`ctx.clickAndWaitForResponse(...)`).
- A page is ready when what the user sees is there: wait on its elements (`page trade portal is displayed`), not on
  one of the requests it sends. The response helpers wait for the first matching request the action sends and that
  request's whole response (a request already in flight is not taken for it), so a retried or reordered request
  fails the wait on a page that loaded; keep them for a response that is the result of the action (a save, a
  refresh). When the page answers the action with another request of its own (the blotter reload after
  trigger-event), wait for both in order (`ctx.clickAndWaitForResponses(...)`) and assert where the user is, instead
  of reloading or searching again. A response is not a redrawn page: after a refresh, also wait for the view's
  loading state to go. The
  login waits for the trade portal and its New Today blotter, not for `blotter/trades`.
- An action that makes the page call the risk engine (save, book, an event's confirm, opening the trade detail page,
  which loads `/trades/{id}/risk-metrics`) goes through `ctx.clickUsingRiskEngineMode`,
  `ctx.doubleClickUsingRiskEngineMode` or `ctx.navigateUsingRiskEngineMode` with a `List<RiskCall>`: in `MOCK` mode
  each call with a mock name is answered from `data/setup/<name>.json`, a `null` mock only waits; in `REAL` mode they
  are only awaited, in order. Paths come from `TradeApiClient` (`calculateRiskPath`, `riskMetricsPath`), never a
  literal. Open the detail page with `TradePortalSteps.navigateToTradeDetail` (View Details),
  `TradePortalSteps.doubleClickToViewTradeDetail` or `TradeDetailSteps.navigateToTradeDetails` (URL); a new way to
  open it waits for `TradeDetailSteps.openingCalls(tradeId)`.
- A flaky step (passes on retry) lacks a wait condition: find it; never add a sleep or a retry.
- Form values are asserted with `eventually has value` (Playwright `hasValue`), not text content.
- Glue waits and asserts through `PlaywrightServiceResolver.Context` (`ctx.assertEventuallyContains(...)`,
  `ctx.waitForVisible(...)`, ...), never with `PlaywrightAssertions` or `Locator.waitFor` options itself. Playwright
  calls and their timeout live in the `playwright` package; a missing one is added there (`EventualAssertions`,
  `ElementActions`) and exposed on the Context, then used by the step.
- ctx keys are camelCase (`createdTradeId`). New reusable glue takes the key as a step parameter. Write useful values to
  the report with `ctx.write()`; never credentials, tokens or full sensitive payloads.

### 4.4 Existing capabilities to reuse

| Capability | Where |
|---|---|
| REST service and context | `RestServiceResolver` |
| Current-case / setup JSON as request, path parameters, task IDs, response extraction, request ID header | `ApiRequestSteps` |
| Request from Java (provisioning, hooks, cleanup) with the required headers | `RestApiUtil` |
| Endpoint paths and provisioning requests of a domain, for Java steps and response waits; the IDs a provisioning response returns (`CreatedTrade`, `CreatedProduct`) | `TradeApiClient`, `ProductApiClient` |
| Product provisioning, event configuration, schema files to upload | `ProductApiClient` |
| Product, multipart and dat file requests | `TradeApiSteps` |
| Recorded-response comparison, stored-variable and field assertions | `ApiResponseAssertionSteps` |
| UI trade provisioning | `PlaywrightTradeProvisioningSteps` |
| UI lifecycle event forms (confirm and wait), event amounts from the trade's notional | `TradeLifecycleEventSteps` |
| API trade provisioning | `ApiTradeProvisioningSteps` |
| Provisioning calls shared by both (no scenario state) | `TradeApiClient` `provision...` methods |
| Lifecycle event request and results (`childTradeIds` included) | `TradeApiClient.triggerEvent` |
| Dynamic elements, inputs, eventual assertions | `CommonE2EInteraction`, `CommonE2EAssertion` |
| Response wait / capture, eventual assertions (visible, text, count, ...), mocked routes, parameterized locators | `PlaywrightServiceResolver.Context` |
| Risk calls in MOCK mode, awaited after a click, double click or navigation (`clickUsingRiskEngineMode`, `doubleClickUsingRiskEngineMode`, `navigateUsingRiskEngineMode`; opening the trade detail page mocks its risk metrics) | `PlaywrightServiceResolver.Context`, `data/setup/*-mock.json` |

A new domain starts from these. When no existing snippet, glue or data contract fits, report the gap before
designing a new one.

## 5. Test Data

The format and every rule of the test data are in [docs/test-data.md](../test-data.md):

| Need | Section |
|---|---|
| Which file and layer a value goes into | "Where a value goes" |
| Case data: layers, `use`, merge rules, dates (`${date:T+N}`, `${date:S-N}` from the product's settlement date), `datFile`, `productId` | "Case data" |
| An amount that depends on the trade's notional (a share, API and UI) | "Where a value goes" |
| Setup data, `extends` | "Setup data" |
| Products and capabilities, `sync-examples` | "Products and capabilities"; a new product: skill `onboarding-e2e-product` |
| Recordings, `dynamicFields` (`var:` for a scenario variable, `request:` for a value the request sent, such as a `${date:...}` date), `@expected#none`, re-recording | "Recorded responses", "Updating a recorded response" |

## 6. Verification

### 6.1 Offline — before every commit that touches data, features, the registry or setup names

`mvn -Ptest-data process-test-classes` (the unit tests of the data code, then `TestDataTool check`). What it checks
and the other commands: [docs/test-data.md, "Commands"](../test-data.md#commands).

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

The hard stops are in [CLAUDE.md §4](../../CLAUDE.md#4-hard-stops), with the note on legacy code: existing wrappers,
sleeps, direct JsonPath / RestAssured and Playwright assertions in glue are exceptions, not templates.
