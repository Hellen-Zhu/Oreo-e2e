Feature: SIT seed - new trades
  Books one live trade per composition and product ID for UAT (docs/sit-seed.md). Not a test: each booking, or its
  failure, is a line of target/seed/manifest-<date>.jsonl, which TestDataTool seed-report summarises.

  @api @seed-trade
  Scenario Outline: [SEED-TRADE-<composition>-<product_id>] A live <composition> trade of product ID <product_id> is booked
    Given a seed trade of composition '<composition>' and product ID '<product_id>' is booked live via api

    Examples:
      | composition    | product_id      |
      | FX_TRF         | FX_TRF          |
      | FX_CO          | FX_PSCRIPT      |
      | FX_FSB         | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF | FX_PSCRIPT_TRF  |
      | FX_PSCRIPT_TRF | FX_PSCRIPT      |

  @api @seed-trade
  Scenario Outline: [SEED-TRADE-<variant>-<composition>-<product_id>] A live <variant> <composition> trade of product ID <product_id> is booked
    Given a '<variant>' seed trade of composition '<composition>' and product ID '<product_id>' is booked live via api

    Examples:
      | variant        | composition    | product_id |
      | stepin-full    | FX_TRF         | FX_TRF     |
      | stepin-partial | FX_TRF         | FX_TRF     |
      | stepin-full    | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | stepin-partial | FX_PSCRIPT_TRF | FX_PSCRIPT |
