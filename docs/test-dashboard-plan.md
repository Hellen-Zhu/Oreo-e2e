# Test Dashboard Plan

**Status: designed, not built** (design agreed 2026-11-23; it replaces the 2026-11-05 plan of a static page fed by
an after-hook). Of everything below, only `coverage --html` exists. Before building, answer the
[open questions](#open-questions): several choices depend on them.

## Why

- The Genie report shows one run, is kept for 90 days and cannot show a trend or what one case did over a month.
- `coverage --html` ([test-data.md](test-data.md)) answers one question at one moment: which product x capability
  cells have cases.
- A test lead needs both, over time, in one place, and has to show them to people who do not read test reports.

## Readers

| Level | Reader | Questions | Language |
|---|---|---|---|
| L1 | Leadership, business | Can we release? Which business flows are at risk? Is quality improving? | Products and lifecycle events, status colours, trend arrows; no case IDs |
| L2 | Test lead | Is the coverage enough? Which cases are unstable? Is a red case a product defect or the environment? | Product x capability matrix, failure classes, red age |
| L3 | Test engineer | Which case failed, where, why? | Case ID, failed step, error, link to the Genie report |

One page per level, each drilling into the next. L1 is one page.

## Measures

| Measure | Reader | Source |
|---|---|---|
| Release readiness per product: Go, Go with risk, No-go, by a written rule (smoke all green, regression pass rate at least an agreed value, no open severe defect, the agreed cells covered) | L1 | Last baseline run, ADO Bugs |
| Business coverage: covered / supported lifecycle events per product, and its trend | L1, L2 | `case_catalog`, `coverage.json` |
| Pass-rate trend of baseline runs, per product | L1, L2 | Baseline runs |
| E2E defects opened and closed, per product | L1 | ADO Bugs |
| Why the red cases are red: product behavior, environment, automation, recording | L1, L2 | Triage in the app |
| Unstable cases, red age, cases not run | L2 | Case results, `case_catalog` |
| Failing and slow steps, hook overhead | L2, L3 | Step and hook results |
| Manual effort saved | L1 | `manDayHours` of the run ([open question](#open-questions)) |

Left out on purpose:

- A pass rate over all runs together: narrow runs and recording runs would dominate it
  ([Which runs count](#which-runs-count)).
- Case counts or run counts as a measure of work done.

The rules (Go / No-go, pass-rate threshold) are agreed with the readers before the page shows them.

## Decisions

| Decision | Why |
|---|---|
| A small web app, not a static page | Failure triage writes back, and the readers need filters and drill-down (the user's decision) |
| No Grafana, no BI tool (Metabase, Power BI) | The user's decision. Consequence: every chart is code, so the app stays small and its pages few |
| Results come from the Genie report API, not from an after-hook | The API already holds run, scenario, step and hook results, one report per run; no test code changes; it allows a backfill of the last 90 days |
| PostgreSQL, a schema of its own (`oreo_qa`) | The instance exists; the pipeline can reach it |
| Test cases stay in git; the database holds a read-only catalog exported from this repository | Genie runs feature files, not database rows; cases keep code review and `TestDataTool check` |
| The app does not parse case IDs; it joins the `[TC-...]` of a scenario name to the catalog | `CaseId` and the product expansion stay defined once, in this repository |
| The app lives in its own internal repository | It has its own build and deployment, and holds connection settings this public copy must not |
| Login by username and password, accounts kept by the app | Simple is enough for an internal QA tool (the user's decision) |

## Genie report API

As observed in an API client, base `https://<genie-report-host>/api`:

| # | Call | Returns |
|---|---|---|
| 1 | `POST /auth/login` with `{"username", "password"}` | A temporary token |
| 2 | `POST /auth/perm-token`, bearer the temporary token | The token for the other calls, its expiry (about two days) and the projects it may read (`authorities`, `project:<projectId>`) |
| 3 | `GET /reports/details/{runIdentifier}` | One run's summary (below) |
| 4 | `POST` *Get All Reports* | The list of runs. Request and paging not seen yet ([open question](#open-questions)) |
| 5 | `POST /reports/details/{runIdentifier}/fragments` with `{"featureName", "status", "tag", "duplication"}` (each may be `null`) | One record per scenario, paged (`pageCurrent`, `pageTotal`, `recordTotal`) |

A token expires, so each sync logs in again. The account and password are pipeline secrets, never in a repository.

**The run summary (3)**, the fields the dashboard uses:

| Field | Use |
|---|---|
| `runIdentifier`, `runStartTime`, `runEndTime` | Key, time axis, duration |
| `status`, `fragmentCount` (`passed`, `failed`, `skipped`, `deferred`, `blocked`, `executedTotal`, `expectedTotal`, `filteredTotal`, `grossTotal`) | Result; `filteredTotal` / `grossTotal` shows how much of the suite the run left out |
| `genieRunArguments` | The run's scope (`--tags ...`) and fork settings |
| `labels` | The run's kind ([Which runs count](#which-runs-count)) |
| `traceability.testSourceDetails` (`branch`, `commitHash`, `hasModifiedFiles`) | Which code ran; `hasModifiedFiles: true` means uncommitted changes |
| `traceability.releaseName` | Release |
| `hostname`, `userId` | A build agent or a person's machine |
| `manDayHours` | Manual effort the run stands for ([open question](#open-questions)) |
| `reportState` | Whether the report is still live |

**A fragment (5)** is one scenario in the Cucumber result model:

- `scenario.name` with the case ID (`[TC-TRADE-FIXINGS-FX_TRF-UI-001] ...`), `scenario.id` (feature file and line),
  `feature.tags` and `scenario.tags`;
- `before` / `after` hooks: `location` (`ScenarioLifecycleHooks.beforeScenario(Scenario)`), `result` (`status`,
  `startDate`, `endDate`);
- `steps`: `keyword`, `name`, `arguments`, `result`, `stepDefinition.location` (a glue method or a snippet), nested
  through `parent` / `isLeaf` for the steps a snippet expands to;
- `embeddings`: type and length only, never content.

## Architecture

```text
Genie report service --API--> sync job --> PostgreSQL (oreo_qa) --> REST API --> pages
this repository --exports---> catalog, coverage.json --^                ^
ADO (Bugs, later) ------------------------------------------------------'
```

One deployable: a Spring Boot service (sync job, REST API) with its pages (Vue and ECharts) built into it.

**Sync job.**

1. Log in (calls 1 and 2).
2. List the runs (call 4) since the last sync; skip the run identifiers already stored.
3. For each new run: its summary (3) and all its fragments (5, every page).
4. Transform and write in one transaction, keyed by `runIdentifier` and `fragmentId`: a second sync of a run replaces
   it, never duplicates it.
5. Mask errors before storing: trade and task IDs, hosts, URLs and accounts become placeholders. No request or
   response bodies, no attachments.

It runs as the last step of the nightly pipeline, and on a schedule (hourly) to catch runs from other machines. Its
first run backfills every report the service still holds, so the trends start with up to 90 days of history.

**Login.** Username and password, accounts kept by the app (passwords hashed), created by an administrator; no
self sign-up. Two roles: `viewer` reads, `qa` also triages and corrects a run's kind. HTTPS only.

## Which runs count

A run's `kind` is derived, and a `qa` user can correct it:

- **`baseline`**: its `labels` hold the baseline label, `branch` is `main`, `hasModifiedFiles` is false and it ran on a
  build agent. Only baseline runs enter trends, release readiness and stability.
- **`adhoc`**: anything else (a narrow run, a branch, local changes). Kept and listed, never averaged in.
- **`recording`**: a run whose purpose was recording new cases (their first run fails by design). Set by hand. Kept,
  never averaged in.

The baseline scope does not exist yet: `OreoTest` fixes `not @wip` and `@ADO#trade_creation`, which is not a full
regression. API cases book real trades, so the scope, environment and trade volume of a baseline run need approval.

## Data model

| Table | One row per | Columns |
|---|---|---|
| `run` | Run | `run_id` (`runIdentifier`), name, start, end, status, the `fragmentCount` counts, labels, scope arguments, branch, commit, `has_modified_files`, host, release, `man_day_hours`, `kind` (derived), `kind_set_by`, report link |
| `case_result` | Scenario of a run | `run_id`, `fragment_id`, `case_id`, feature file, tags, status, start, end, seconds, hook seconds, failed step and its step definition, masked error, `failure_class`, `triaged_by`, `triaged_at` |
| `step_result` (phase 3) | Leaf step of a scenario | `fragment_id`, order, keyword, name, step definition, status, seconds |
| `case_catalog` | Case ID at a commit | `case_id`, event, product, layer, capability, tags, `wip`, feature file, story, first seen, retired |
| `coverage_snapshot` | Product x capability cell per day | product, capability, supported, covered |
| `app_user` | Account | username, password hash, role |

`status` of a scenario: `failed` when a hook or step failed; else `skipped` / `undefined` as Cucumber reports;
else `passed`. `failure_class` is one of the classes of `qa-bdd-test-execution` (`automation`, `recording`,
`behavior`, `environment`), or `unclassified` until triaged; never guessed.

## Exports from this repository

| Export | Content | Status |
|---|---|---|
| `coverage` | `coverage.json` ([test-data.md](test-data.md), "Commands") | Exists |
| `catalog` | One row per case ID after expanding the product `Examples`: event, product, layer (`CaseId`), capability, tags, `@wip`, feature file, story (the feature's `# source:` comment) | To build, a `TestDataTool` command |

The pipeline runs them after each merge to `main` and the app loads the result. Rules about cases stay here: the app
only stores and shows.

`# source:` comments ([bdd-feature-generation-standards.md](standards/bdd-feature-generation-standards.md) §7) are
the only link from a case to its story; no feature carries one yet.

## Pages

| Page | Content | Phase |
|---|---|---|
| Home (L1) | Product x lifecycle event matrix (status of the last baseline run, trend arrow), pass-rate trend, red cases by class, release readiness | 2 |
| Runs | Every run, filtered by kind, branch, label; a run's scope, commit, counts, Genie report link while live | 2 |
| Case history | One case across runs; a case x last 30 baseline runs grid (unstable and long-red cases) | 2 |
| Triage | Failed cases of a run, set their class | 3 |
| Gaps | Catalog cases no baseline run ran in N days; supported cells without cases | 3 |
| Hot spots | Step definitions and snippets that fail most, slowest steps, hook overhead per scenario | 3 |
| Products | Each product's onboarding stage: registered in `ProductDatFiles` → `Examples` synced → API recorded → API green → UI green | 4 |
| Defects, effort | ADO Bugs per product, opened and closed; manual effort saved | 4 |

## Definitions

| Measure | Definition |
|---|---|
| Pass rate (of a baseline run) | Passed / (passed + failed). Skipped, undefined and `@wip` cases are not counted |
| Covered cell | As `coverage` counts it: a supported product x capability cell with at least one case that runs (not `@wip`) |
| Unstable case | Over the last 30 baseline runs, its status changed at least twice between consecutive runs of the same commit |
| Red age | Days since the case's last passing baseline run |
| Not run | A catalog case (not `@wip`) absent from every baseline run of the last N days |
| E2E defect | An ADO Bug whose tags carry a case ID; `qa-defect-reporting` writes the case ID and the product into `System.Tags` |

## Phases

| Phase | Work | What readers get |
|---|---|---|
| 1 | Schema, sync job, backfill | No page yet; history is kept from that day, with up to 90 days before it |
| 2 | Login, Home, Runs, Case history | Trends and the business matrix |
| 3 | `catalog` export, Triage, Gaps, Hot spots | Why cases are red, what is not run |
| 4 | ADO Bugs, Products, effort, release readiness | Go / No-go per product |

## ADO Test Plans

Separate from the dashboard, and kept as a second view for stories:

- `genie-report-ado` (`report.ado.*` in `genie.properties`, off today) writes each run's results into an ADO test
  plan and suite given per run. It matches a scenario to an ADO Test Case by the case ID in the scenario name; the
  `@ADO#` tags become the test case's labels.
- The ADO Test Cases are created from this repository, one per case ID after expanding `Examples`. A new product row
  means new test cases to sync (a step for `onboarding-e2e-product`); a retired one is closed, not deleted.
- A case ID synced to ADO is permanent: renaming it loses its history there.

## Open questions

1. *Get All Reports*: its request (filter by project, time, label?) and paging.
2. A failed step's error: its field and length (runs pass `--maxReasonSizeLimit 2000`).
3. How a run gets its `labels`, so the pipeline can mark baseline runs.
4. What `manDayHours` means (fixed per run or per case) and where it is set.
5. What happens to a report after 90 days: deleted, or kept with another `reportState`.
6. A Genie API account with read access to the project, for the sync job.
7. Where the app runs (platform, HTTPS certificate) and who administers its accounts.
8. The baseline scope, environment and approved trade volume per run.
9. For ADO: the suite structure, and whether a run given a parent suite also records results in its child suites.

## What lives where

- **This repository** (public copy): this plan, the `catalog` and `coverage` exports, synthetic fixtures for their
  tests. Never a host, account, token, real run identifier or result.
- **The app repository** (internal): the sync job, schema, API, pages.
- **Secrets** (pipeline variables or the company's secret store): the Genie account, the database accounts.
