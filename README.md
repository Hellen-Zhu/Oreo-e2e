# oreo-e2e

End-to-end tests of **OREO**, the FX trade management portal: Java 21 + Cucumber on the Genie framework, in two
layers, `@api` (genie-rest) and `@playwright` (genie-playwright, Edge). Test data is layered JSON per lifecycle event;
API responses are recorded and compared strictly.

This is the public copy: identifiers are anonymized and some files are placeholders or missing ("This copy", below).
It runs only in the company environment. Agents and contributors: read [CLAUDE.md](CLAUDE.md) first.

## Quick start

### Prerequisites

- JDK 21 configured through `JAVA_HOME`.
- Maven, with access to the company Artifactory repositories configured in `~/.m2/settings.xml` (never committed).
- Company network or VPN access when resolving internal dependencies or connecting to a test environment.
- Node.js and `npx` only when installing or inspecting Playwright browser binaries (and for the trace viewer).
- IntelliJ IDEA with the Gherkin and Cucumber for Java plugins is recommended.

### Clone, compile and install the browser

From the repository root:

```shell
mvn -B clean compile
npx playwright install msedge
```

The Maven Surefire configuration sets `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`, so install the browser explicitly when a
local machine does not already have the required Playwright browser available.

### Run the offline data validation first

This check does not require a browser or a test environment:

```shell
mvn -Ptest-data process-test-classes
```

It runs the test-data unit tests, then `TestDataTool check`. There is no `TestDataValidationTest` any more.

### Run the configured Genie runner

```shell
mvn -B test -Dtest=OreoTest
```

The default tag declaration is in `src/test/java/com/example/module/oreo/OreoTest.java` and currently contains
`not @wip` and `@ADO#trade_creation`. Therefore, do not describe this command as an unrestricted full-suite run
without first checking the runner configuration ("Running", below). API runs book real trades.

### Credentials and secrets

Environment credentials must be supplied through an approved local or CI mechanism. Do not commit passwords, tokens
or copied environment-specific secrets to `genie.properties` or any other tracked file. If a credential is ever
placed in a tracked file, treat it as exposed and follow the team rotation process.

### Common commands

| Goal | Command |
|---|---|
| Check the test data (no browser, no environment) | `mvn -Ptest-data process-test-classes` |
| Run what the runner selects (trade creation only) | `mvn test` |
| Run a selection | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api --tags @ADO#smoke"` |
| Run one case | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"` |
| See what one case sends and is compared with | `mvn -Ptest-data process-test-classes "-Dexec.args=case <case ID>"` |
| Update a case's recorded response | [docs/test-data.md, "Updating a recorded response"](docs/test-data.md#updating-a-recorded-response) |
| Update a shared part (`basic`, `instrument`) | [docs/test-data.md, "Updating a shared part"](docs/test-data.md#updating-a-shared-part) |
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
  `Examples` are written from the registry `ProductDatFiles` by `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"`. A new product is one
  registry constant and one dat file.
- **API cases compare the whole response** with `data/api/expected/<event>/<caseId>.json`, masking run-dependent
  values by `dynamicFields` rules. A missing file is recorded on the first run.
- **UI steps are layered**: scenario (business language) → flow snippet → page / component snippet → element
  repository (locators).

## Updating recorded responses

Step-by-step procedures, with the commands, in [docs/test-data.md](docs/test-data.md):

- [Updating a recorded response](docs/test-data.md#updating-a-recorded-response): the API changed a response on
  purpose and a case fails its comparison (re-record in update mode, compact, check, review, run again).
- [Updating a shared part](docs/test-data.md#updating-a-shared-part): `basic` / `instrument` in
  `data/api/expected/_shared/` has to change, because the API changed it (`shared <case ID>`) or because a masking
  rule or a new field changed it (`compact all`).

## Layout

```
src/main/java/com/example/module/oreo/
  glue/hooks, glue/api, glue/ui       step definitions and hooks
  playwright/                         the UI context of the steps (PlaywrightServiceResolver) and its helpers
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
| [ai-workflow.md](docs/ai-workflow.md) | The agent pipeline: `/writebddfeatures`, `/codetestcases`, their agents and skills |
| [test-dashboard-plan.md](docs/test-dashboard-plan.md) | Plan, not scheduled: a dashboard of run history and coverage for the test lead |

## This copy

- Files are transcribed from photos as they are; only the package prefix (`com.example`) and the e-mail domain
  (`example.com`) are substituted. Missing code is not guessed, so the project is not expected to build here.
- Placeholders (a comment only): `.gitattributes`, `.mcp.json`, `azure-pipelines-maven.yml`,
  `META-INF/services/com.example.genie.module.GenieModule`, and the dat files
  `data/dat/{FX_TRF,FX_CO,FX_FSB,FX_PSCRIPT_TRF}`.
- Missing: `qa.properties` and the other environments' properties; the setup files `trade-cancellation` and
  `trade-amendment` (`KNOWN_MISSING` in `tools/Checks.java`).
- Event data files exist for API `trade_creation`, `trade_early_termination`, `trade_allocation`,
  `refdata_sync_management`, `risk_metric_retrieval` and UI `trade_creation`, `trade_early_termination`,
  `trade_novation_remaining`; the other features' cases run without case data until theirs are written.
