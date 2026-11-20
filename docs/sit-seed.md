# SIT seed

The SIT seed books trades in bulk for UAT: a live trade per product and a trade per lifecycle event, with a summary
of the trade IDs. It is not a test suite. It lives in this project because it sends the same requests as the E2E cases:
the same API client (`TradeApiClient`), setup bodies (`data/setup/`), dat files and event requests (the E2E cases'
data). When the API changes, one fix serves both.

## What is where

| What | Where |
|---|---|
| Runner, never run by a plain `mvn test` (surefire excludes it) | `src/test/java/com/example/module/oreo/SeedTest.java` |
| Catalog: the trades and events, by name | `src/test/resources/seed/catalog.json` (`SeedData`) |
| What a run books: one row per trade, one row per event x trade | `src/test/resources/seed/features/*.feature` |
| Steps | `glue/api/SeedSteps.java` |
| Manifest: one line per booking or failure | `target/seed/manifest-<date>.jsonl` (generated, never committed) |
| Summary for UAT | `TestDataTool seed-report` → `target/seed/seed-report.csv` |

The seed features are outside `features/`: `OreoTest`, `check` and `sync-examples` do not see them.

## Catalog

```json
{
  "trades": {
    "fx-trf": { "composition": "FX_TRF" },
    "fx-trf-stepin-full": { "composition": "FX_TRF", "setup": "trade-create-stepin-full" },
    "fx-example": { "productId": "FX_EXAMPLE", "dat": "seed/dat/fx-example.dat" }
  },
  "events": {
    "termination": { "case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001" }
  }
}
```

- **A trade** is either a registered composition, which takes its product ID and dat file from `ProductDatFiles`, or
  a product ID with the classpath path of its dat file. Use the second form for products the E2E cases do not cover:
  registering them in `ProductDatFiles` would add them to every product Outline.
- `setup` is the body (`data/setup/<name>.json`, default `trade-create`). A variant (StepIn, another entity or
  portfolio) is a setup file that `extends` `trade-create`.
- **An event** is sent with the request of an E2E positive case (`case`, the Outline's ID). Its `tradeIds` are replaced
  by a new live trade, and the checker approves the task when the event creates one. The trade must be a composition,
  because the case data is per composition.
- Names are lower case, digits and `-`. `SeedDataTest` checks that every row of the seed features is in the catalog
  and every setup exists.

**A new product** is a trade in the catalog and a row in `seed_new_trades.feature`, plus a dat file for a product ID.
**A new event** is an event in the catalog and its rows in `seed_lifecycle_events.feature`; it needs a positive E2E
case first.

## Run (company machine)

Every run books real trades on the environment of the properties file. `SeedTest` reads `sit.properties`;
`-Dgenie.properties` picks another. Narrow the run when you can:

```shell
mvn -B test -Dtest=SeedTest
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-trade"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-event"
mvn -B test -Dtest=SeedTest "-Dcucumber.options=--name ^\[SEED-EVENT-termination-fx-trf\].*$"
mvn -Ptest-data process-test-classes "-Dexec.args=seed-report"
```

`OREO_SEED_ENV=sit` (an environment variable, which reaches the forked JVMs) labels each line of the manifest with the
environment.

The steps check only that a booking happened: a trade ID and an approved task. A failed booking fails its scenario and
is written to the manifest with its error. To rerun only the failed ones, use `--name` with their scenario names
(`[SEED-TRADE-<trade>]`, `[SEED-EVENT-<event>-<trade>]`). `seed-report` keeps the latest line of each booking
(environment, trade, event), writes one CSV row per booking with its trade ID (and child trade IDs for allocation),
and lists the failures.

## Maintenance

- **Data goes stale.** Trades are dated `T+N` and other people change them in SIT. Rebook on a schedule (each sprint,
  after an SIT refresh), for example from a manual pipeline job with `OREO_SEED_ENV` and a `--tags` filter as
  parameters. Keep each run's CSV with the release it supported.
- **Never commit** manifests, reports, real trade IDs, entities or portfolios. Real values for SIT (portfolio, entity)
  go into setup files on the company copy only.
- **A failing seed is a signal.** A product that no longer books in SIT is worth raising, like a failing case.

## Open

- Excel events not mapped yet:
  - **Amendment**: it goes through the trade update, not `trigger-event`, so it needs its own step.
  - **Withdrawal**: is it Cancellation?
  - **OptionEvent_knockout**: needs market data that triggers the knock-out.
- To confirm: RemainingPartyFull / RemainingPartyPartial are NovationRemaining / PartialNovationRemaining, and the
  Excel's FX_PSCRIPT with payoff script TRFGeneric is the composition FX_PSCRIPT_TRF.
- The new-trade sheet's product IDs beyond the four registered compositions: each needs its dat template, and the
  entity and portfolio of its row, as setup data on the company copy.
- The public copy has only some events' case data (allocation, early termination). The other events resolve on the
  company copy.
