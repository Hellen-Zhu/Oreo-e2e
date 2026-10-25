# oreo-e2e

End-to-end tests of **OREO**, the FX trade management portal: Java 21 + Cucumber on the Genie framework, in two
layers, API and UI (Playwright). Test data is layered JSON per lifecycle event; API responses are recorded and compared
strictly.

| Layer | Tag | Through | Proves |
|---|---|---|---|
| API | `@api` | genie-rest | Business rules, validation, permissions, state, contracts; no browser |
| UI | `@playwright` | genie-playwright | What the user sees and can do in the browser |

This is the public copy: identifiers are anonymized, environment settings and dat files are placeholders, and some
files are missing ("This copy", at the end). Run it only in the company environment, with approved local data and
configuration. Agents and contributors: read [CLAUDE.md](CLAUDE.md) first.

## Quick start

| Goal | Command |
|---|---|
| Check the test data (no browser, no environment); before every commit of data, features or the registry | `mvn -Ptest-data process-test-classes` |
| Run what the runner selects (what the pipeline runs: trade creation only, see "Running") | `mvn test` |
| Run a selection | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api --tags @ADO#smoke"` |
| Run one case | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"` |
| See what one case sends and is compared with | `mvn -Ptest-data process-test-classes "-Dexec.args=case <case ID>"` |
| Add a product | skill `.claude/skills/onboarding-e2e-product` |
| Change data, features, recordings or the registry | skill `.claude/skills/maintaining-e2e-test-data` |

## Setup

- JDK 21 (`JAVA_HOME`) and Maven, with the company Artifactory repositories in `~/.m2/settings.xml` (never committed);
  company network or VPN for dependencies and test environments.
- Microsoft Edge for Playwright. Surefire sets `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`, so install it once if the machine
  lacks it: `npx playwright install msedge` (Node.js is needed for this and for the trace viewer only).
- IntelliJ IDEA with the Gherkin and Cucumber for Java plugins is recommended.
- Credentials come from an approved local or CI mechanism, never from a tracked file. A credential found in a tracked
  file is exposed: rotate it.

```shell
mvn -B clean compile
mvn -Ptest-data process-test-classes
```

## Running

### What the runner selects

`src/test/java/com/example/module/oreo/OreoTest.java` is a Genie JUnit 4 runner (`@RunWith(Genie.class)`) and the
source of truth for the default scope:

| Setting | Value | Consequence |
|---|---|---|
| Features / glue | `classpath:features`; `com.example.module.oreo.glue` and `classpath:snippet` | Java glue and snippets form one step vocabulary |
| Fixed tags | `not @wip` and `@ADO#trade_creation` (all must match) | `mvn test` runs trade creation only, **not the whole suite**; a command-line filter is combined with these |
| Environment | `@GenieOptions(properties = "src/test/resources/qa.properties")` | Addresses, accounts and browser come from that file |
| Forks | `forkMode = FEATURE`, `forkCount = 3`, `DISCRETE` | Up to 3 features run at once in separate JVMs; scenarios of one feature run in order |

`genie.properties` holds the shared defaults (Edge, headless, 30 s timeout, trace mode, risk engine mode, element
repository paths); never hard-code an address in a feature or in glue.

### Selecting scenarios

Use the legacy Cucumber property `-Dcucumber.options`. **Not** `-Dcucumber.filter.tags` / `-Dcucumber.filter.name`:
this project does not support them, and they may be ignored silently and run everything.

```shell
# By layer, suite, business feature or capability (repeated --tags must all match)
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright --tags @ADO#smoke"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#trade_early_termination"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @capability#earlyTermination"

# By case ID: it is in the scenario name, not a tag
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-\d+\].*$"
```

- Quote each `-D` argument whole (PowerShell included).
- An empty run is not a pass: a case your filter selects can still be excluded by the runner's fixed tags.
- Every API run books real trades in the target environment, and a case without a recording writes one into `src`
  ("Recorded responses"). Know what a filter selects before running it.

