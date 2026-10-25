# oreo-e2e

End-to-end tests of **OREO**, the FX trade management portal: Java 21 + Cucumber on the Genie framework, in two
layers, `@api` (genie-rest) and `@playwright` (genie-playwright, Edge). Test data is layered JSON per lifecycle event;
API responses are recorded and compared strictly.

This is the public copy: identifiers are anonymized and some files are placeholders or missing ("This copy", below).
It runs only in the company environment. Agents and contributors: read [CLAUDE.md](CLAUDE.md) first.

## Quick start

Prerequisites: JDK 21, Maven with the company Artifactory in `~/.m2/settings.xml`, company network. Install Edge for
Playwright once if missing: `npx playwright install msedge`.

| Goal | Command |
|---|---|
| Check the test data (no browser, no environment) | `mvn -Ptest-data process-test-classes` |
| Run what the runner selects (trade creation only) | `mvn test` |
| Run a selection | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api --tags @ADO#smoke"` |
| Run one case | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"` |
| See what one case sends and is compared with | `mvn -Ptest-data process-test-classes "-Dexec.args=case <case ID>"` |
| Add a product | skill `.claude/skills/onboarding-e2e-product` |
| Change data, features, recordings or the registry | skill `.claude/skills/maintaining-e2e-test-data` |

## Running

The runner `src/test/java/com/example/module/oreo/OreoTest.java` decides the default scope:

- **Fixed tags** `not @wip` and `@ADO#trade_creation`: `mvn test` is **not** the whole suite, and a command-line
  filter is combined with them. An empty run is not a pass.
- **Environment** `src/test/resources/qa.properties`; `genie.properties` holds shared defaults (browser, timeout,
  trace mode, risk engine mode).
- **Parallel**: 3 features at a time in separate JVMs, so scenarios must not share mutable state.

Select with `-Dcucumber.options`: `--tags` (repeated `--tags` must all match) or `--name` (a regular expression on
the scenario name, which starts with the case ID). **Not** `-Dcucumber.filter.*`: unsupported here, it may be
ignored silently and run everything. Every API run books real trades: filter to what you need.

| Tag | Level | Values |
|---|---|---|
| Layer | Feature | `@api`, `@playwright` |
| Business | Feature | `@ADO#<featureName>`, optional group `@ADO#trade_lifecycle` |
| Polarity, suite | Scenario | one of `@ADO#positive` / `@ADO#negative`, one of `@ADO#smoke` / `@ADO#regression` |
| Capability | Product Outline | one `@capability#<name>` |
| No response check | Feature or Scenario | `@expected#none` |
| Exclusion | any | `@wip` |

UI debugging: `PWDEBUG=1` opens the Playwright Inspector; `-Doreo.playwright.trace.mode=ON|RETAIN_ON_FAILURE|OFF`
writes traces to `target/traces` (`npx playwright show-trace <zip>`). Never commit or upload traces.

## How the cases are organised

- **One feature file per lifecycle event and layer** (`features/api/trading/trade_creation.feature`), with one data
  file of the same name (`data/api/trade_creation.json`).
- **The case ID in the scenario name selects the data**: `[TC-TRADE-CREATION-FX_TRF-API-001]` → event
  `trade_creation`, product `FX_TRF`, layer API, case `001`. Before each scenario, `ScenarioLifecycleHooks` merges
  `defaults` → `byProduct[P]` → the case's row → the row's `byProduct[P]` and puts the request into the context.
- **A product is a row, not a file.** Outlines over `<product_type>` carry a `@capability#<name>` tag; their
  `Examples` are written from the registry `ProductDatFiles` by `TestDataTool sync-examples`. A new product is one
  registry constant and one dat file.
- **API cases compare the whole response** with `data/api/expected/<event>/<caseId>.json`, masking run-dependent
  values by `dynamicFields` rules. A missing file is recorded on the first run.
- **UI steps are layered**: scenario (business language) → flow snippet → page / component snippet → element
  repository (locators).

## Layout

```
src/main/java/com/example/module/oreo/
  glue/hooks, glue/api, glue/ui       step definitions and hooks
  testdata/                           data loading, products (ProductDatFiles, Capability), recorded responses
src/main/resources/snippet/
  api/, ui/{flows,pages,components}/  reusable step sequences
src/test/java/com/example/module/oreo/
  OreoTest                            the runner
  testdata/*Test, tools/              unit tests of the data code; the offline tool TestDataTool
src/test/resources/
  features/{api,ui}/<domain>/         feature files
  data/{api,ui,setup,dat}/            test data, recorded responses, dat files
  elements/{pages,components}/        element repository
```

## Documentation

| Document | For |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Working rules: where a change goes, hard stops, checks |
| [docs/test-data.md](docs/test-data.md) | Test data format, recorded responses, `TestDataTool` commands |
| [test-layering-standards.md](docs/standards/test-layering-standards.md) | Story → test points and their layer |
| [bdd-feature-generation-standards.md](docs/standards/bdd-feature-generation-standards.md) | Test points → feature files |
| [bdd-feature-implementation-standards.md](docs/standards/bdd-feature-implementation-standards.md) | Feature files → steps, snippets, glue, data |
| [REST](docs/genie_rest_glue_catalog.md) / [Playwright](docs/genie_playwright_glue_catalog.md) catalogs | Genie built-in steps |

## This copy

- Files are transcribed from photos as they are; only the package prefix (`com.example`) and the e-mail domain
  (`example.com`) are substituted. Missing code is not guessed, so the project is not expected to build here.
- Placeholders (a comment only): `.gitattributes`, `.mcp.json`, `azure-pipelines-maven.yml`,
  `META-INF/services/com.example.genie.module.GenieModule`, and the dat files
  `data/dat/{FX_TRF,FX_PSCRIPT,FX_PSCRIPT_FSKO,FX_PSCRIPT_TRF}`.
- Missing: `qa.properties` and the other environments' properties; the setup files `trade-cancellation` and
  `trade-amendment` (`KNOWN_MISSING` in `tools/Checks.java`).
- Event data files exist for API `trade_creation`, `trade_early_termination`, `trade_allocation`,
  `refdata_sync_management`, `risk_metric_retrieval` and UI `trade_creation`, `trade_early_termination`,
  `trade_novation_remaining`; the other features' cases run without case data until theirs are written.
