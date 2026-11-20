# SIT seed

The SIT seed books trades in bulk for UAT: a live trade per product and a trade per lifecycle event, with a summary
of the trade IDs. It is not a test suite. It lives in this project because it sends the same requests as the E2E cases:
the same API client (`TradeApiClient`), setup bodies (`data/setup/`), dat files and event requests (the E2E cases'
data). When the API changes, one fix serves both.

## What is where

| What | Where |
|---|---|
| Runner, never run by a plain `mvn test` (surefire excludes it) | `src/test/java/com/example/module/oreo/SeedTest.java` |
| Catalog: composition → Itradeable Type, what a pair needs beyond its IDs, variants, events | `src/test/resources/seed/catalog.json` (`SeedData`) |
| What a run books: one row per seed case (a new trade, or an event on a new trade) or variant | `src/test/resources/seed/features/*.feature` |
| Steps | `glue/api/SeedSteps.java` |
| Manifest: one file per run, one line per booking or failure | `target/seed/manifest-<run ID>.jsonl` (generated, never committed) |
| Summary for UAT | `TestDataTool seed-report [<run ID>\|all]` → `target/seed/seed-report-<run ID or all>.csv` and `.html` |
| The page's template | `src/test/resources/tools/seed-report.html` |
| Each seed case's dat file and whether it is still to export | `TestDataTool seed-dats` → `target/seed/seed-dats.csv` |
| Export dat files from Cortex by trade ID (Windows, pywinauto) | `scripts/cortex_dat_export.py` |

The seed features are outside `features/`: `OreoTest`, `check` and `sync-examples` do not see them.

## Bookings and catalog

