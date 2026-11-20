Feature: SIT seed - lifecycle events
  Books each lifecycle event of seed/catalog.json on a new live trade of a composition and product ID, approved by the
  checker when it makes a task, for UAT (docs/sit-seed.md). The event's request is its E2E case's (the catalog's
  "case"). Not a test: each booking, or its failure, is a line of target/seed/manifest-<date>.jsonl.

  @api @seed-event
  Scenario Outline: [SEED-EVENT-<event>-<composition>-<product_id>] <event> is booked on a new live <composition> trade of product ID <product_id>
    Given seed event '<event>' is booked on a new live trade of composition '<composition>' and product ID '<product_id>' via api

    Examples:
      | event                   | composition    | product_id |
      | allocation-full         | FX_TRF         | FX_TRF     |
      | amendment               | FX_TRF         | FX_TRF     |
      | withdrawal              | FX_TRF         | FX_TRF     |
      | termination             | FX_TRF         | FX_TRF     |
      | remaining-party-full    | FX_TRF         | FX_TRF     |
      | remaining-party-partial | FX_TRF         | FX_TRF     |
      | step-out-full           | FX_TRF         | FX_TRF     |
      | step-out-partial        | FX_TRF         | FX_TRF     |
      | partial-termination     | FX_TRF         | FX_TRF     |
      | portfolio-reassignment  | FX_TRF         | FX_TRF     |
      | allocation-full         | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | amendment               | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | withdrawal              | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | termination             | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | remaining-party-full    | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | remaining-party-partial | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | step-out-full           | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | step-out-partial        | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | partial-termination     | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | portfolio-reassignment  | FX_PSCRIPT_TRF | FX_PSCRIPT |