| Tag | Level | Values |
|---|---|---|
| Layer | Feature | `@api`, `@playwright` |
| Business | Feature | `@ADO#<featureName>` (`@ADO#trade_creation`), optional group (`@ADO#trade_lifecycle`) |
| Polarity, suite | Scenario | exactly one of `@ADO#positive` / `@ADO#negative`, one of `@ADO#smoke` / `@ADO#regression` |
| Capability | Product Outline | exactly one `@capability#<name>` ("Products and capabilities") |
| Response check | Feature or Scenario | `@expected#none` ("Recorded responses") |
| Exclusion | any | `@wip` |

### Debugging UI cases

```powershell
# Playwright Inspector for one case
$env:PWDEBUG = "1"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-UI-001\].*$"
Remove-Item Env:PWDEBUG

# Traces: oreo.playwright.trace.mode = OFF | RETAIN_ON_FAILURE (default) | ON, written to target/traces
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright" "-Doreo.playwright.trace.mode=ON"
npx playwright show-trace (Get-ChildItem target\traces\*.zip | Select-Object -First 1).FullName

# New locators: record them against the environment's playwright.base.url
npx playwright codegen --channel=msedge <playwright.base.url>
```

Recorded locators go into `src/test/resources/elements/{pages,components}/*.json`, never into Java. Traces can
contain credentials and test data: do not commit them or upload them anywhere.

## How the cases are organised

- **One feature file per lifecycle event and layer** (`features/api/trading/trade_creation.feature`,
  `features/ui/trading/trade_early_termination.feature`), with one event data file of the same name
  (`data/api/trade_creation.json`).
- **A product is a row, not a file.** Each Scenario Outline whose case ID has `<product_type>` carries one
  `@capability#<name>` tag; its `Examples` are the products of that capability in `ProductDatFiles`, written by
  `TestDataTool sync-examples`. A new product is one registry constant and one dat file; product differences go into
  `byProduct` of the event files.
- **The case ID in the scenario name selects the data**: `[TC-TRADE-CREATION-FX_TRF-API-001]` → event
  `trade_creation`, product `FX_TRF`, layer API, case `001`.
- **UI steps are layered**: scenario (business language) → flow snippet (business workflow) → page / component snippet
  (element keys and built-in UI steps) → element repository (locators).

## How a scenario runs

1. `ScenarioLifecycleHooks` reads the case ID from the scenario name and merges the case's data
   (`TestData.forCase`): `defaults` → `byProduct[P]` → the case's row → the row's `byProduct[P]`.
2. It puts the request into the scenario context (`_tc.key`, `_tc.request`, `_tc.request.<field>`, flat leaves such as
   `basic.counterpartyName`, `_tc.datFile`) and writes the applied layers to the report.
3. The steps (`glue/api`, `glue/ui`) and the snippets (`src/main/resources/snippet`) read it from there; setup data
   (`data/setup/<name>.json`) prepares what a case needs (a live trade, a mocked risk calculation).
4. API cases end with `response matches current scenario`: a strict comparison with
   `data/api/expected/<event>/<caseId>.json`, masked by the case's `dynamicFields`.

## Documentation

| Document | For |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Working rules for agents and contributors: where a change goes, hard stops, checks |
| [test-layering-standards.md](docs/standards/test-layering-standards.md) | From a story to test points: what to prove, on which layer, with which evidence |
| [bdd-feature-generation-standards.md](docs/standards/bdd-feature-generation-standards.md) | From test points to feature files |
| [bdd-feature-implementation-standards.md](docs/standards/bdd-feature-implementation-standards.md) | From feature files to running steps, snippets, glue and data |
| [genie_rest_glue_catalog.md](docs/genie_rest_glue_catalog.md), [genie_playwright_glue_catalog.md](docs/genie_playwright_glue_catalog.md) | Genie built-in steps |
| `.claude/skills/` | Onboarding a product; maintaining the test data |

## Test data