A new trade is a **seed case** of the Excel's new-trade sheet: its case ID (`SEED-TRADE-001`, the scenario name), its
**composition** (the E2E's, mapped from the Itradeable Type in the catalog), **product ID** and **taxonomy**, the ISDA
taxonomy of column A after the asset class (`ComplexExotic`, `SimpleExotic:Digital`, `Metals:Precious:Exotic`). The
taxonomy labels the booking in the manifest and the report and can pick a catalog entry; it is never sent to the API.
An event row is a seed case too (`SEED-EVENT-001`): the event on a new live trade of a composition and product ID.
Variants are named by composition and product ID. Each row is one booking:

```gherkin
Given seed case 'SEED-TRADE-024' books a live trade of composition 'FX_PSCRIPT_TRF' and product ID 'FX_PSCRIPT' with taxonomy 'ComplexExotic' via api
Given a 'stepin-full' seed trade of composition 'FX_TRF' and product ID 'FX_TRF' is booked live via api
Given seed case 'SEED-EVENT-004' books event 'termination' on a new live trade of composition 'FX_TRF' and product ID 'FX_TRF' via api
```

One composition can be booked under several product IDs, and one pair under several taxonomies, each its own row.
`SeedDataTest` fails when a case ID is not `SEED-<KIND>-<3 digits>` or is used twice, when two variant rows book the
same composition, product ID and variant (two seed cases may book one pair with their own `cases` entries), when a
row's dat file or setup is missing, when an event's composition is not registered, and when a case that is not an
event row has an `eventRequest`. Continue the numbering for a new row; never renumber, UAT knows the cases by their IDs.

The catalog maps each composition to its Itradeable Type, holds what a pair needs beyond its two IDs and what each
case sends of its own:

```json
{
  "itradeableTypes": {
    "FX_PSCRIPT_TRF": "Cortex.Finance.Tradeable.PayoffScript.Ctrl.TRFGeneric",
    "FX_TRF":         "Cortex.Finance.Tradeable.TRF"
  },
  "trades": [
    { "composition": "FX_EXAMPLE", "productId": "FX_EXAMPLE_1", "dat": "OTHER.dat", "setup": "trade-create-example" },
    { "composition": "FX_EXAMPLE", "productId": "FX_EXAMPLE_1", "taxonomy": "Metals:Precious:Exotic", "dat": "FX_EXAMPLE_1_METALS.dat" }
  ],
  "cases": {
    "SEED-TRADE-001": { "request": { "basic": { "portfolioId": "EXAMPLE_PORTFOLIO_B", "riskPortfolio": "EXAMPLE_PORTFOLIO_B", "scbEntity": "EXAMPLE_ENTITY_B" } } },
    "SEED-TRADE-035": { "dat": "FX_TRF_METALS.dat", "request": { "basic": { "portfolioId": "EXAMPLE_PORTFOLIO_C" } } },
    "SEED-EVENT-004": { "request": { "basic": { "portfolioId": "EXAMPLE_PORTFOLIO_D" } }, "eventRequest": { "<field of the event>": "<value>" } }
  },
  "variants": {
    "stepin-full": { "setup": "trade-create-stepin-full" }
  },
  "events": {
    "termination": { "case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001" },
    "amendment": { "case": "TC-TRADE-AMENDMENT-<product_type>-API-001", "via": "update" }
  }
}
```

- **`itradeableTypes`**: each composition the seed books, with its full Itradeable Type of the Excel (column E,
  `Cortex.FX.FXVanilla.Tradeable.FXAverageRateOption`), one to one: the one place where the two names meet. The
  sheet's composition column is the E2E's except `FX_PSCRIPT_FSB`, booked as `FX_FSB`. A row whose composition is not
  in it fails, and so does an Itradeable Type given to two compositions. The manifest and the report carry the
  Itradeable Type beside the composition, so UAT reads its own name.
- **Dat file**: a composition registered in `ProductDatFiles` takes `data/dat/<C>/<C>.dat`; any other takes
  `seed/dat/<C>/<productId>.dat`. Do not register a seed-only composition in `ProductDatFiles`: it would join every
  product Outline of the E2E cases.
- **`trades`**: a pair whose dat file name (`dat`) or body (`setup`, default `trade-create`) is not the default, or
  a pair with one taxonomy (`taxonomy`): an entry with the row's taxonomy wins over one without, so one pair books
  another dat file or body per taxonomy. A body is a setup file that `extends` `trade-create`.
- **`cases`**: what one seed case sends of its own, by case ID, over `trades`. `request` is merged over the setup body
  (objects deeply, arrays and values replaced, `null` deletes a field): the case's portfolio (`basic.portfolioId`),
  risk portfolio (`basic.riskPortfolio`) and entity (`basic.scbEntity`). The UI fills the last two from the portfolio;
  the API takes them as sent, so a case gives all three. `dat` and `setup` replace
  the pair's. An event case (`SEED-EVENT-...`) books its trade with them too, and its `eventRequest` is merged the
  same way over its event's request (the E2E case's): the fields one event booking needs of its own. Every case
  listed must be a row of the features (`SeedDataTest`), and a value starting with `TODO`
  stops the catalog from loading. The catalog has a case per row of the Excel with placeholders,
  `EXAMPLE_PORTFOLIO_<X>` and `EXAMPLE_ENTITY_<X>`, one letter per distinct portfolio and entity, so cases that share
  one share the placeholder. The company copy replaces them with the real SIT values (`scbEntity` is the payload's
  entity code, not the Excel's entity name); a git-ignored `seed/cases.local.json` (`*.local.json`) can hold them
  meanwhile. The manifest and the report carry the
  portfolio, risk portfolio and entity each booking sent.
- **Times**: every booking sends `basic.effectiveTimestamp` as the booking's time (`2026-11-20T11:42:59Z`) and
  `basic.mifidExeTime` as the same instant (`2026-11-20T11:42:59.000Z`), as the trade ticket does. A case's `request`
  that gives either keeps it; `mifidExeTime` follows a given `effectiveTimestamp`.
- **`variants`**: another body for any pair, by name (StepIn Full, StepIn Partial), the `variant` column.
- **`events`**: sent on a new live trade of the pair with the request of an E2E positive case (`case`, the Outline's
  ID), the seed case's `eventRequest` over it, and the checker approves the task when the event creates one. `via` is how it is sent: `trigger-event` (the
  default, `POST /trades/trigger-event`, its `tradeIds` replaced) or `update` (the amendment,
  `POST /trades/<id>/update`, its task read from `msg`). The pair's composition must be registered, because the case
  data is per composition.

