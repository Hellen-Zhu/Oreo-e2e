# oreo-e2e: the Java E2E project with layered test data

This published copy uses anonymized counterparty and portfolio identifiers, example internal type names, and placeholder environment settings. DAT files are placeholders. Restore approved local test data and configuration in the company environment before running tests; do not commit credentials or real trade data.

The Java + Cucumber (Genie) E2E project's file tree, transcribed from the user's photos, with its test data moved
from YAML to layered JSON case data, setup files and recorded API responses compared strictly (section "Test data").
Design and reasons: `docs/superpowers/specs/2026-10-16-oreo-e2e-test-data-design.md`.

The folders follow the E2E project's layout (`glue`, `testdata`, `utils`, `OreoModule`, `OreoModuleConfiguration`
in `src/main`; `OreoTest`, `data`, `features/ui/trading`, `genie.properties`, `log4j2.xml` in `src/test`). The
package is `com.example.module.oreo` because this repository is public: rename it to the E2E project's package when
copying. Not created, as they need Genie or were not seen: `META-INF/services`, `snippet/`, `cucumber.xml`,
`elements/`, `log4j2.xml`, the other environments' properties.

## Problem it solves

The old format (`data/**/*.yml`) needed one entry per case per product, each repeating a `requestRef` and the
product ID; templates copied each other's whole `basic` block; requests were JSON text inside YAML strings; every API
case carried a hand-copied full response (770 lines for 15 API cases); a missing entry only logged a warning.

## Layout

**Current stage: transcription.** The E2E project's files are typed in from the user's photos as they are, without
guessing missing code and without making the project compile; only the company package prefix is written `com.example`
and the accounts' e-mail domain `example.com`, since this repository is public. Comments the user did not photograph are
left out (only comments are left out). Until all files are in, `mvn test` is not expected to pass.

The folders follow the E2E project. Files marked *placeholder* hold only a comment (Java: an empty class) and are
there so the tree matches; copy the real file over them from the E2E project.