```
src/test/resources/data/
  api/common.json, ui/common.json       shared blocks; they apply only where a file names them in "use"
  api/<event>.json, ui/<event>.json     one per feature file name: its own blocks, defaults, byProduct, case rows
  api/expected/<event>/<caseId>.json    recorded API responses, one per case: in full, or a patch on a base case
  setup/<name>.json                     payloads that prepare a case (trade-create, risk-calc-success-mock ...)
  dat/<PRODUCT>/<PRODUCT>.dat           a product's default dat file; its variants in the same folder
```

API and UI data never read each other; only the code is shared.

### Products and capabilities

`ProductDatFiles` lists every product with its dat file and the capabilities it supports (`Capability`: `create`,
`stepInFull`, `stepInPartial` and one per lifecycle event, `earlyTermination`, `novationRemaining`, `cancellation`,
`allocation`, `amendment`, `partialTermination`, `partialNovationRemaining`, `stepOutFull`, `stepOutPartial`,
`portfolioReassignment`, named as in the replay project's `framework/products.ts`; and `fixings`, which it has not yet).
The trade lifecycle is also one group, `Capability.LIFECYCLE_EVENTS`: `create`, `stepInFull`, `stepInPartial` and the
lifecycle events (`LIFECYCLE_EVENTS` there has the events only). A product names the group, or
`except(LIFECYCLE_EVENTS, ...)` when it lacks some, and its other capabilities one by one
(`FX_TRF(LIFECYCLE_EVENTS, FIXINGS)`).
Approval, rejection and query are not capabilities: an Outline that approves, rejects or queries what an event made
carries that event's capability (`create`, `stepInFull`, `stepInPartial`, `cancellation`; a query of fixing data
`fixings`). Each
Scenario Outline whose case ID has `<product_type>` carries one tag `@capability#<name>` next to its other tags, and its
`Examples` is one table with the single column `product_type`: one row per product of that capability, in registry
order. Nobody edits those rows; `TestDataTool sync-examples` writes them ("Commands").
It prints one line per Outline (`unchanged`, `add X`, `remove Y`, `reordered`, `reformatted`) and writes nothing when a
feature file has a problem: an Outline without a tag or with two, an unknown name, a tag anywhere else, a table other
than one `product_type` column, a capability no product has. `TestDataTool check` fails on those problems and on
every Outline the tool would change; a capability no Outline carries is only a warning.
Scenarios without `<product_type>` (negative and permission cases) carry no tag and are not touched. To leave a product
out of an Outline for a while, take the capability off the product in `ProductDatFiles` and run the tool: a row
commented out, or a blank line, inside a product table stops the tool and fails the check.

### Case data

The case ID in the scenario name selects the file and the product:
`TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002` -> `data/api/trade_creation.json`, product `FX_PSCRIPT_TRF`. A product is a
`ProductDatFiles` constant; an ID without one (`TC-TRADE-CREATION-API-004`) is a case without a product. The product
stands right before the channel; the only other part an `Examples` column may fill is the number at the end. A case
for each value of something else (a dataset) is an Outline `[TC-REFDATA-SYNC-MANAGEMENT-API-<case>] <dataset> ...`
with the columns `| case | dataset |` (`001`, `002` ...); each case is its own row, with its value, as in
`api/refdata_sync_management.json`.

```jsonc
{
  "_comment": "features/api/trading/trade_creation.feature",
  "defaults":  { "use": ["trade-basic"], "dynamicFields": { "data.trade.id": "type" } },
  "byProduct": { "FX_PSCRIPT_TRF": { "request": { "basic": { "direction": "Sell", "premiumAmount": null } } } },
  "cases": [
    { "id": "TC-TRADE-CREATION-<product_type>-API-002", "_comment": "StepIn Partial", "use": ["stepin-partial"] },
    { "id": "TC-TRADE-CREATION-API-004", "request": { "basic": { "counterpartyName": "" } }, "dynamicFields": null }
  ]
}
```

- Layers, each optional, later wins: `defaults` -> `byProduct[P]` -> the case's row -> the row's `byProduct[P]`.
  A layer applies its `use` blocks in order, then its own `request` and `dynamicFields`. A case without a product
  gets `defaults` and its row only.
