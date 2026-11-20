# SIT seed

The SIT seed books trades in bulk for UAT: a live trade per product and a trade per lifecycle event, with a summary
of the trade IDs. It is not a test suite. It lives in this project because it sends the same requests as the E2E cases:
the same API client (`TradeApiClient`), setup bodies (`data/setup/`), dat files and event requests (the E2E cases'
data). When the API changes, one fix serves both.

## What is where

| What | Where |
|---|---|
| Runner, never run by a plain `mvn test` (surefire excludes it) | `src/test/java/com/example/module/oreo/SeedTest.java` |
| Catalog: Itradeable Type → composition, what a pair needs beyond its IDs, variants, events | `src/test/resources/seed/catalog.json` (`SeedData`) |
| What a run books: one row per seed case (composition, product ID, taxonomy), variant or event | `src/test/resources/seed/features/*.feature` |
| Steps | `glue/api/SeedSteps.java` |
| Manifest: one line per booking or failure | `target/seed/manifest-<date>.jsonl` (generated, never committed) |
| Summary for UAT | `TestDataTool seed-report` → `target/seed/seed-report.csv` |

The seed features are outside `features/`: `OreoTest`, `check` and `sync-examples` do not see them.

## Bookings and catalog

A new trade is a **seed case** of the Excel's new-trade sheet: its case ID (`SEED-TRADE-001`, the scenario name), its
**composition** (the E2E's, mapped from the Itradeable Type in the catalog), **product ID** and **taxonomy**, the ISDA taxonomy of column A after the asset
class (`ComplexExotic`, `SimpleExotic:Digital`, `Metals:Precious:Exotic`). The taxonomy labels the booking in the
manifest and the report and can pick a catalog entry; it is never sent to the API. Variants and events are named by composition and
product ID. Each row is one booking:

```gherkin
Given seed case 'SEED-TRADE-024' books a live trade of composition 'FX_PSCRIPT_TRF' and product ID 'FX_PSCRIPT' with taxonomy 'ComplexExotic' via api
Given a 'stepin-full' seed trade of composition 'FX_TRF' and product ID 'FX_TRF' is booked live via api
Given seed event 'termination' is booked on a new live trade of composition 'FX_TRF' and product ID 'FX_TRF' via api
```

One composition can be booked under several product IDs, and one pair under several taxonomies, each its own row.
`SeedDataTest` fails when a case ID is not `SEED-<KIND>-<3 digits>` or is used twice, when two rows book the same
composition, product ID, taxonomy, variant and event, when a row's dat file or setup is missing, and when an event's
composition is not registered. Continue the numbering for a new row; never renumber, UAT knows the cases by their IDs.

The catalog maps each Itradeable Type to its composition and holds what a pair needs beyond its two IDs:

```json
{
  "itradeableTypes": {
    "Cortex.Finance.Tradeable.PayoffScript.Ctrl.TRFGeneric": "FX_PSCRIPT_TRF",
    "Cortex.Finance.Tradeable.TRF": "FX_TRF"
  },
  "trades": [
    { "composition": "FX_EXAMPLE", "productId": "FX_EXAMPLE_1", "dat": "OTHER.dat", "setup": "trade-create-example" },
    { "composition": "FX_EXAMPLE", "productId": "FX_EXAMPLE_1", "taxonomy": "Metals:Precious:Exotic", "setup": "trade-create-metals" }
  ],
  "variants": {
    "stepin-full": { "setup": "trade-create-stepin-full" }
  },
  "events": {
    "termination": { "case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001" },
    "amendment": { "case": "TC-TRADE-AMENDMENT-<product_type>-API-001", "via": "update" }
  }
}
```

- **`itradeableTypes`**: the Excel's full Itradeable Type (column E, `Cortex.FX.FXVanilla.Tradeable.FXAverageRateOption`)
  of each composition the seed books, one to one: the one place where the two names meet. The sheet's composition
  column is the E2E's except `FX_PSCRIPT_FSB`, booked as `FX_FSB`. A row whose composition is
  not in it fails, and so does a composition under two Itradeable Types. The manifest and the report carry the
  Itradeable Type beside the composition, so UAT reads its own name.
- **Dat file**: a composition registered in `ProductDatFiles` takes `data/dat/<C>/<C>.dat`; any other takes
  `seed/dat/<C>/<productId>.dat`. Do not register a seed-only composition in `ProductDatFiles`: it would join every
  product Outline of the E2E cases.
- **`trades`**: a pair whose dat file name (`dat`) or body (`setup`, default `trade-create`) is not the default, or
  a pair with one taxonomy (`taxonomy`): an entry with the row's taxonomy wins over one without. A body is a setup
  file that `extends` `trade-create` (another entity or portfolio).
- **`variants`**: another body for any pair, by name (StepIn Full, StepIn Partial), the `variant` column.
- **`events`**: sent on a new live trade of the pair with the request of an E2E positive case (`case`, the Outline's
  ID), and the checker approves the task when the event creates one. `via` is how it is sent: `trigger-event` (the
  default, `POST /trades/trigger-event`, its `tradeIds` replaced) or `update` (the amendment,
  `POST /trades/<id>/update`, its task read from `msg`). The pair's composition must be registered, because the case
  data is per composition.

**A new product** is its Itradeable Type in `itradeableTypes` (when the composition is new), a row (next case ID,
composition, product ID, taxonomy) in `seed_new_trades.feature`, plus its dat file in
`seed/dat/<C>/` when the composition is not registered. **A new event** is an event in the catalog and its rows in
`seed_lifecycle_events.feature`; it needs a positive E2E case first.

## Run (company machine)

Every run books real trades on the environment of the properties file. `SeedTest` reads `sit.properties`;
`-Dgenie.properties` picks another. Narrow the run when you can:

```shell
mvn -B test -Dtest=SeedTest
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-trade"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-event"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--name ^\[SEED-TRADE-024\].*$"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--name ^\[SEED-EVENT-termination-FX_TRF-FX_TRF\].*$"
mvn -Ptest-data process-test-classes "-Dexec.args=seed-report"
```

`OREO_SEED_ENV=sit` (an environment variable, which reaches the forked JVMs) labels each line of the manifest with the
environment.

The steps check only that a booking happened: a trade ID and an approved task. A failed booking fails its scenario and
is written to the manifest with its error. To rerun only the failed ones, use `--name` with their scenario names
(`[SEED-TRADE-001]`, `[SEED-TRADE-<variant>-<composition>-<product_id>]`,
`[SEED-EVENT-<event>-<composition>-<product_id>]`). `seed-report` keeps the latest line of each booking (environment,
composition, product ID, taxonomy, variant, event), writes one CSV row per booking with its case, Itradeable Type
(from the catalog when a manifest line has none) and trade ID (and child trade IDs for allocation),
counts the bookings by kind and by Itradeable Type, and lists the failures.

## Maintenance

- **Data goes stale.** Trades are dated `T+N` and other people change them in SIT. Rebook on a schedule (each sprint,
  after an SIT refresh), for example from a manual pipeline job with `OREO_SEED_ENV` and a `--tags` filter as
  parameters. Keep each run's CSV with the release it supported.
- **Never commit** manifests, reports, real trade IDs, entities or portfolios. Real values for SIT (portfolio, entity)
  go into setup files on the company copy only.
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
  placeholder here: the real template, and the entity and portfolio of its row as setup data, are on the company copy.
- Rows that differ only by taxonomy (`008`/`009` FXBO + FX_BO_MULTIDIGI, `035`/`036` FX_TRF + FX_TRF) book the same
  body unless a `trades` entry with the taxonomy gives one its own (dat, entity, portfolio). `037` FX_TRF_DLYSTTL
  books `FX_TRF.dat` unless an entry names another dat.
- To confirm: three Itradeable Types are cut off at the cell's edge in the sheet and are written as seen:
  `...PayoffScript.Ctrl.CrossCurrency` (FX_PSCRIPT_CCK), `...Ctrl.FloatingStrikeB` (FX_FSB),
  `...Ctrl.VolBarrierDigi` (FX_PSCRIPT_VBD).
- The public copy has only some events' case data (allocation, early termination). The other events, amendment and
  cancellation among them, resolve on the company copy.
