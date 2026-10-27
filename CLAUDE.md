# CLAUDE.md

Working rules for agents and contributors in `oreo-e2e`, the Genie E2E suite of the OREO FX trade management portal.
Read this before changing files. It holds the rules and the traps; the details live in the documents it points to.

## 1. Facts that are easy to get wrong

- **`mvn test` is not the whole suite.** `OreoTest` fixes the tags `not @wip` and `@ADO#trade_creation`; a command-line
  filter is combined with them. Read `src/test/java/com/example/module/oreo/OreoTest.java` before describing a run's
  scope or reading an empty run as a pass.
- **Select with `-Dcucumber.options`** (`"--tags ..."`, `"--name ^\[TC-...\].*$"`). Never `-Dcucumber.filter.*`: not
  supported here, possibly ignored silently, running everything.
- **The environment is `qa.properties`** (`@GenieOptions`); `genie.properties` holds shared defaults (Edge, headless,
  trace mode, risk engine mode, element paths).
- **Features run in parallel**: 3 at a time, in separate JVMs. A scenario may not depend on another or on shared mutable
  state (a fixed trade, portfolio or user setting another feature changes).
- **API runs book real trades** and a case without a recording writes one into `src` and fails once. Run the narrowest
  filter that proves the change.
- **The case ID is in the scenario name** (`[TC-TRADE-CREATION-FX_TRF-API-001]`), never a tag; it selects the event
  file, product, layer and row of the test data.
- **Products are rows.** `Examples` of `@capability#` Outlines are written by `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"` from
  `ProductDatFiles`; never by hand.
- **The data checks are `mvn -Ptest-data process-test-classes`** (six unit tests, then `TestDataTool check`). There is
  no `TestDataValidationTest`, `CompactExpectedFiles` or `SyncProductExamples`: they became `TestDataTool` commands.

## 2. Where to look

| Task | Read |
|---|---|
| Turn a story into test points, choose API or UI | `docs/standards/test-layering-standards.md` |
| Write or append feature files | `docs/standards/bdd-feature-generation-standards.md` |
| Implement steps, snippets, glue, data; verify | `docs/standards/bdd-feature-implementation-standards.md` |
| Built-in steps | `docs/genie_rest_glue_catalog.md`, `docs/genie_playwright_glue_catalog.md` |
| Test data formats, recorded responses, the tool's commands | `docs/test-data.md` |
| Change data, features, recordings or the registry | skill `maintaining-e2e-test-data` |
| Add a product | skill `onboarding-e2e-product` |
| Story → feature files, feature files → running cases | `/writebddfeatures`, `/codetestcases`; overview `docs/ai-workflow.md` |

The executable truth is the code and the feature tree; when a document disagrees with them, trust the code and fix the
document.

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
| Atomic page action / assertion | `snippet/ui/pages/<page>_page.snippet` |
| Dialog, toast, panel used on several pages | `snippet/ui/components/<component>_component.snippet` |
| API action / outcome | `snippet/api/`; a request starts with `start a new api request as user '<user>'` |
| Header every API request needs | that step (`api/common_api.snippet`) and `RestApiUtil`, nowhere else |
| Locator | `src/test/resources/elements/{pages,components}/*.json` |
| Request body | `data/{api,ui}/<event>.json` (case), `data/setup/<name>.json` (preconditions) |
| Product, its dat file, its capabilities | `ProductDatFiles` + `data/dat/<P>/<P>.dat` |
| Address, account, mode | the properties files, never code or features |

Trade preconditions already exist; reuse them instead of inlining create + approve (headers and context keys get
lost): `a live|draft|dead '<product_type>' trade is created via api`, `a '<product_type>' trade is submitted to
pending approval via api` (API); `a Live|Draft|Dead|Draft Pending Approval '<product_type>' trade exists in the
blotter` (UI: provisions through the API, neither logs in nor navigates; the trade is in `createdTradeId`).

## 4. Hard stops

Never in new work:

- HTTP verbs, endpoints, bodies, dat paths, expected responses, selectors or UI primitives in a feature file.
- A `@playwright` scenario calling an API snippet (use the UI provisioning steps).
- A Java wrapper around a built-in UI step, or a selector hard-coded in Java.
- `Thread.sleep()`, direct RestAssured, `@Component` on glue, or glue inheritance.
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

- Everything in the repo is written in English. Two exceptions, on the user's decision, both in Chinese for the team:
  `docs/demo/oreo-e2e-demo.html` (the demo script) and `docs/demo/oreo-ai-workflow.html` (the agent workflow
  explainer; its English counterpart is `docs/ai-workflow.md`).
- This is the public copy. It builds and runs only on the company machine: elsewhere, give the commands instead of
  running them. Never commit a server address, IP, account, password, company name or real trade / task ID.
- Transcribed files stay as typed in (only the `com.example` package and the `example.com` e-mail domain are
  substituted); do not complete missing code by guessing. `oreo-company-*-transcribed.md` are reference copies of the
  company's documents: do not edit them.
- `.dat` files are one-line placeholders; never commit a real dat file. `KNOWN_MISSING` (`tools/Checks.java`) is changed
  only on the user's decision.
- Never force-push; never push to or merge into `main` without asking.
