---
name: maintaining-e2e-test-data
description: Use when the test data, feature files, recorded API responses or product registry of oreo-e2e change, before committing them - "check the test data", "what does case X send", "compact the expected files", "write the Examples", "a product or capability was added", "the API added a field to the response" - or when a data mistake got past mvn test.
---

# Maintaining the test data (oreo-e2e)

## Overview

The offline tools of the test data are normal Java test code in `src/test/java/com/example/module/oreo/tools/` (entry point `TestDataTool.java`, one class per file), run through the Maven `test-data` profile. A plain `mvn test` (the pipeline) runs only the Cucumber suite `OreoTest`: it does not check the data. Run `check` before every commit that touches `src/test/resources/data/`, `src/test/resources/features/`, `ProductDatFiles`, `Capability`, or a step or snippet that names setup data.

Paths are relative to the oreo-e2e root. Run Maven and the tool only where the user says oreo-e2e builds (the company machine); elsewhere hand over the commands.

## Run

Run from the project root on the company machine, with the same commands in PowerShell, macOS and Linux:

```shell
mvn -Ptest-data process-test-classes
mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"
mvn -Ptest-data process-test-classes "-Dexec.args=case TC-TRADE-CREATION-FX_TRF-API-001"
mvn -Ptest-data process-test-classes "-Dexec.args=compact trade_creation"
mvn -Ptest-data process-test-classes "-Dexec.args=suggest trade_creation"
mvn -Ptest-data process-test-classes "-Dexec.args=shared TC-TRADE-CREATION-FX_TRF-API-001"
mvn -Ptest-data process-test-classes "-Dexec.args=coverage"
mvn -Ptest-data process-test-classes "-Dexec.args=coverage --html"
```

Each line is a complete command. The first defaults to `check`; choose one command for the task.
Maven compiles the project, copies test resources and supplies the test classpath automatically.
Before the tool, the profile runs the ten unit tests of the test data code (`src/test/java/.../testdata/*Test`, `tools/*Test`;
their data files are written by the tests, into a temporary folder): a failing one stops Maven before the tool reads any data, and the tool's verdict
is not to be trusted until they pass. Stopping at `process-test-classes` does not execute the Cucumber suite. Do
not replace it with `test` or `verify` for a data-only operation. The commands below are the value of `-Dexec.args`
(`"-Dexec.args=suggest trade_creation"`), not commands of their own: Maven runs the class `TestDataTool` with them, nothing else does. The company Maven repository must supply `exec-maven-plugin:3.6.4`.

