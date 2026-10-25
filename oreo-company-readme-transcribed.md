![img.png](img.png)# OreoE2E — OREO Trade Management E2E Test Suite

## 1. What this repository contains

`oreo-e2e` is the Genie end-to-end test suite for **OREO**, the FX Trade Management platform at Example Bank.

The suite covers trade booking and lifecycle workflows, maker/checker actions, allocation, portfolio and termination flows, reference/product/risk functions, blotter behavior and selected UI workflows. The executable coverage is under `src/test/resources/features`; check that tree for the current set of domains rather than relying on a static module list in this document.

### Execution layers

| Layer | Tag | Purpose |
|---|---|---|
| API | `@api` | REST contracts, response assertions and business-rule checks without a browser |
| UI | `@playwright` | Browser workflows through Genie Playwright; the default browser is Microsoft Edge |

### UI composition layers

| Layer | Location | Responsibility |
|---|---|---|
| Scenario | `src/test/resources/features/ui/` | Business-language actions and outcomes |
| Flow snippet | `src/main/resources/snippet/ui/flows/` | Business workflow composition |
| Page snippet | `src/main/resources/snippet/ui/pages/` | Page-level atomic actions and assertions |
| Component snippet | `src/main/resources/snippet/ui/components/` | Reusable behavior shared across pages |

Scenarios should call business-level flow steps. Locator-level operations such as `click`, `fill`, `select` and visibility assertions belong in built-in steps or the appropriate page/component snippet.

## 2. Technology stack

| Component | Version/configuration | Role |
|---|---|---|
| Java | 21 | Test runtime and custom glue |
| Maven | See `pom.xml`; CI currently uses 3.6.3 | Build and test execution |
| Genie | `5.0.0-RC7-SNAPSHOT` | Test framework and runner |
| genie-playwright | `1.0.0-SNAPSHOT` | Browser automation integration |
| genie-rest | Genie-managed | REST API test steps and helpers |
| genie-snippet | Genie-managed | Reusable step sequences |
| Cucumber | Legacy `cucumber.api`; managed by Genie | Feature execution |
| JUnit | `4.13.2` | Test runner and offline checks |
| Playwright browser | Microsoft Edge | Default browser configured in `genie.properties` |

The test runner is:

```text
src/test/java/com/example/module/oreo/OreoTest.java
```

## 3. Quick start

### Prerequisites

- JDK 21 configured through `JAVA_HOME`.
- Maven and access to the Example Bank Artifactory repositories configured in `~/.m2/settings.xml`.
- Example Bank network/VPN access when resolving internal dependencies or connecting to a test environment.
- Node.js and `npx` only when installing or inspecting Playwright browser binaries.
- IntelliJ IDEA with Gherkin and Cucumber for Java plugins is recommended.

### Clone, compile and install the browser

From the repository root:

```powershell
cd C:\Users\example-user\projects\oreo-e2e
mvn -B clean compile
npx playwright install msedge
```

The Maven Surefire configuration sets `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`, so install the browser explicitly when a local machine does not already have the required Playwright browser available.

### Run the offline data validation first

This check does not require a browser or test environment:

```shell
mvn -B test -Dtest=TestDataValidationTest
```

### Run the configured Genie runner

```shell
mvn -B test -Dtest=OreoTest
```

The default tag declaration is in `OreoTest.java` and currently contains `not @wip`, `@api` and `@ADO#trade_novation_remaining`. Therefore, do not describe this command as an unrestricted full-suite run without first checking the runner configuration.

### Credentials and secrets

Environment credentials must be supplied through an approved local or CI mechanism. Do not commit passwords, tokens or copied environment-specific secrets to `genie.properties` or any other tracked file. If a credential has been placed in a tracked file, treat it as exposed and follow the team rotation process.

## 4. Project structure