- `use` names a block of `common.json` or of the event file's own `shared` (same shape: `request`, `dynamicFields`),
  for data of this event only. A name in both files is an error.
- Objects merge key by key; arrays and values replace; `null` removes a key. `_comment` is a comment at any depth.
- `{ "_append": [ ... ] }` as the value of a key adds its elements to the end of the list an earlier layer gave that
  key. No list there, or an element whose `key` the list already has, is an error (to change an element, give the
  whole list); written inside a list it is a load error.
- `${date:T}`, `${date:T+N}`, `${date:T-N}` in a text value, alone or in a longer text: today's date plus or minus N
  calendar days, `yyyy-MM-dd`, this machine's time zone (`DateUtil.resolveDate`), filled in each time a step reads the
  data (`request()`, `flattenRequest()`, a setup's `request()` / `response()`). Any other `${...}` is left for the step
  that fills it. Another expression (`${date:tomorrow}`) is a load error. Not business days.
- A row exists only for a case that differs from the defaults. Its `id` is the feature's, as written: an Outline's
  `TC-TRADE-CREATION-<product_type>-API-002` covers every product; product differences go into the row's `byProduct`.
  A case matched by two rows is an error.
- Any other key is a load error. `basic.productId` is never written: the step sets it from the product.
- A field that only some products' forms have is case data, not an `Examples` column: its value goes into those
  products' `byProduct`, and the step that fills it ends in `if it is set` (`type inner value '<key>' from stored
  variable into '<element>' if it is set` types nothing when the case has no value). Example: `targetAmount` of
  `ui/trade_novation_remaining.json`, which the old feature chose with an `input_profile` column.
- An element that only some products' API body has, in a list such as `data`: the list without it in `defaults`, the
  element once in a block of the file, and one line per product that has it:

  ```jsonc
  "shared":    { "target-amount": { "request": { "data": { "_append": [
                   { "key": "newTargetAmount", "value": 1000, "type": "Numeric" } ] } } } },
  "defaults":  { "request": { "data": [ { "key": "novationDate", "value": "${date:T}", "type": "Date" } ] } },
  "byProduct": { "FX_TRF": { "use": ["target-amount"] }, "FX_PSCRIPT_TRF": { "use": ["target-amount"] } }
  ```

  A row of a case without a product that needs it names the block too (`"use": ["target-amount"]`).
- `datFile`: the dat file the case uploads or sends, a file name of the product's folder `data/dat/<P>/`; without it
  (or `null`, back from a variant) the product's default `<P>.dat`. A variant of a product (same product ID, another
  structure) is data, not a product: the layer that needs it names it (`byProduct` for every case of that product in
  the file, a row or the row's `byProduct` for one case). A case is added for a variant only when its steps or checks
  differ; its recorded response is its own. Checked at load (a file name, no folder) and when the case's data is
  built (the file exists for the case's product); a case without a product uses the folder of its step's product.
  `upload '<P>' dat file`, `set multi-part dat file for product '<P>'` and the trade provisioning steps read it, and
  the report shows `[test-data:datFile]` and the `[dat file]` sent.
- The hook puts the request into ctx as before (`_tc.key`, `_tc.request`, `_tc.request.<field>`, flat leaves such as
  `basic.counterpartyName`) and writes the applied layers to the report (`[test-data:layers]`).

### Setup data

`data/setup/<name>.json`: `request` and / or `response`, `_comment`, and `extends` (another setup file, merged
under this one, so an `_append` adds to its lists; `${date:...}` as in case data). Read by the steps `... from test data '<name>'` and by `TestData.setup("<name>")`; its `request()` /
`response()` fail when the file (with its `extends` chain) has no such part, so a caller never checks for null and
asks `hasRequest()` / `hasResponse()` only when the part is optional. The setup trade
and the API case blocks hold the same values but are kept apart on purpose: what an API case sends must not change
how a UI case prepares its trade. `trade-cancellation` and `trade-amendment` have no file yet (not covered).

### Recorded responses

`response matches current scenario` (behind `the '<x>' should be '<y>' successfully` and `failed to ...`, after the
status code check) compares the response strictly with `data/api/expected/<event>/<caseId>.json`:

| Situation | Run | Result |
|---|---|---|
| Regression | `mvn test` | strict comparison |
| New product or case | `mvn test`, then the compaction | its file is recorded and the scenario fails once: compact, review the file against the requirement, commit, run again |
| The API changed on purpose | `OREO_EXPECTED=update mvn test ...` (or `-Doreo.expected=update`), then the compaction | the files are rewritten in full and compacted again: review `git diff`, commit |

The values that change from run to run are masked by the case's `dynamicFields` (path -> rule) when the file is
recorded:

| Rule | Still checked |
|---|---|
| `type` | presence and type of the value; on an array (`${json-unit.any-array}`), that it is an array, of any length and content; not on an object |
| `type-tree` | the whole structure (array lengths included), and the type of every leaf |
| `regex:<pattern>` | the value matches the pattern |
| `var:<name>` | the value equals the scenario variable `<name>` of this run (`createdTradeId`, stored by `a live '<product_type>' trade is created via api`) |
| `ignore` | nothing; only when the others cannot work |

A path is keys joined by `.`, `[n]` for one array element, `[*]` for all. A rule whose path is not in the response
fails the recording. Never edit an expected file by hand: change the rule and record again.

An API case that never compares its response with a recording (`trade_audit`: it calls a series of APIs, then checks
the database) carries the tag `@expected#none`, on its Scenario or Scenario Outline line, or once on the Feature or
Rule line for all of them. It has no expected file: `check` neither asks for one nor accepts one left from before
the tag, and `response matches current scenario` fails in it. Not on an `Examples` table, not on a UI case.

`var:<name>` records `"${var:<name>}"`, and only when the actual value is the variable's value; before each
comparison the variable's value of that run is put in its place. It takes text, a number or a boolean, and a
scenario that has not stored the variable fails. Example, a risk query by the trade of the case:
`"dynamicFields": { "data.tradeId": "var:createdTradeId" }`.

### Base and patch

The variants of a case return mostly the same response, so a variant's file holds only its differences from a
base case of the same product. The event file declares the base once:

```jsonc
"defaults": { "expectedBase": "TC-TRADE-CREATION-<product_type>-API-001", ... }
```

`<product_type>` becomes the case's product, so `FX_TRF-API-002` builds on `FX_TRF-API-001`. A case without a
product, and the base case itself, have no base; a `byProduct` entry or a row may name another base, or `null`.
A base has no base of its own.

```json
{
  "_base": "TC-TRADE-CREATION-FX_TRF-API-001",
  "_patch": [
    { "op": "replace", "path": "/data/trade/basic/eventStatus", "value": "..." },
    { "op": "add", "path": "/data/trade/basic/udf", "value": [ "..." ] }
  ]
}
```

The case is compared strictly with the base's file plus the ops. Recording always writes the full response; the
compaction, `TestDataTool compact trade_creation` ("Commands"; `all` for every event), then turns the variants into
patches. Objects are compared key by key, arrays of the same length element by element, other arrays are replaced
whole; a patch that is not shorter than the full response is not written. Any problem (a base without a file, a patch
that does not apply) stops it before a file is written, and a second run changes nothing. Then review
`git diff` and commit. A change every variant shares (a new response field) shows in the base files only.

Re-record a base together with its variants. A base recorded alone can leave a variant's patch that no longer fits
(an `add` on a key the base now has); that case then fails with an error naming the op. The way out is in the error:
record the case again with `OREO_EXPECTED=update` (update mode does not read the file), then compact.

### Commands

The offline tool is normal test-source code in `src/test/java/com/example/module/oreo/tools/`, entry point
`TestDataTool.java`.
The `test-data` Maven profile compiles it, refreshes test resources and supplies dependencies automatically.
The Skill contains usage guidance only. From the project root on the company machine, use the same commands
in PowerShell, macOS and Linux (each line is a separate operation):

```shell
mvn -Ptest-data process-test-classes
mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"
mvn -Ptest-data process-test-classes "-Dexec.args=case TC-TRADE-CREATION-FX_TRF-API-001"
mvn -Ptest-data process-test-classes "-Dexec.args=compact trade_creation"
```

The first command defaults to `check`. Each command first runs the five unit tests of the code that decides what a
case sends and what its response is compared with (`ResponseMaskerTest`, `ExpectedResponsesTest`, `JsonPatchTest`,
`TestDataRepositoryTest`, `JsonLayersTest`; their data is written in the tests themselves, none of it lives under
`src/test/resources`): a failing one stops Maven before the tool reads any data. The commands stop before the
`test` phase: they do not run Cucumber. A failed check fails Maven. Plain `mvn test` retains its existing behavior and does not activate
this profile. The company Maven repository needs `org.codehaus.mojo:exec-maven-plugin:3.6.4`.
After deleting resource files, add `clean` before `process-test-classes` to remove stale copied files.

- `check`: every data file loads, every case of every feature resolves, every row and event file has its feature,
  every API case has its expected file (except those tagged `@expected#none`) and every expected file its case, the rules agree with the expected files,
  every setup name used exists, the expected files resolve and are compacted (a file the compaction would still change
  is a warning), the product Outlines' `Examples` match the registry, every product with a capability has its dat
  file. Exit code 1 on a problem. Run it before committing a change to the data, the features or the registry.
- `sync-examples`: writes the product Outlines' `Examples` from `ProductDatFiles` ("Products and capabilities").
- `compact <event>` or `compact all`: the compaction of "Base and patch".
- `case TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002`: prints the merged data of one case: the applied layers, the dat
  file, the request, the `dynamicFields`, the `expectedBase`, the expected file (full, or patch on which base) and the
  expected response in full.

The unit tests stay out of a plain `mvn test` (the pipeline runs `OreoTest` only). They are JUnit 4, and the profile
fails when none ran: should it report no tests (a JUnit Platform without the vintage engine), add
`org.junit.vintage:junit-vintage-engine` in test scope. Change a rule of the data code together with its test.

### Maintenance

| Task | What to change |
|---|---|
| New product | `ProductDatFiles` constant with its capabilities, and `data/dat/<P>/<P>.dat`; `TestDataTool sync-examples` writes it into the Outlines' Examples; `byProduct` only where its data or dynamic fields differ; run the API cases, compact, review the recorded files, commit (no base to declare: `defaults` covers every product). The skill `.claude/skills/onboarding-e2e-product` walks through it |
| New positive case | nothing when it runs on the defaults; otherwise a row with the feature's ID; after the recording, compact |
| New negative case | a row with the `request` change and `"dynamicFields": null` |
| New API case that checks the database, not the response | the tag `@expected#none` on it (or on its Feature); no expected file |
| New event | `data/<channel>/<event>.json`, named after the feature file; shared blocks through `use`. For its Outlines with `<product_type>`: a `Capability` constant named as in the replay project's `framework/products.ts`, the tag `@capability#<name>` on them, the capability on the products that support it, then `TestDataTool sync-examples` |
| A field turns out dynamic | its rule in the event file (`defaults` or `byProduct`), then record again |
| Change the default counterparty | `api/common.json`, `ui/common.json`, `setup/trade-create*.json` (three places, on purpose); record the API cases again |
| New setup payload | `data/setup/<what-it-prepares>.json`, referenced by that name |

## Layout

```
pom.xml                                   build; profile test-data: the unit tests of the data code, then TestDataTool
azure-pipelines-maven.yml                 the pipeline: mvn test
CLAUDE.md                                 working rules for agents and contributors
docs/standards/                           test layering, feature generation, feature implementation
docs/genie_*_glue_catalog.md              Genie built-in steps
.claude/skills/                           onboarding-e2e-product, maintaining-e2e-test-data
src/main/java/com/example/module/oreo/
  OreoModule, OreoModuleConfiguration     the Genie module and its settings (risk engine mode, trace mode)
  glue/ScenarioContext                    the scenario context
  glue/hooks/                             ScenarioLifecycleHooks (test data into ctx), E2EScenarioHooks,
                                          PlaywrightTraceHooks
  glue/api/                               ApiRequestSteps, ApiResponseAssertionSteps (recorded responses),
                                          TradeApiSteps, BlotterApiSteps, RiskMetricApiSteps, RestServiceResolver
  glue/ui/                                one class per business area (LoginSteps, NewTradeSteps,
                                          TradeLifecycleEventSteps, TradeProvisioningSteps, BlotterSteps, ...),
                                          CommonE2E*, CommonHelpers, ToastRecorder, PlaywrightServiceResolver
  testdata/                               ProductDatFiles + Capability (products), TestData, TestDataRepository,
                                          CaseId, CaseData, SetupData, JsonLayers, JsonPatch, ExpectedResponses,
                                          ExpectedCompaction, ResponseMasker, DataPlaceholders, TradeRequests, ...
  utils/                                  DateUtil, RestApiUtil, DatabaseSqlUtil, CleanupTestDataUtil
src/main/resources/
  snippet/api/*.snippet                   API preconditions, actions and outcomes, one file per event or area
  snippet/ui/{flows,pages,components}/    UI flows, pages and shared components
  cucumber.xml, META-INF/services/
src/test/java/com/example/module/oreo/
  OreoTest                                the Genie runner ("Running")
  testdata/*Test                          unit tests of the data code ("Commands")
  tools/                                  the offline tool ("Commands"): TestDataTool (entry point), Checks,
                                          ExpectedFileChecks, SyncExamples, ProductExamples, Compact, Lookup,
                                          FeatureCases, FeatureFile
src/test/resources/
  features/api/{trading,blotter,reference_data,refdata,risk_management,product_management}/
  features/ui/{trading,blotter,compose,notification,user_management}/
  data/{api,ui,setup,dat}/                the test data ("Test data")
  data/common.json                        left from an earlier design; nothing reads it (kept on purpose)
  elements/{pages,components}/*.json      the element repository (UI locators)
  genie.properties, log4j2.xml            shared settings (addresses and accounts are placeholders)
```

## This copy

**Transcription.** The E2E project's files are typed in from photos as they are, without guessing missing code; only
the company package prefix is written `com.example` and the accounts' e-mail domain `example.com`. Comments that were
not photographed are left out. The project is not built here, and until all files are in, `mvn test` is not expected
to pass. Rename the package to the E2E project's when copying.

**Test data coverage.** Every feature has its case data file except those listed; a case of a feature without one
runs on no case data until the file is written.

| Layer | Event data files | Features without one yet |
|---|---|---|
| API | `trade_creation`, `trade_early_termination`, `trade_allocation`, `refdata_sync_management`, `risk_metric_retrieval` | the other trading lifecycle events, `trade_approval_rejection`, `trade_audit`, `trade_blotter`, `trade_query`, the reference data features |
| UI | `trade_creation`, `trade_early_termination`, `trade_novation_remaining` | the other trading features, `compose_product`, `product_schema_upload`, `user_preference_config`, `websocket_notification` |

The setup files `trade-cancellation` and `trade-amendment` do not exist yet (`KNOWN_MISSING` in `tools/Checks.java`).

**Placeholders** (a comment only), to be replaced by the E2E project's file: `.gitattributes`, `.mcp.json`,
`azure-pipelines-maven.yml`, `META-INF/services/com.example.genie.module.GenieModule` and the dat files
`data/dat/{FX_TRF,FX_PSCRIPT,FX_PSCRIPT_FSKO,FX_PSCRIPT_TRF}`.

**Not in this copy**: `qa.properties`, the environment file `OreoTest` loads, and the other environments' properties
(enrich, pt, sit).