```
pom.xml, .gitignore
.gitattributes, .mcp.json, azure-pipelines-maven.yml, .claude/, docs/   placeholders
.claude/skills/onboarding-e2e-product/   skill: a new product
.claude/skills/maintaining-e2e-test-data/   skill: the offline checks and tools of the test data
                                  (Maven test-data profile, "Commands")
src/main/java/com/example/module/oreo/
  OreoModule.java, OreoModuleConfiguration.java   transcribed from the E2E project
  glue/ScenarioContext.java       transcribed from the E2E project
  glue/ui/PlaywrightServiceResolver.java, CommonE2EInteraction.java, CommonE2EAssertion.java,
          CommonHelpers.java, LoginSteps.java, NewTradeSteps.java, ToastSteps.java,
          TradeDetailSteps.java, TradePortalSteps.java, TradeLifecycleEventSteps.java,
          TradeProvisioningSteps.java   transcribed from the E2E project
  glue/ui/RiskMetricSteps.java    the E2E project's risk metrics mocks, on TestData, both steps in one method
  glue/ui/ToastRecorder.java      records every toast as it appears, so ToastSteps asserts on the record
  glue/api/RestServiceResolver.java, ApiRequestSteps.java, ApiResponseAssertionSteps.java,
          TradeApiSteps.java   transcribed from the E2E project
  glue/hooks/ScenarioLifecycleHooks.java, E2EScenarioHooks.java   transcribed from the E2E project
  glue/hooks/PlaywrightTraceHooks          placeholder
  glue/ui/LoginSteps.java                  "<role> is logged in to the trade portal" (authentication_login.snippet)
  glue/ui/NewTradeSteps.java               trade_creation.snippet
  glue/ui/TradeProvisioningSteps.java      "a Live '<P>' trade exists in the blotter" (API, setup data trade-create)
  glue/ui/TradeLifecycleEventSteps.java    lifecycle_event / common_trade_action / trade_early_termination snippets
  glue/ui/CommonE2EAssertion, CommonE2EInteraction, CommonHelpers, PlaywrightServiceResolver,
          ToastSteps, TradeDetailSteps, TradePortalSteps            placeholders
  testdata/ProductDatFiles.java   the products: dat file and capabilities ("Products and capabilities")
  testdata/Capability.java        what a product can be tested for: the @capability# tag names
  testdata/TestData, TestDataRepository, CaseData, SetupData, CaseId, JsonLayers, Json,
          ExpectedResponses, ResponseMasker, ProjectPaths, TradeRequests   test data and recording ("Test data")
  utils/DateUtil.java, RestApiUtil.java   transcribed from the E2E project
src/main/resources/
  META-INF/services/com.example.genie.module.GenieModule   placeholder (named after the GenieModule interface there)
  snippet/api/common_api.snippet, common_trade_action.snippet, trade_creation.snippet,
          trade_early_termination.snippet, trade_query.snippet,
          snippet/ui/components/*.snippet, snippet/ui/flows/*.snippet,
          snippet/ui/pages/*.snippet, elements/**/*.json,
          pom.xml, genie.properties, log4j2.xml   transcribed from the E2E project
  cucumber.xml                    transcribed from the E2E project
src/test/java/com/example/module/oreo/
  OreoTest.java                   the Cucumber run (features/; glue and report set here)
  testdata/ResponseMaskerTest, ExpectedResponsesTest, JsonPatchTest, TestDataRepositoryTest, JsonLayersTest
                                  unit tests of what decides a case's request and verdict (not in a plain mvn
                                  test); to be deleted after one green run on the company machine
src/test/resources/
  data/api/, data/ui/, data/setup/   the test data ("Test data")
  data/common.json                left from an earlier design; nothing reads it (kept on purpose)
  testdata-fixtures/data/         small data files for the loader's unit tests
  data/dat/<PRODUCT ID>/<PRODUCT ID>.dat   placeholders: FX_PSCRIPT, FX_PSCRIPT_FSKO, FX_PSCRIPT_TRF, FX_TRF
  elements/components|pages/*.json       the files the replay project has (../elements/) copied; the others []
  features/ui/trading/, features/api/trading/   creation and the lifecycle events (API: also checker approval and
                                  query), as in the E2E project; most of their snippets are not here yet
                                  (product column product_type, @capability# tags, Examples in registry order)
  features/api/refdata/, risk_management/   refdata sync, risk metric retrieval, as in the E2E project
  genie.properties, log4j2.xml          settings (addresses and accounts are placeholders)
  enrich, pt, sit .properties, log4j2.xml   placeholders
```

## How a step maps to the E2E project

| E2E project | Here |
|---|---|
| feature line -> FLOW snippet | a Cucumber step method (`steps/`) doing the snippet's lines in order |
| PAGE / COMPONENT snippet, built-in step on `'<element key>'` | a page object method calling `OreoUi` with the same element key |
| `ctx.clickAndWaitForResponse`, `clickWithMockedResponse`, `clickAndCaptureResponse` | the `OreoUi` methods of the same names |
| `RestApiUtil.postMultipart(...)` | `OreoApi.postMultipart(...)` |
| `ctx.riskEngineMode()` | `OreoConfig.riskEngineMode()`; `RiskCalculation.clickUsingMode` = the switch of TradeLifecycleEventSteps |
| `from stored variable 'earlyTermination.amount'` | `ctx.text("earlyTermination.amount")`, injected by the hook |

