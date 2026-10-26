# Test Data

The reference for the JSON test data, the recorded API responses and `TestDataTool`. The [README](../README.md) has
the overview; the skills `maintaining-e2e-test-data` and `onboarding-e2e-product` have the step-by-step workflows.

## Files

```
src/test/resources/data/
  api/common.json, ui/common.json       shared blocks; they apply only where a file names them in "use"
  api/<event>.json, ui/<event>.json     one per feature file name: its own blocks, defaults, byProduct, case rows
  api/expected/<event>/<caseId>.json    recorded API responses, one per case: in full, or a patch on a base case
  setup/<name>.json                     payloads that prepare a case (trade-create, risk-calc-success-mock ...)
  dat/<PRODUCT>/<PRODUCT>.dat           a product's default dat file; its variants in the same folder
```

API and UI data never read each other; only the code is shared.

## Products and capabilities

`ProductDatFiles` lists every product with its dat file and the capabilities it supports (`Capability`: `create`,
`stepInFull`, `stepInPartial` and one per lifecycle event, `earlyTermination`, `novationRemaining`, `cancellation`,
`allocation`, `amendment`, `partialTermination`, `partialNovationRemaining`, `stepOutFull`, `stepOutPartial`,
`portfolioReassignment`, named as in the replay project's `framework/products.ts`; then `fixings`, `cortexFormula` and
`productUpload`). They also form one group, `Capability.SUPPORT_CAPABILITY`: every constant above (the replay
project's `SUPPORT_CAPABILITY` has the events only). A product names the group, or `except(SUPPORT_CAPABILITY, ...)`
when it lacks some, and any capability outside the group one by one (`FX_TRF(SUPPORT_CAPABILITY)`,
`<P>(except(SUPPORT_CAPABILITY, CANCELLATION))`). A new constant joins the group only if every product that names the group supports it.
Approval, rejection and query are not capabilities: an Outline that approves, rejects or queries what an event made
carries that event's capability (`create`, `stepInFull`, `stepInPartial`, `cancellation`; a query of fixing data
`fixings`). Each
Scenario Outline whose case ID has `<product_type>` carries one tag `@capability#<name>` next to its other tags, and its
`Examples` is one table with the single column `product_type`: one row per product of that capability, in registry
order. Nobody edits those rows; `TestDataTool sync-examples` writes them ("Commands", below).
It prints one line per Outline (`unchanged`, `add X`, `remove Y`, `reordered`, `reformatted`) and writes nothing when a
feature file has a problem: an Outline without a tag or with two, an unknown name, a tag anywhere else, a table other
than one `product_type` column, a capability no product has. `TestDataTool check` fails on those problems and on
every Outline the tool would change; a capability no Outline carries is only a warning.
Scenarios without `<product_type>` (negative and permission cases) carry no tag and are not touched. To leave a product
out of an Outline for a while, take the capability off the product in `ProductDatFiles` and run the tool: a row
commented out, or a blank line, inside a product table stops the tool and fails the check.

## Case data

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
- `use` names a block of `common.json` or of the event file's own `shared` (same shape: `request`, `dynamicFields`,
  `sharedFragments`),
  for data of this event only. A name in both files is an error.
- Objects merge key by key; arrays and values replace whole; `null` removes a key. `_comment` is a comment at any
  depth. To change one element of a list, give the whole list.
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
  whole list with it once in a block of the file, and one line per product that has it:

  ```jsonc
  "shared":    { "target-amount": { "request": { "data": [
                   { "key": "novationDate", "value": "${date:T}", "type": "Date" },
                   { "key": "newTargetAmount", "value": 1000, "type": "Numeric" } ] } } },
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

## Setup data

`data/setup/<name>.json`: `request` and / or `response`, `_comment`, and `extends` (another setup file, merged
under this one, its lists replaced whole; `${date:...}` as in case data). Read by the steps `... from test data '<name>'` and by `TestData.setup("<name>")`; its `request()` /
`response()` fail when the file (with its `extends` chain) has no such part, so a caller never checks for null and
asks `hasRequest()` / `hasResponse()` only when the part is optional. The setup trade
and the API case blocks hold the same values but are kept apart on purpose: what an API case sends must not change
how a UI case prepares its trade. `trade-cancellation` and `trade-amendment` have no file yet (not covered).

## Recorded responses

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

## Base and patch

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
compaction, `TestDataTool compact trade_creation` ("Commands", below; `all` for every event), then turns the variants into
patches. Objects are compared key by key, arrays of the same length element by element, other arrays are replaced
whole; a patch that is not shorter than the full response is not written. Any problem (a base without a file, a patch
that does not apply) stops it before a file is written, and a second run changes nothing. Then review
`git diff` and commit. A change every variant shares (a new response field) shows in the base files only.

Re-record a base together with its variants. A base recorded alone can leave a variant's patch that no longer fits
(an `add` on a key the base now has); that case then fails with an error naming the op. The way out is in the error:
record the case again with `OREO_EXPECTED=update` (update mode does not read the file), then compact.

## Shared parts

Several events return the same part of a trade: creation and query both return `data.trade.basic` and
`data.trade.instrument`. Each is kept once per product in `data/api/expected/_shared/<name>.json`, and an expected file
refers to it at the place of the part, with only the keys that differ:

```jsonc
// expected/trade_creation/TC-TRADE-CREATION-FX_TRF-API-001.json
"trade": { "basic": { "_shared": "basic/FX_TRF" }, "instrument": { "_shared": "instrument/FX_TRF" }, ... }
// another event, the same trade in another state
"basic": { "_shared": "basic/FX_TRF", "status": "Live" }
```

The event data declares which parts are shared, response path -> part name, in any layer; `<product_type>` becomes the
case's product, a case without a product skips such an entry, and `null` removes one entry or the whole map. The block
`trade-response-parts` of `api/common.json` declares the two trade parts; an event whose response holds `data.trade`
names it in `use`:

```jsonc
"defaults": { "use": ["trade-basic", "trade-response-parts"], ... }
```

When the file is read, a reference becomes the shared part with the reference's keys replacing the part's keys of the
same name; the case is compared with that full response, as before. The compaction writes both the shared files and
the references, in a file kept in full (a patch on a base works on the base's full response):

- a part with no file yet is taken from the first case that has it (events, then case IDs, in order);
- a part that lacks a key of the shared one stays in full in the case's file (a reference cannot remove a key), as
  does a part that is not an object (a whole-part placeholder);
- `compact all` takes every part again from the first case that has it and deletes the shared files no case uses;
  `compact <event>` keeps the shared files there are.

A part looks the same in every event only when every event masks its dynamic values with the same rules
(`dynamicFields`): an unmasked date in one event is a key that differs, kept in that event's files. Re-record a shared
part's events together, then `compact all`.

## Commands

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
- `compact <event>` or `compact all`: the compaction of "Base and patch" and "Shared parts".
- `case TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002`: prints the merged data of one case: the applied layers, the dat
  file, the request, the `dynamicFields`, the `expectedBase`, the expected file (full, or patch on which base) and the
  expected response in full.

The unit tests stay out of a plain `mvn test` (the pipeline runs `OreoTest` only). They are JUnit 4, and the profile
fails when none ran: should it report no tests (a JUnit Platform without the vintage engine), add
`org.junit.vintage:junit-vintage-engine` in test scope. Change a rule of the data code together with its test.

## Maintenance

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
