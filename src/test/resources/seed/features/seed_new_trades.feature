Feature: SIT seed - new trades
  Books one live trade per trade of seed/catalog.json for UAT (docs/sit-seed.md). Not a test: each booking, or its
  failure, is a line of target/seed/manifest-<date>.jsonl, which TestDataTool seed-report summarises.

  @api @seed-trade
  Scenario Outline: [SEED-TRADE-<trade>] A live <trade> trade is booked
    Given seed trade '<trade>' is booked live via api

    Examples:
      | trade                         |
      | fx-trf                        |
      | fx-co                         |
      | fx-fsb                        |
      | fx-pscript-trf                |
      | fx-trf-stepin-full            |
      | fx-trf-stepin-partial         |
      | fx-pscript-trf-stepin-full    |
      | fx-pscript-trf-stepin-partial |