**A new product** is its Itradeable Type in `itradeableTypes` (when the composition is new), a row (next case ID,
composition, product ID, taxonomy) in `seed_new_trades.feature`, plus its dat file in
`seed/dat/<C>/` when the composition is not registered. **A new event** is an event in the catalog and its rows
(next `SEED-EVENT` case ID, event, composition, product ID) in `seed_lifecycle_events.feature`; it needs a positive
E2E case first.

## Run (company machine)

Every run books real trades on the environment of the properties file. `SeedTest` reads `sit.properties`;
`-Dgenie.properties` picks another. Narrow the run when you can:

```shell
mvn -B test -Dtest=SeedTest
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-trade"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-event"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--name ^\[SEED-TRADE-024\].*$"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--name ^\[SEED-EVENT-004\].*$"
mvn -Ptest-data process-test-classes "-Dexec.args=seed-report"
mvn -Ptest-data process-test-classes "-Dexec.args=seed-report 20261120-153000"
mvn -Ptest-data process-test-classes "-Dexec.args=seed-report all"
mvn -Ptest-data process-test-classes -DskipTests "-Dexec.args=seed-report"
```

Pick the cases of a run with `--name` (one case, `^\[SEED-TRADE-001\].*$`, or several, `^\[SEED-TRADE-(001|007)\].*$`)
or `--tags`, not by editing the features. A row commented out (`# | SEED-TRADE-002 | ...`) is left out of the run and
still counts as the row of its `cases` entry. `seed-report` reports what a run booked, however few cases it ran.
`-Ptest-data` runs the test data's unit tests before the tool; `-DskipTests` skips them when only the report is
wanted.

**Each run has its own manifest**, `manifest-<run ID>.jsonl`, the run ID its start time (`20261120-153000`), never
appended to an earlier run's. `SeedTest` starts the run in the JVM that forks the features and writes
`target/seed/current-run` (its process ID and the run ID); each forked JVM finds that its parent started the run and
writes to the same file. A seed step run without `SeedTest` (from the IDE) starts a run of its own.

`OREO_SEED_ENV=sit` (an environment variable, which reaches the forked JVMs) labels each line of the manifest with the
environment.

The steps check only that a booking happened: a trade ID and an approved task. A failed booking fails its scenario and
is written to the manifest with its error. To rerun only the failed ones, use `--name` with their scenario names
(`[SEED-TRADE-001]`, `[SEED-EVENT-004]`, `[SEED-TRADE-<variant>-<composition>-<product_id>]`): the rerun is a run of
its own.

`seed-report` reports the latest run; `seed-report <run ID>` another one; `seed-report all` every run, the latest line
of each booking (a rerun's result over the first run's failure). It keeps the latest line of each booking
(environment, case, composition, product ID, taxonomy, variant, event) and writes it twice: a CSV, one row per booking
(run, trade ID, then case, Itradeable Type, portfolio, risk portfolio, entity, child trade IDs for allocation,
status, error), and an HTML page beside it, `seed-report-<run ID or all>.html`, for UAT: counts, a table to search,
filter by status and sort, each failure's error under its row, a button that copies the case and trade ID of the rows
shown, and the counts by Itradeable Type. The page opens offline. The console prints the counts by kind and by
Itradeable Type and the failures.

## Dat files from Cortex

Each seed case books a dat file exported from Cortex from the case's Sample Trade (the Excel): TDS Browser, Trade ID,
Load (F9), double-click the trade's INSTRUMENT ("Trade <trade ID>_1"), Export > View ("Viewer - {value}"),
double-click its `{Object ITradeable}` ("View"), Export > Save As..., "Cortex object file (*.dat)".
`scripts/cortex_dat_export.py` does those clicks for the trade IDs it is given.