| `-Dexec.args=` | What it does |
|---|---|
| `check` | All offline checks (next section). Exit code 1 on a problem |
| `sync-examples` | Writes the `Examples` of every product Outline from `ProductDatFiles`: one line per Outline it changes (`add X`, `remove Y`, `reordered`, `reformatted`), the unchanged ones counted; nothing written when a feature file has a problem |
| `compact <event>` or `compact all` | Turns the recorded responses of the variants into patches on their base and the declared shared parts (`sharedFragments`) into references to `expected/_shared/`; `all` also rebuilds the shared files (from the `sharedSources` events of `api/common.json` first) and deletes unused ones; nothing written on a problem |
| `suggest <event>` | Prints, from the event's recordings, the `dynamicFields` rules they need (values that look generated or changed since the committed recording) with the layer each belongs in, and the `expectedBase` that leaves the fewest characters; writes nothing |
| `case <case ID>` | Prints the merged data of one case: layers, dat file, request, `dynamicFields`, `sharedFragments`, `expectedBase`, expected file and response |
| `shared <case ID>` (or the path of its expected file; `<product_type>` in the ID: every product's case with a recording) | Makes the shared parts the case declares (`sharedFragments`) what its recording holds, then compacts every event again against them: each case still expects what it expected, the keys that differ stay beside its reference. A part the case holds as a placeholder, not an object, is kept; nothing written on a problem |
| `coverage` | Prints the product x capability matrix of the product Outlines (`API/UI` per cell; `-` not supported, `NONE` supported with no case, `*` a row `sync-examples` would add), then the gaps and the cells to sync; writes nothing, never fails |
| `coverage --html` | The same, and writes the matrix as a page, `target/coverage/coverage.html` (tabs by capability group, a row per product, a product's cases on a click; opens offline), with its data, `coverage.json`; generated files, never committed; never fails |

The imports of the files in `tools/` name the package of `ProductDatFiles` (`com.example.module.oreo.testdata` here): in the company copy, the project's own.

## What `check` checks

One `[ok]` or `[FAIL]` line per check, its problems and `[warn]` lines below it:

1. every data file loads (event files, `common.json`, setup files); every `byProduct` product has its dat file;
2. every case of every feature resolves; its event file and channel match the feature;
3. every row and event file has its feature;
4. every API case has its expected file, every expected file its case; a case tagged `@expected#none` (on its Scenario / Outline, Rule or Feature line: it checks the database, not the response) has none, and a file left for it is a problem;
5. the `dynamicFields` rules agree with the expected files (warning: a placeholder without a rule);
6. the expected files resolve and are compacted (warning: a file `compact` would still change);
7. every setup name the code, snippets or features use has its `data/setup/` file, except `trade-cancellation` and `trade-amendment` (not covered yet: the user's decision, `check.knownMissingSetups` in `src/test/resources/test-data.properties`);
8. the product Outlines' `Examples` match the registry;
9. every product with a capability has its default dat file;
10. no feature file runs more cases than its layer's limit, `check.maxCasesPerFeature.<api|ui>` (UI 12, API 30): a
    Scenario counts 1, an Outline one per `Examples` row, `@wip` 0 (warning only: report the feature and propose a
    split by business behavior, standard `bdd-feature-generation-standards.md` §3.3; a split renames case IDs, so it is
    the user's decision);
11. no API snippet only renames a shared outcome of `api/common_api.snippet` (its body one outcome step, or the status
    check of a status an outcome covers, plus `response matches current scenario`): the feature calls the outcome.

## When

| Change | Run (`-Dexec.args=` of the command above) |
|---|---|
| Event or setup data, a row, `byProduct`, `use`, `datFile` | `check`; `case <ID>` to see what a case sends |
| A product or capability in `ProductDatFiles`, a `@capability#` tag | `sync-examples`, review `git diff`, then `check`. A new product: the skill `onboarding-e2e-product` |
| API responses recorded (first run, or `OREO_EXPECTED=update`) | `suggest <event>`, add the rules you agree with and record again; then `compact <event>`, review `git diff`, then `check` |
| The API adds a field to every product's response | Decide from the requirement whether its value is fixed, dynamic or a scenario variable. A dynamic one gets its rule **before** recording, in the layer covering every case that returns it: `trade-response-parts` in `api/common.json` for `basic` / `instrument`, written against the part (`"{basic}.<field>": "type"`: each event's `sharedFragments` gives the path; a case with `"sharedFragments": null` has none of them), else the event's `defaults`, or the products' rows when only some return it (a rule whose path is missing fails the recording). Record every case that returns it (`OREO_EXPECTED=update` as an environment variable; events that share the part together), `suggest <event>`, then `compact all` (not `shared`), review `git diff` (one line per product in `_shared/` or the base files), then `check`. Details: "A new response field" in `docs/test-data.md` |
| The API adds a field to every product's request | Its value in the `use` block or `defaults.request` (a differing product overrides it in its row), `check`, then record and compact as for a new response field |
| A shared part (`basic`, `instrument`) changed in the API on purpose | Record the case that should give it (`OREO_EXPECTED=update`), `shared <that case ID>` (`TC-...-<product_type>-API-001` for every product), review `git diff`, then `check` |
| Feature files | `check` |

## Common mistakes

| Mistake | Instead |
|---|---|
| Running the compiled class directly after a change | Use the full Maven command to refresh classes and resources |
| Stale resource message after deleting a data file | Run `mvn -Ptest-data clean process-test-classes` with the desired `exec.args` |
| Plugin cannot be resolved | Ensure the company Maven repository provides `org.codehaus.mojo:exec-maven-plugin:3.6.4` |
| Every run downloads dependencies the local repository has | The Genie SNAPSHOTs are checked daily and taken again when a new one is published: add `-o` (offline) or `-nsu` (no snapshot updates) to the command, or set `MAVEN_ARGS=-nsu` on the machine; never in `pom.xml` (the pipeline needs the latest). Release jars downloaded again: show the `Downloading from` lines to the user |
| A unit test fails | Fix the code under `testdata/` (or the test, if the rule it checks changed on purpose); never `-DskipTests` to get the tool to run |
| "No tests were executed" | The JUnit Platform has no vintage engine: add `org.junit.vintage:junit-vintage-engine` in test scope |
| A `-D` argument unquoted in PowerShell | Quote each one whole |
| Run from another folder | From oreo-e2e: the tool finds the project through `target/test-classes/data` |
| `Examples` rows typed by hand | `sync-examples` |
| Expected files written or patched by hand | Record them, then `compact` |
| A case that checks the database fails `check` with `no expected file` | Tag it `@expected#none`; never record a response it does not compare |
| A check loosened, or a name added to `check.knownMissingSetups`, to get `[ok]` | Fix the data; that list is the user's decision |
