# CLAUDE.md — Agent Operational Spec

This file is the single entry point for any agent or coding session working in this repository. Read it before changing files. It describes the current repository facts, the test/data contracts, the layering rules, and the hard stops for new work.

## 1. Current repository facts

**Repository:** `oreo-e2e` **Artifact:** `oreo-e2e` **Purpose:** Genie E2E coverage for the Example FX trade management platform.

The project has two execution layers:

- `@api` — REST API contract and business-rule tests through `genie-rest`; no browser.
- `@playwright` — browser E2E tests through `genie-playwright`; the default browser in `genie.properties` is Microsoft Edge.

The UI composition layers are:

- **Scenario** — business-language Cucumber steps.
- **Flow snippet** — business workflow composition; no locator or raw UI primitive exposure.
- **Page snippet** — atomic operations and assertions for a page.
- **Component snippet** — reusable cross-page component behavior.

**Runner:** `src/test/java/com/example/module/oreo/OreoTest.java` **Default configuration:** `src/test/resources/genie.properties` **Java:** 21 **Genie:** `5.0.0-RC7-SNAPSHOT` **genie-playwright:** `1.0.0-SNAPSHOT` **Cucumber API:** legacy `cucumber.api`, version managed by Genie **Build:** Maven

The repository uses **JSON test data**, not YAML. The primary data loader is `TestDataRepository`.

> The report metadata in `genie.properties` still contains the identifier `REPORT_ID`. Treat that as a reporting/traceability identifier; do not confuse it with the repository name `oreo-e2e` or change it without a reporting requirement.

### Default runner filter

`OreoTest.java` currently filters out `@wip` and selects `@api` together with `@ADO#trade_novation_remaining`.

The inline comment in that file is stale: this declaration is not an unrestricted API+UI run. Treat the runner declaration as the source of truth for the default scope. If a command-line filter appears to select no scenarios, inspect `OreoTest.java` before changing feature files.

## 2. Repository layout

Use responsibility, not file type alone, to decide where a change belongs.

```text
src/main/java/com/example/module/oreo/
├── glue/
│   ├── hooks/       Scenario lifecycle and context setup
│   ├── api/         API-specific glue and infrastructure
│   ├── ui/          UI-specific glue, provisioning and browser support
│   └── testdata/    JSON repository, case IDs, expected responses and utilities
└── ...

src/main/resources/
├── snippet/
│   ├── api/         API preconditions, actions, lifecycle and reference-data snippets
│   └── ui/
│       ├── flows/       Business workflow composition
│       ├── pages/       Page-level operations and assertions
│       └── components/  Shared cross-page behavior
└── ...

src/test/resources/
├── features/
│   ├── api/{businessDomain}/
│   └── ui/{businessDomain}/
├── data/
│   ├── api/{event}.json
│   ├── ui/{event}.json
│   ├── setup/{name}.json
│   └── api/expected/{event}/{case-id}.json
├── elements/
│   ├── pages/*.json
│   └── components/*.json
├── genie.properties
└── sit.properties
```

The feature tree is the executable coverage source. Current domains include API trading, reference data, product management, risk management and blotter coverage, plus UI trading, compose, notification, blotter and user-management coverage. Verify the actual directories before documenting a new domain.

## 3. Source-of-truth hierarchy for implementation

When implementing a scenario or a test behavior, use this order:

1. Reuse an existing flow, page, component or API snippet.
2. Check the Genie built-in steps:
   - API: `docs/genie_rest_glue_catalog.md`
   - UI: `docs/genie_playwright_glue_catalog.md`
3. Reuse an existing Java step in the appropriate `glue/api` or `glue/ui` package.
4. Add or extend a focused snippet when the behavior is reusable.
5. Add new Java glue only when the built-ins, snippets, element repository and existing glue cannot express the requirement.

Prefer the smallest change that preserves the existing public step language and data contract.

## 4. Test-data contract

### Case data

Case data is loaded from `data/api/*.json` or `data/ui/*.json`. The event file name must match the event portion of the case ID. The repository supports these layers:

- `common.json` — reusable `shared` blocks.
- `defaults` — event-level defaults.
- `byProduct` — product-specific values.
- `cases` — case rows selected by the case ID.
- `use` — ordered references to shared blocks.
- `request` — request data.
- `dynamicFields` — response masking rules.
- `expectedBase` — expected-response inheritance metadata.

A case row must contain a unique, resolvable `id`. Product differences belong in `byProduct`, not in duplicate rows that can match the same case.

Case IDs are taken from the scenario name, not from a Cucumber tag. The expected form is a scenario name containing a token such as `Scenario: [TC-TRADE-CREATION-FX_TRF-API-001] creates a trade`.

`CaseId` maps that token to the channel, event file, product and row. Do not add `@TC-...` tags unless the framework contract explicitly changes.

### Setup data

Setup data is stored in `data/setup/{name}.json`. Its supported top-level fields are `extends`, `request` and `response`. Setup names are referenced by snippets/glue and must match the file name. Reuse an existing provisioning setup instead of manually rebuilding its request.

### API expected responses

Recorded responses are stored in:

```text
data/api/expected/{event}/{case-id}.json
```

A file may contain a complete response or a patch object:

```json
{
  "_base": "<case-id>",
  "_patch": ["..."]
}
```

A patch must use a base case from the same event/channel and must not build on another patch. Dynamic fields and expected response files must agree; use `TestDataValidationTest` to detect drift.

### Product dat files

Every product named in an event-level or row-level `byProduct` block must resolve through `ProductDatFiles` to a classpath dat file.

## 5. Tags, scenario selection and command-line filters

Keep tag purposes distinct:

| Purpose | Current examples |
|---|---|
| Execution layer | `@api`, `@playwright` |
| Suite/polarity | `@ADO#smoke`, `@ADO#regression`, `@ADO#positive`, `@ADO#negative` |
| Business/module selection | `@ADO#trade_creation`, `@ADO#trade_lifecycle` |
| Capability selection | `@capability#create`, `@capability#amendment` |
| Exclusion | `@wip` |

Business and execution tags may be feature-level when they classify the whole feature. Polarity and test-suite tags should be scenario-level so that a suite filter does not accidentally select every scenario in a feature.

Use:

- `--tags` for execution layer, business module, capability or suite selection.
- `--name` for a particular case ID, because the ID is in the Scenario name.
- `-Dcucumber.options`, not `-Dcucumber.filter.tags`; the latter is not the supported filter for this legacy Cucumber setup.

Examples:

```shell
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @api"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @playwright"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--tags @ADO#smoke"
mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-TRADE-CREATION-FX_TRF-API-001\].*$"
```

A case selected by `--name` can still be excluded by the fixed tags in `OreoTest.java`. Do not claim that `mvn test` is a full-suite run without checking the runner configuration.

## 6. UI/API layering rules

### Scenarios

Scenarios express business action, business result and business-rule-compliant state. Do not expose `click`, `type`, `fill`, `select`, selectors, page objects or HTTP protocol details in a new scenario.

### UI flows, pages and components

- Flow snippets compose business workflows and delegate UI primitives to page/component snippets.
- Page snippets own page-level atomic interactions and page assertions.
- Component snippets own behavior reused across pages.
- Scenarios call flow-level business steps; they should not directly call page/component snippets.

A `@playwright` scenario must not call `snippet/api/`. UI preconditions may use the dedicated UI provisioning glue/flow, which can create test data through the API before the browser flow starts. This is API data setup, not an API scenario.

### API behavior

HTTP actions and request bodies belong in API snippets or supported Java infrastructure, never directly in feature files. Request bodies belong in JSON test data; product dat paths belong in `ProductDatFiles`; endpoint resolution belongs in the framework configuration.

## 7. Hard stops for new code

Stop and reconsider if new work would do any of the following:

- Add a Java wrapper for a built-in UI `click`, `type`, `fill`, `select`, navigation or visible/text assertion.
- Hardcode a CSS, XPath, `data-testid` or role selector in a Java step method. Register top-level locators in `elements/pages/*.json` or `elements/components/*.json` and resolve them through the element repository.
- Put a JSON request body, endpoint URL, local dat path or expected response in a feature file.
- Put `When post/get/delete ...` protocol details directly in a feature file.
- Add `@Component` to glue or make a glue class extend another glue/base class.
- Add `Thread.sleep()` to new Java glue. Use framework auto-wait or response-based waiting.
- Add direct RestAssured usage to new glue. Prefer Genie request/response helpers.
- Add a plain `@TC-...` tag to select an individual case; use the Scenario name and `--name`.
- Commit credentials, tokens, generated traces containing sensitive data, or environment-specific secrets.

Existing code may contain legacy direct JsonPath/RestAssured usage, sleeps or UI wrappers. Do not expand those patterns; migrate them separately when requested rather than pretending the repository is already free of legacy code.

## 8. Stop conditions before editing

Escalate instead of guessing when:

- The requested behavior belongs to a domain with no existing glue, snippet or data contract.
- An existing feature is being appended to: do not add another `Feature:`, top-level tag block or `Background:`.
- A proposed case ID or setup name may duplicate an existing one. Search both `data/api` and `data/ui`; duplicate data keys can fail repository validation.
- A provisioning snippet already establishes the required trade state. Use it; do not inline create + approve logic because required headers and context handling can be lost.
- The required product is not present in `ProductDatFiles` or its dat file is missing.

For a new domain, first reuse `ApiRequestSteps`, `ApiResponseAssertionSteps`, `RestServiceResolver` or the corresponding UI infrastructure where applicable. Only then create a focused domain glue class.

## 9. Common trade preconditions

Use existing provisioning steps from `snippet/ui/flows/common_trade_action.snippet` and `snippet/api/common_trade_action.snippet`.

### UI preconditions

These create data through API provisioning; they do not log in or navigate. The default trade reference is `createdTradeId`.

| Step | Result |
|---|---|
| `a Live '<product_type>' trade exists in the blotter` | Live |
| `a Draft Pending Approval '<product_type>' trade exists in the blotter` | Pending Approval |
| `a Dead '<product_type>' trade exists in the blotter` | Dead |
| `a Draft '<product_type>' trade exists in the blotter` | Draft |

### API preconditions

| Step | Result |
|---|---|
| `a '<product_type>' trade is submitted to pending approval via api` | Pending Approval |
| `a live '<product_type>' trade is created via api` | Live |
| `a draft '<product_type>' trade is created via api` | Draft |
| `a dead '<product_type>' trade is created via api` | Dead |

Use the `with ... stored as` variants when a downstream action needs custom context keys.

## 10. Verification commands

Run the smallest relevant check first. PowerShell examples:

```powershell
mvn -B clean compile
mvn -B test -Dtest=TestDataValidationTest
mvn -B test -Dtest=ExpectedResponsesTest
mvn -B test -Dtest=JsonProcessingTest
mvn -B test -Dtest=TestDataRepositoryTest
mvn -B test -Dtest=OreoTest
```

The Surefire configuration excludes several focused `testdata` classes from the ordinary test scan. Running `mvn test` is therefore not equivalent to running every offline data test; invoke excluded classes explicitly when validating the data layer.

For SIT:

```shell
mvn -B test -Dtest=OreoTest "-Dgenie.properties=src/test/resources/sit.properties"
```

For expected-response maintenance:

```powershell
$env:OREO_EXPECTED = "update"
mvn -B test -Dtest=OreoTest
Remove-Item Env:OREO_EXPECTED

mvn -B test -Dtest=CompactExpectedFiles "-Doreo.compact=all"
mvn -B test -Dtest=SyncProductExamples "-Doreo.sync=all"
```

Before committing a test-data change, run `TestDataValidationTest` and review any expected-response or product-example warnings. Do not commit generated browser traces or credentials.