1. `seed-dats` lists every seed case (commented-out rows too), the dat path it books and its state: `placeholder` or
   `missing` (to export), `ok` (real), `e2e` (the default dat of a registered composition, the E2E cases': do not
   overwrite it; give the case its own `"dat"` in `cases` to book another), `shared` (an earlier case, `sameAs`,
   books the same file).

   ```
   mvn -Ptest-data process-test-classes -DskipTests "-Dexec.args=seed-dats"
   ```

2. On the Windows machine where Cortex runs, with the TDS Browser open (`pip install pywinauto`), export by trade ID:

   ```
   python scripts/cortex_dat_export.py --inspect
   python scripts/cortex_dat_export.py <trade ID>
   python scripts/cortex_dat_export.py <trade ID> <trade ID> ...
   python scripts/cortex_dat_export.py --file <trade ID list>
   ```

   A list file has one trade ID per line (or comma-separated, `#` starts a comment); keep it outside the
   project, as the IDs are real. Each trade becomes `<trade ID>.dat` in `--out` (`~/Downloads/seed-dats` by default, outside the project, with
   `export-log.csv`). An exported file is reused unless `--force`. One trade's failure does not stop the others.

   The Cortex controls in `LOCATORS` (top of the script) were read from a screen recording: run `--inspect` once (it writes each window's control tree to `<out>/inspect-<n>.txt`),
   with a trade's View window open, and adjust them to the names it prints. A cell without a name is clicked at an
   offset: `--where` prints the mouse position in the window under it for the `*_offset` locators. A failure names its step and locator.

3. Copy each `<trade ID>.dat` to its case's dat path (`datFile` in `seed-dats.csv`) and run `seed-dats` again: what
   is left is still `placeholder`. A real dat file is company data: it stays on the company copy and is never
   committed (`.dat` files in the repo are placeholders).

## Maintenance

- **Data goes stale.** Trades are dated `T+N` and other people change them in SIT. Rebook on a schedule (each sprint,
  after an SIT refresh), for example from a manual pipeline job with `OREO_SEED_ENV` and a `--tags` filter as
  parameters. Keep each run's CSV with the release it supported.
- **Never commit** manifests, reports, real trade IDs, entities or portfolios. Real values for SIT (portfolio, entity)
  go into `cases` (or setup files) on the company copy only.
- **A failing seed is a signal.** A product that no longer books in SIT is worth raising, like a failing case.

## Open

- The Excel's events and the catalog: Withdrawal is `withdrawal` (Cancellation), Amendment is `amendment` (`via:
  update`), StepInFull / StepInPartial are the variants `stepin-full`, `stepin-partial`. OptionEvent_knockout is left
  out for now: it needs market data that triggers the knock-out.
- To confirm: RemainingPartyFull / RemainingPartyPartial are NovationRemaining / PartialNovationRemaining, and the
  Excel's FX_PSCRIPT with payoff script TRFGeneric is the composition FX_PSCRIPT_TRF under product ID FX_PSCRIPT (the
  lifecycle rows book it so).
- The new-trade sheet: `SEED-TRADE-001` to `038` are its first 38 rows; rows 39 to 41 (FX_VOLOPTION, FX_AMER_OPT
  twice) wait for their composition and taxonomy. Each unregistered composition's dat file in `seed/dat/<C>/` is a
  placeholder here: the real template, and the entity and portfolio of each row in `cases`, are on the company copy.
- `008` and `009` (FXBO + FX_BO_MULTIDIGI) book different dat files: `008` names `FX_BO_MULTIDIGI_Precious.dat`
  (the name it is exported under from Cortex) in its `cases` entry, `009` takes the pair's default
  `FX_BO_MULTIDIGI.dat`; both are placeholders here.
- `037` FX_TRF_DLYSTTL books `FX_TRF.dat` unless an entry names another dat.
- To confirm: three Itradeable Types are cut off at the cell's edge in the sheet and are written as seen:
  `...PayoffScript.Ctrl.CrossCurrency` (FX_PSCRIPT_CCK), `...Ctrl.FloatingStrikeB` (FX_FSB),
  `...Ctrl.VolBarrierDigi` (FX_PSCRIPT_VBD).
- The public copy has only some events' case data (allocation, early termination). The other events, amendment and
  cancellation among them, resolve on the company copy.