Taken from the replay project, not seen in the Java code: the login testids, `/checker/tasks/<taskId>/approve`
with `{}`, `/trades/calculate-risk-for-new` for Book (mocked like the trade's calculation), the trade detail check of
"trade is created with pending approval status and info" (trade ID and `PARV` on the header card), and the blotter
check by row and column.

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
| `type` | presence and type of the value |
| `type-tree` | the whole structure, and the type of every leaf |
| `regex:<pattern>` | the value matches the pattern |
| `var:<name>` | the value equals the scenario variable `<name>` of this run (`createdTradeId`, stored by `a live '<product_type>' trade is created via api`) |
| `ignore` | nothing; only when the others cannot work |

A path is keys joined by `.`, `[n]` for one array element, `[*]` for all. A rule whose path is not in the response
fails the recording. Never edit an expected file by hand: change the rule and record again.

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

The offline tool is a normal test-source class:
`src/test/java/com/example/module/oreo/tools/TestDataTool.java`.
The `test-data` Maven profile compiles it, refreshes test resources and supplies dependencies automatically.
The Skill contains usage guidance only. From the project root on the company machine, use the same commands
in PowerShell, macOS and Linux (each line is a separate operation):

```shell
mvn -Ptest-data process-test-classes
mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"
mvn -Ptest-data process-test-classes "-Dexec.args=case TC-TRADE-CREATION-FX_TRF-API-001"
mvn -Ptest-data process-test-classes "-Dexec.args=compact trade_creation"
```

The first command defaults to `check`. These commands stop before test execution: they do not run Cucumber
or unit tests. A failed check fails Maven. Plain `mvn test` retains its existing behavior and does not activate
this profile. The company Maven repository needs `org.codehaus.mojo:exec-maven-plugin:3.6.4`.
After deleting resource files, add `clean` before `process-test-classes` to remove stale copied files.

- `check`: every data file loads, every case of every feature resolves, every row and event file has its feature,
  every API case has its expected file and every expected file its case, the rules agree with the expected files,
  every setup name used exists, the expected files resolve and are compacted (a file the compaction would still change
  is a warning), the product Outlines' `Examples` match the registry, every product with a capability has its dat
  file. Exit code 1 on a problem. Run it before committing a change to the data, the features or the registry.
- `sync-examples`: writes the product Outlines' `Examples` from `ProductDatFiles` ("Products and capabilities").
- `compact <event>` or `compact all`: the compaction of "Base and patch".
- `case TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002`: prints the merged data of one case: the applied layers, the dat
  file, the request, the `dynamicFields`, the `expectedBase`, the expected file (full, or patch on which base) and the
  expected response in full.

```bash
mvn test -Dtest='com.example.module.oreo.testdata.*Test'
```

Runs the five unit tests of the code that decides what a case sends and what its response is compared with
(`ResponseMaskerTest`, `ExpectedResponsesTest`, `JsonPatchTest`, `TestDataRepositoryTest`, `JsonLayersTest`, with
the fixtures in `testdata-fixtures/`); the pom keeps them out of a plain `mvn test`. They are JUnit 4: should the
command report no tests (a JUnit Platform without the vintage engine), add `org.junit.vintage:junit-vintage-engine`
in test scope. They go once they have passed on the company machine.

### Maintenance

| Task | What to change |
|---|---|
| New product | `ProductDatFiles` constant with its capabilities, and `data/dat/<P>/<P>.dat`; `TestDataTool sync-examples` writes it into the Outlines' Examples; `byProduct` only where its data or dynamic fields differ; run the API cases, compact, review the recorded files, commit (no base to declare: `defaults` covers every product). The skill `.claude/skills/onboarding-e2e-product` walks through it |
| New positive case | nothing when it runs on the defaults; otherwise a row with the feature's ID; after the recording, compact |
| New negative case | a row with the `request` change and `"dynamicFields": null` |
| New event | `data/<channel>/<event>.json`, named after the feature file; shared blocks through `use`. For its Outlines with `<product_type>`: a `Capability` constant named as in the replay project's `framework/products.ts`, the tag `@capability#<name>` on them, the capability on the products that support it, then `TestDataTool sync-examples` |
| A field turns out dynamic | its rule in the event file (`defaults` or `byProduct`), then record again |
| Change the default counterparty | `api/common.json`, `ui/common.json`, `setup/trade-create*.json` (three places, on purpose); record the API cases again |
| New setup payload | `data/setup/<what-it-prepares>.json`, referenced by that name |
