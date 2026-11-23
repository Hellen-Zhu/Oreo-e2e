# CLAUDE.md

Working rules for agents and contributors in `oreo-e2e`, the Genie E2E suite of the OREO FX trade management portal.
Read this before changing files. It holds the rules and the traps; the details live in the documents it points to.

## 1. Facts that are easy to get wrong

- **`mvn test` is not the whole suite.** `OreoTest` fixes the tags `not @wip` and `@ADO#trade_creation`; a command-line
  filter is combined with them. Read `src/test/java/com/example/module/oreo/OreoTest.java` before describing a run's
  scope or reading an empty run as a pass.
- **`SeedTest` is not a test.** It books the SIT seed for UAT (`docs/sit-seed.md`), runs only with `-Dtest=SeedTest`
  (surefire excludes it from `mvn test`) and reads `seed/features`, outside `features/`.
- **Select with `-Dcucumber.options`** (`"--tags ..."`, `"--name ^\[TC-...\].*$"`). Never `-Dcucumber.filter.*`: not
  supported here, possibly ignored silently, running everything.
- **The environment is `qa.properties`** (`@GenieOptions`), or the file of `-Dgenie.properties=./src/test/resources/<env>.properties`
  (dev, sit, qa, uat, pt); `genie.properties` holds shared defaults (Edge, headless,
  trace mode, risk engine mode, element paths).
- **Features run in parallel**: 3 at a time, in separate JVMs. A scenario may not depend on another or on shared mutable
  state (a fixed trade, portfolio or user setting another feature changes). How Genie forks: `docs/genie-parallel-execution.md`.
- **API runs book real trades** and a case without a recording writes one into `src` and fails once. Run the narrowest
  filter that proves the change.
- **The case ID is in the scenario name** (`[TC-TRADE-CREATION-FX_TRF-API-001]`), never a tag; it selects the event
  file, product, layer and row of the test data.
- **Products are rows.** `Examples` of `@capability#` Outlines are written by `sync-examples`, never by hand; a product
  is a composition (`FX_CO`), not a product ID (`FX_PSCRIPT`). Details: `docs/test-data.md`, "Products and capabilities".
- **The data checks are `mvn -Ptest-data process-test-classes`** (unit tests, then `TestDataTool check`); every
  `TestDataTool` command and what `check` covers: `docs/test-data.md`, "Commands". There is no
  `TestDataValidationTest`, `CompactExpectedFiles` or `SyncProductExamples`.
- **The test data code has two layers, one direction.** `testdata/` (`src/main`) loads case, setup and expected data
  for the glue; `tools/` (`src/test`, `TestDataTool`) checks and rewrites it offline and uses `testdata/`. Neither
  imports glue, `playwright`, `config` or the API clients (`utils.DateUtil` only); the glue calls `testdata/`'s public
  API and never `tools/`. Decisions about the data, not code, go to `src/test/resources/test-data.properties`.

## 2. Where to look

| Task | Read |
|---|---|
| Turn a story into test points, choose API or UI | `docs/standards/test-layering-standards.md` |
| Write or append feature files | `docs/standards/bdd-feature-generation-standards.md` |
| Implement steps, snippets, glue, data; verify | `docs/standards/bdd-feature-implementation-standards.md` |
| Built-in steps | `docs/genie_rest_glue_catalog.md`, `docs/genie_playwright_glue_catalog.md` |
| Parallel runs: fork mode, fork count, working directory, child logs | `docs/genie-parallel-execution.md` |
| Test data formats, recorded responses, the tool's commands | `docs/test-data.md` |
| Change data, features, recordings or the registry | skill `maintaining-e2e-test-data` |
| Add a product | skill `onboarding-e2e-product` |
| Book the SIT seed for UAT, its catalog, its report | `docs/sit-seed.md` |
| Raise a defect a run found (ADO Bug) | skill `qa-defect-reporting`; settings in `ado-defects.local.properties` (git-ignored) |
| Story → feature files, feature files → running cases | `/writebddfeatures`, `/codetestcases`; overview `docs/ai-workflow.md` |

The executable truth is the code and the feature tree; when a document disagrees with them, trust the code and fix the
document. Each fact lives in one place: rules and facts in this file, `docs/standards/` and `docs/test-data.md`;
skills, agents and commands hold only their procedure and output and link to those. Change a rule where it lives and
never copy it into a skill.

## 3. Where a change goes

Stop at the first that fits:

1. An existing flow, page, component or API snippet expresses it → reuse it.
2. A Genie built-in does it → use it inside a snippet.
3. Existing glue does it (`glue/api`, `glue/ui`) → call it from a snippet.
4. Compose a new snippet from the above.
5. Only then new Java, for one capability none of the above has.