```text
oreo-e2e/
├── pom.xml
├── azure-pipelines-maven.yml
├── src/
│   ├── main/
│   │   ├── java/com/example/module/oreo/
│   │   │   ├── glue/
│   │   │   │   ├── hooks/       # Scenario lifecycle and context setup
│   │   │   │   ├── api/         # API glue and infrastructure
│   │   │   │   ├── ui/          # UI glue, browser support and provisioning
│   │   │   │   └── testdata/    # JSON data, case IDs and expected responses
│   │   │   └── ...
│   │   └── resources/snippet/
│   │       ├── api/             # API preconditions, actions and lifecycle
│   │       └── ui/
│   │           ├── flows/       # Business workflow composition
│   │           ├── pages/       # Page-level snippets
│   │           └── components/  # Shared component snippets
│   └── test/resources/
│       ├── features/
│       │   ├── api/{businessDomain}/
│       │   └── ui/{businessDomain}/
│       ├── data/
│       │   ├── api/{event}.json
│       │   ├── ui/{event}.json
│       │   ├── setup/{name}.json
│       │   └── api/expected/{event}/{case-id}.json
│       ├── elements/
│       │   ├── pages/*.json
│       │   └── components/*.json
│       ├── genie.properties     # Default environment/configuration
│       └── sit.properties       # SIT overrides
└── docs/
    ├── genie_playwright_glue_catalog.md
    ├── genie_rest_glue_catalog.md
    └── standards/
```

Useful references:

- `docs/genie_rest_glue_catalog.md` — built-in REST steps.
- `docs/genie_playwright_glue_catalog.md` — built-in browser steps.
- `docs/standards/` — feature generation, implementation and layering guidance.
- `CLAUDE.md` — agent and contributor guardrails, current data contracts and hard stops.

## 5. Test data and expected responses

The repository uses JSON, loaded by `TestDataRepository`.

### Case data

Case files are stored under `src/test/resources/data/api/` and `src/test/resources/data/ui/`. A typical event file can contain:

- `defaults` — event-level defaults.
- `byProduct` — product-specific overrides.
- `cases` — case rows selected by ID.
- `use` — shared blocks from `common.json`.
- `request` — request data.
- `dynamicFields` — response masking rules.
- `expectedBase` — expected-response inheritance metadata.

A Feature scenario contains its case ID in the Scenario name:

```gherkin
Scenario: [TC-TRADE-CREATION-FX_TRF-API-001] creates a trade
```

The case ID is not normally a tag. `CaseId` uses it to resolve the channel, event file, product and case row. Use `--name` to run one case; do not use `--tags @TC-...`.

### Setup data

Reusable provisioning data is stored as:

```text
src/test/resources/data/setup/{name}.json
```

Setup files support `extends`, `request` and `response`. Existing provisioning snippets should be reused so that required headers, context references and lifecycle behavior are not accidentally omitted.

### Recorded API responses

Expected API responses are stored in:

```text
src/test/resources/data/api/expected/{event}/{case-id}.json
```

A response can be stored in full, or as a patch on a base case in the same event/channel:

```json
{
  "_base": "<case-id>",
  "_patch": ["..."]
}
```

`TestDataValidationTest` checks that every API case has an expected file and that dynamic-field rules agree with the recorded response.

For an array, the `type` dynamic-field rule records one `${json-unit.any-array}` placeholder: replay requires an array, but does not check its length or elements. `type-tree` instead preserves object keys and array structure, replacing scalar leaves with type placeholders. Objects still require `type-tree` (or `ignore` to skip the value entirely); `type` preserves `null`. Changing a rule does not rewrite existing snapshots: record them again and review the result.

### Product data files

Products referenced by `byProduct` must resolve through `ProductDatFiles` to a classpath `.dat` file. When adding a product, update the product registry and data coverage together; do not invent a dat path in a Feature file.

## 6. Running tests

### Select by tag

Use the legacy Cucumber option property below. `-Dcucumber.filter.tags` is not supported by this project and may be silently ignored.

```shell
# API layer
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api"

# UI layer
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright"

# Suite/polarity tags used by the current features
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#smoke"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#regression"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#positive"

# Business/capability selection; check the feature tags before using a name
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#trade_creation"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @capability#create"
```

When combining filters, use the Cucumber syntax supported by the runner. For example:

```shell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api --tags @ADO#smoke"
```

The fixed tags in `OreoTest.java` can further restrict command-line selection. If a valid tag produces no scenarios, inspect that runner before changing the Feature.

### Select one case by Scenario name

The case ID is embedded in square brackets in the Scenario name, so use `--name`:

```shell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"
```

For a group of cases, use a broader regular expression:

```shell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-\d+\].*$"
```

### Run against SIT

`genie.properties` is the default configuration. `sit.properties` overrides the SIT URLs and JDBC database selection:

```shell
mvn -B test -Dtest=OreoTest "-Dgenie.properties=src/test/resources/sit.properties"
```

Do not hardcode environment URLs in Feature files or glue. Read them from the active properties file.

### Playwright debugging and traces

The default configuration uses Edge, headless mode and a 30-second timeout. For a single UI scenario, Playwright Inspector can be enabled in PowerShell:

```powershell
$env:PWDEBUG = "1"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-UI-001\].*$"
Remove-Item Env:PWDEBUG
```

Trace mode is controlled by `oreo.playwright.trace.mode` (`OFF`, `RETAIN_ON_FAILURE` or `ON`).

```powershell
# Keep traces for every selected UI scenario
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright" "-Doreo.playwright.trace.mode=ON"

# Disable traces for a run
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright" "-Doreo.playwright.trace.mode=OFF"

# Open the first trace generated under target/traces
$trace = Get-ChildItem target\traces\*.zip | Select-Object -First 1
npx playwright show-trace $trace.FullName
```

Do not upload traces containing credentials or sensitive test data to external services.

### Generate locators with Playwright Codegen

Use the browser channel configured by the project and read the active `playwright.base.url`:

```powershell
$playwrightBaseUrl = (Get-Content src/test/resources/genie.properties | Select-String '^playwright.base.url=').ToString().Split('=', 2)[1]
npx playwright codegen --channel=msedge $playwrightBaseUrl
```

Copy locator definitions into `src/test/resources/elements/pages/` or `src/test/resources/elements/components/`; do not hardcode them in new Java glue.

## 7. Maintaining expected data

Record or update an expected response only deliberately and review the resulting diff:

```powershell
$env:OREO_EXPECTED = "update"
mvn -B test -Dtest=OreoTest
Remove-Item Env:OREO_EXPECTED
```

Then compact reusable expected responses and synchronize product Examples when needed:

In Windows PowerShell, quote dotted `-D` properties so that each reaches Maven as a single argument.

```powershell
mvn -B test -Dtest=CompactExpectedFiles "-Doreo.compact=all"
mvn -B test -Dtest=SyncProductExamples "-Doreo.sync=all"
```

Always run the validation after changing case data, product coverage or expected responses:

```shell
mvn -B test -Dtest=TestDataValidationTest
```

## 8. Authoring rules

- Prefer existing snippets and Genie built-in steps before adding Java glue.
- Keep HTTP actions in API snippets, never directly in Feature files.
- Keep request bodies in JSON data and locators in the element repository.
- Do not expose page/component/click/type details in new business scenarios.
- Do not call `snippet/api/` from a `@playwright` scenario. Use the UI provisioning flow when API-created preconditions are required.
- Do not add new `Thread.sleep()`, hardcoded selectors, direct RestAssured calls or Java wrappers for built-in UI primitives.
- Glue classes are plain Cucumber classes: do not add Spring `@Component` or glue inheritance.
- When appending to an existing Feature, do not add another top-level `Feature:`, tag block or `Background:`.
- Check for duplicate case IDs, setup names and rows before adding data; duplicate keys can fail startup or validation.
- Use the existing trade precondition snippets for Live, Draft, Dead and Pending Approval states instead of manually inlining create/approve logic.

Existing legacy wrappers, sleeps or direct response utilities may remain. New code must not expand those patterns; cleanup should be handled as a separate migration.

## 9. Offline verification

The ordinary Maven test scan excludes several focused testdata classes. Run them explicitly when validating the data layer:

```shell
mvn -B clean compile
mvn -B test -Dtest=TestDataValidationTest
mvn -B test -Dtest=ExpectedResponsesTest
mvn -B test -Dtest=JsonProcessingTest
mvn -B test -Dtest=TestDataRepositoryTest
```

These checks do not require a browser or a live environment. They validate repository structure, JSON layering, expected-response resolution and Feature/data consistency.

## 10. Further documentation

- `CLAUDE.md` — operational rules and source-of-truth hierarchy.
- `docs/standards/bdd-feature-generation-standards.md` — generating Feature scenarios.
- `docs/standards/bdd-feature-implementation-standards.md` — implementing steps and snippets.
- `docs/standards/test-layering-standards.md` — API/UI and UI snippet layering.
- `docs/genie_rest_glue_catalog.md` — REST built-in steps.
- `docs/genie_playwright_glue_catalog.md` — Playwright built-in steps.
