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

It runs trade creation only and books real trades: read "Running", below, before you rely on it.

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

### Test data commands (`TestDataTool`)

Every command of the offline tool (`check`, `sync-examples`, `case`, `suggest`, `compact`, `shared`, `unused-steps`,
`coverage`, with `--by productId` or `--by tradeType` to count by product ID or trade type), with what it does and what `check` covers: [docs/test-data.md, "Commands"](docs/test-data.md#commands).

## Running

`mvn test` is **not** the whole suite, an empty run is not a pass, and every API run books real trades. Before
running, read the runner's settings and the selection syntax:
[implementation standard §6.2](docs/standards/bdd-feature-implementation-standards.md#62-against-an-environment);
the tags: [generation standard §5.2](docs/standards/bdd-feature-generation-standards.md#52-tags).

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
  repository (locators). A precondition that only provisions through the API is a business-worded Java step
  (`a Live '<product_type>' trade exists in the blotter`) the scenario calls directly: it has no UI to layer.

## Layout

```
src/main/java/com/example/module/oreo/
  OreoModule                          the Genie module: registers the suite's configuration
  config/                             the configuration (OreoModuleConfiguration), its resolver, the test users by
                                      role (TestUser)
  glue/hooks, glue/api, glue/ui       step definitions and hooks; glue/ScenarioContext, the base of the steps' context
  playwright/                         the UI context of the steps (PlaywrightServiceResolver) and its helpers
  utils/                              API clients the glue calls (TradeApiClient, ProductApiClient, RestApiUtil), dates
                                      (DateUtil), database queries and cleanup
  testdata/                           data loading, products (ProductDatFiles, Capability), recorded responses;
                                      no glue imports (CLAUDE.md, section 1)
src/main/resources/
  snippet/api/, snippet/ui/{flows,pages,components}/  reusable step sequences
  cucumber.xml, META-INF/             Spring and Genie module wiring
src/test/java/com/example/module/oreo/
  OreoTest                            the runner
  testdata/*Test, tools/              unit tests of the data code; the offline tool TestDataTool (uses testdata/)
src/test/resources/
  features/{api,ui}/<domain>/         feature files
  data/{api,ui,setup,dat}/            test data, recorded responses, dat files
  elements/{pages,components}/        element repository
  genie.properties                    shared defaults (browser, timeouts, users, modes); an environment's file overrides
  test-data.properties                decisions about the data the checks read (CLAUDE.md, section 1)
  tools/                              the page template of coverage --html
```

## Documentation

The map of the documents and skills is [CLAUDE.md §2](CLAUDE.md#2-where-to-look). Run results are also kept by the
QA dashboard, a separate internal project that reads them from the Genie report service: it relies on the case IDs in
scenario names, and its only link to this repository is a step of the test pipeline.

## This copy

- Files are transcribed from photos as they are; only the package prefix (`com.example`) and the e-mail domain
  (`example.com`) are substituted. Missing code is not guessed, so the project is not expected to build here.
- Placeholders (a comment only): `.gitattributes`, `.mcp.json`, `azure-pipelines-maven.yml`,
  `META-INF/services/com.example.genie.module.GenieModule`, and the dat files
  `data/dat/<C>/<C>.dat` of every composition of `ProductDatFiles` and `seed/dat/<C>/<productId>.dat`.
- Missing: `qa.properties` and the other environments' properties; the setup file `trade-cancellation`
  (`check.knownMissingSetups` in `test-data.properties`).
- Event data files exist for API `trade_creation`, `trade_early_termination`, `trade_allocation`,
  `refdata_sync_management`, `risk_metric_retrieval` and UI `trade_creation`, `trade_early_termination`,
  `trade_novation_remaining`; the other features' cases run without case data until theirs are written.