| What | Where |
|---|---|
| Business step a UI scenario calls | `snippet/ui/flows/` (no element keys, no UI primitives) |
| UI precondition that only provisions through the API | a Java step of `PlaywrightTradeProvisioningSteps` worded as business (`... exists in the blotter`), called by the feature with no snippet around it |
| Atomic page action / assertion | `snippet/ui/pages/<page>_page.snippet` |
| Dialog, toast, panel used on several pages | `snippet/ui/components/<component>_component.snippet` |
| API action / outcome | `snippet/api/`; a request starts with `start a new api request as user '<role>'` (`maker`, ...) |
| Header every API request needs | that step (`api/common_api.snippet`) and `RestApiUtil`, nowhere else |
| Locator | `src/test/resources/elements/{pages,components}/*.json` |
| Request body | `data/{api,ui}/<event>.json` (case), `data/setup/<name>.json` (preconditions) |
| Product (a composition: its product ID, capabilities), its dat file | `ProductDatFiles` + `data/dat/<C>/<C>.dat` |
| Address, account, mode | the properties files, never code or features; accounts by role through `config/TestUser` |

Trade preconditions and the API outcomes already exist; reuse them instead of inlining create + approve (headers and
context keys get lost): `docs/standards/bdd-feature-implementation-standards.md` §3.3.

## 4. Hard stops

Never in new work:

- HTTP verbs, endpoints, bodies, dat paths, expected responses, selectors or UI primitives in a feature file.
- A `@playwright` scenario calling an API snippet (use the UI provisioning steps).
- An element key or UI primitive in a flow snippet; a state change inside an assertion.
- A Java wrapper around a built-in UI step, a selector hard-coded in Java, or a test ID built from runtime values.
- `Thread.sleep()`, direct RestAssured, `@Component` on glue, or glue inheritance.
- `PlaywrightAssertions` or `Locator.waitFor` options in glue: wait and assert through a
  `PlaywrightServiceResolver.Context` method, added in the `playwright` package when missing.
- A hand-edited expected file or `Examples` row (record + compact; `sync-examples`).
- Two rows matching one case, a duplicated setup name or case ID, an unregistered product.
- A `@TC-...` tag, a feature-level suite or polarity tag, or removing `@wip` to force a run.
- Credentials, tokens, environment addresses, real trade data or Playwright traces in a commit.

Existing code has legacy wrappers, sleeps and direct JsonPath/RestAssured. Do not copy them, and do not refactor them as
a side effect of another change.

## 5. Stop and ask instead of guessing

- The behavior belongs to a domain with no glue, snippet or data yet.
- A product, capability, data value, masking rule or business rule is not given: report it as open, never invent it.
- A product is missing from `ProductDatFiles` or its dat file is missing.
- A requirement and a recorded response disagree (a recording shows what the system did, not what it should do).
- Appending to an existing feature: add Scenario blocks only (no `Feature:`, feature tags or `Background:`); continue
  the numbering of the case-ID prefix after checking features **and** `data/api`, `data/ui`.

## 6. Before you finish

- Offline: `mvn -Ptest-data process-test-classes` passes with no new problem (`"-Dexec.args=case <ID>"` to inspect one
  case).
- Against the environment (company machine only), the narrowest selection:
  `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-...\].*$"`; new scenarios pass twice in a row.
- Read every new recording against the requirement; mask dynamic values with `dynamicFields` rules, then
  `compact <event>` and review `git diff`.
- Scan the diff for credentials, addresses, company names and real trade or task IDs.

## 7. This repository

- Everything in the repo is written in English. Four exceptions, on the user's decision, all in Chinese for the team:
  `docs/demo/oreo-e2e-demo.html` (the demo script), `docs/demo/oreo-ai-workflow.html` (the agent workflow
  explainer; its English counterpart is `docs/ai-workflow.md`), `docs/demo/oreo-sit-seed-demo.html` (the SIT seed
  demo; its English counterpart is `docs/sit-seed.md`) and `docs/demo/oreo-kt-deck.html` (the onboarding KT slides
  for a new QA engineer, with speaker notes; it summarizes this file and the documents of section 2; its English
  counterpart, as Markdown to paste into Confluence one part per page, is `docs/demo/oreo-kt-confluence.md`).
- This is the public copy. It builds and runs only on the company machine: elsewhere, give the commands instead of
  running them. Never commit a server address, IP, account, password, company name or real trade / task ID.
- Transcribed files stay as typed in (only the `com.example` package and the `example.com` e-mail domain are
  substituted); do not complete missing code by guessing. `oreo-company-*-transcribed.md` are reference copies of the
  company's documents: do not edit them.
- `.dat` files are one-line placeholders; never commit a real dat file. `check.knownMissingSetups` (`test-data.properties`) is
  changed only on the user's decision.
- Never force-push; never push to or merge into `main` without asking.
