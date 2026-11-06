# Test Dashboard Plan

**Status: a plan, not scheduled** (decided 2026-11-05). Of everything below, only `coverage --html` exists. When the
work is scheduled, answer the [open questions](#open-questions) first: several choices depend on them.

## Why

- The Genie report records one run. It is a page to read, not data to compute on, and it is kept for 90 days. It cannot
  show a trend, or what one case has done over the last month.
- `coverage --html` ([test-data.md](test-data.md)) answers one question at one moment: which product x capability cells
  have cases.
- A test lead needs both, over time, in one place.

## The questions it answers

| Question | Measure | Source |
|---|---|---|
| Can we release? | The last baseline run on the target environment, per product: red cases and their failure class | Run records |
| Is the coverage enough? | Covered / supported cells per product and the trend; each product's onboarding stage | `coverage.json` of each baseline run |
| Can the suite be trusted? | Flaky cases, failures by class, how long each red case has been red | Run records |
| Is quality improving? | E2E defects opened and closed, per product | ADO Bugs, linked by case ID |

Left out on purpose:

- A pass rate over all runs together. Narrow runs and recording runs would dominate it
  ([Which runs count](#which-runs-count)).
- Case counts as a measure of work done.

## Data before pages

History cannot be rebuilt later. Each run that is not archived leaves a gap in every trend. So the first piece is a
lasting, structured record of each run, and pages are generated from the records. The pages can change at any time;
the records have to start early.

### The run record

One JSON file per run. The values below are synthetic:

```json
{
  "run": "2026-11-05T02-00Z-qa-nightly",
  "started": "2026-11-05T02:00:00Z",
  "finished": "2026-11-05T02:41:10Z",
  "env": "qa",
  "kind": "nightly",
  "scope": "--tags not @wip",
  "commit": "a044293",
  "branch": "main",
  "build": null,
  "cases": [
    { "id": "TC-TRADE-CREATION-FX_TRF-API-001", "event": "TRADE-CREATION", "product": "FX_TRF", "layer": "API",
      "status": "passed", "seconds": 12.4, "attempt": 1 },
    { "id": "TC-TRADE-CANCELLATION-UI-002", "event": "TRADE-CANCELLATION", "product": null, "layer": "UI",
      "status": "failed", "seconds": 61.0, "attempt": 1,
      "class": "environment", "error": "TimeoutError: waiting for <locator> exceeded 30000 ms" }
  ]
}
```

| Field | Rule |
|---|---|
| `kind` | `nightly`, `adhoc` or `recording` ([Which runs count](#which-runs-count)) |
| `scope` | The exact selection of the run, as passed to `-Dcucumber.options`, together with `OreoTest`'s fixed tags |
| `commit`, `branch` | This repository at the time of the run |
| `build` | The environment's deployed version, when it can be read; `null` otherwise |
| `event`, `product`, `layer` | From the case ID (`CaseId.parse`, `TC-<EVENT>[-<PRODUCT>]-<API\|UI>-<NNN>`); no extra tags |
| capability | Not stored: a case ID joins the cell of `coverage.json` whose `cases` list it. A case without a product (a Scenario, not a product Outline) has none, as in `coverage` |
| `status` | `passed`, `failed`, `skipped` or `undefined` |
| `class` | Failed cases only: one of the four classes of `qa-bdd-test-execution`, `automation`, `recording`, `behavior`, `environment`. Set by rule only where the signal is unambiguous (no expected file: `recording`; connection refused or login failure: `environment`); otherwise `unclassified` until someone triages it. Never guessed |
| `error` | The exception type and its first line, masked: trade and task IDs, hosts, URLs and accounts become placeholders. Never a request or response body |
| `attempt` | One entry per attempt when a case is retried in the run; needed to tell a flaky case |

### Where the record comes from

`ScenarioLifecycleHooks` (`glue/hooks`) already writes `target/results/failed-scenarios-<time>.log`: failed scenarios
only, one file per fork JVM. There are two sources:

1. The same after-hook also writes one line per scenario, passed ones included, to
   `target/results/scenarios-<time>.jsonl`. A new `TestDataTool results` command merges the fork files into one run
   record. The format belongs to this repository and does not depend on Genie's report.
2. Genie's own output, if it writes a machine-readable file. `OreoTest` configures no Cucumber JSON plugin; what Genie
   writes is still to be checked on the company machine.

Prefer 1, unless 2 turns out to give the same data with no code. In both cases, `OreoTest` runs `forkMode FEATURE`
with `forkCount 3` and a discrete working directory per fork: three JVMs write three files, and merging them is the
command's job.

### Which runs count

- **`nightly`**: scheduled, with a fixed, written scope. Only nightly runs enter the trends and the release view.
- **`adhoc`**: a person's narrow run (`--name ^\[TC-...\].*$`). Kept and listed; never averaged in.
- **`recording`**: the first run of a new case fails by design when it has no recording. Kept and listed; never
  averaged in.
- A nightly scope does not exist yet. `OreoTest` fixes `not @wip` and `@ADO#trade_creation`, which is not a full
  regression. The scope is to be decided, and the record states it.
- API cases book real trades: the volume of a nightly run and its environment need approval.

### Where the records live

Inside the company network: an internal git repository (one file per run, which gives history and diffs for free) or a
file share. Never this public copy, never an external service. This repository holds only the format, the code and
synthetic test fixtures.

## Options

| Option | For | Against |
|---|---|---|
| A. ADO Test Analytics (`PublishTestResults` in the pipeline) | Almost no work; trends and flaky marking built in; ADO is already in use | No product x capability view, no coverage; retention is set by the ADO administrators |
| B. ReportPortal, self-hosted | The strongest test analytics: history, flaky detection, automatic failure grouping | Its own infrastructure (database, search engine, message queue) and a security review; `OreoTest` uses `cucumber.api`, an old Cucumber, so its agent may not fit |
| C. Grafana and a database | Grafana may already be available; flexible trend charts | Needs a database; a matrix or an onboarding funnel is awkward to draw |
| D. A static dashboard generated here, like `coverage --html` | No new infrastructure; opens offline inside the network; every view fits OREO | No shared triage write-back; maintained by the team |

## Recommendation

Start with D and keep the way to B or C open. The records are the asset; every option above can read them.

1. **Records.** The record format, the after-hook change and the `results` command. The nightly pipeline archives each
   run's record and its `coverage.json`. Start this phase before any page: history starts the day it is archived.
2. **Static dashboard.** A `dashboard` command reads every record and the latest `coverage.json` and writes one offline
   page, built the same way as `coverage --html`. The nightly pipeline publishes it, as a build artifact or on internal
   static hosting.
3. **Only if needed.** When several people have to triage failures on the page, or analysis beyond these views is
   wanted, import the same records into ReportPortal or Grafana. The format stays; nothing from phases 1 and 2 is lost.

## Pages

| Page | Content |
|---|---|
| Overview | One row of measures, each with its trend; pass rate and executed cases over 90, 180 and 365 days; the current red cases, by failure class |
| Coverage | The `coverage --html` page, plus the trend of covered / supported cells |
| Products | Each product's onboarding stage: registered in `ProductDatFiles` → `Examples` synced (nothing to sync) → API recorded (`check` finds no missing recording) → API green (last nightly) → UI green. The first three stages need no run |
| Stability | A case x last 30 nightly runs grid: flaky cases and cases red for a long time stand out |
| Runs | Every run, its scope and result; a run's detail; a link to its Genie report while that still exists |
| Defects | ADO Bugs raised through `qa-defect-reporting`, per product, opened and closed over time |

## Definitions

Every number on the dashboard follows one written definition:

| Measure | Definition |
|---|---|
| Pass rate (of a nightly run) | Passed / (passed + failed). Skipped, undefined and `@wip` cases are not counted |
| Covered cell | As `coverage` counts it: a supported product x capability cell with at least one case that runs (not `@wip`) |
| Flaky case | Over the last 30 nightly runs: it failed and then passed on a retry within one run, or its status changed at least twice between consecutive runs with the same `commit` and the same `build`. Without a `build`, the case is shown as unstable, not flaky |
| Red age | Days since the case's last passing nightly run |
| E2E defect | An ADO Bug whose tags carry a case ID; `qa-defect-reporting` writes the case ID and the product into `System.Tags` |

## Open questions

To answer on the company side before phase 1:

1. Which files does a Genie run leave under `target/`? Is one of them machine-readable? Does each fork write its own?
2. The real `azure-pipelines-maven.yml`: is anything scheduled today, and can a pipeline write to the record store?
3. Where do the records live, where are the pages published, and who reads them?
4. The nightly scope and environment, and the approved number of API trades per run.
5. Can a run read the environment's deployed version (`build`)?
6. Can a pipeline query ADO Bugs by tag (case ID) in the project?

## What can be built in this repository

When the work is scheduled, the public copy can hold, with synthetic data only:

- the record format and synthetic fixtures;
- the after-hook change, and the `results` command with unit tests on synthetic fork files;
- the `dashboard` command, tested on a synthetic history.

Runs against real data, and the records they produce, stay on the company machine.
