Feature: SIT seed - lifecycle events
  Books each lifecycle event of seed/catalog.json on a new live trade of a composition and product ID, approved by the
  checker when it makes a task, for UAT (docs/sit-seed.md). Each row is a seed case: its trade is booked with the
  case's fields (catalog "cases"), its event with its E2E case's request (the catalog's event "case") and the seed
  case's eventRequest over it. A step-in row books a new live trade with the catalog variant's body (stepin-full,
  stepin-partial) and the case's own dat file, portfolio and entity. Not a test: each booking, or its failure, is a
  line of target/seed/manifest-<run ID>.jsonl.

  @api @seed-event
  Scenario Outline: [<case>] <event> is booked on a new live <composition> trade of product ID <product_id>
    Given seed case '<case>' books event '<event>' on a new live trade of composition '<composition>' and product ID '<product_id>' via api

    Examples:
      | case           | event                   | composition    | product_id |
      | SEED-EVENT-001 | allocation-full         | FX_TRF         | FX_TRF     |
      | SEED-EVENT-002 | amendment               | FX_TRF         | FX_TRF     |
      | SEED-EVENT-003 | withdrawal              | FX_TRF         | FX_TRF     |
      | SEED-EVENT-004 | termination             | FX_TRF         | FX_TRF     |
      | SEED-EVENT-005 | remaining-party-full    | FX_TRF         | FX_TRF     |
      | SEED-EVENT-006 | remaining-party-partial | FX_TRF         | FX_TRF     |
      | SEED-EVENT-007 | step-out-full           | FX_TRF         | FX_TRF     |
      | SEED-EVENT-008 | step-out-partial        | FX_TRF         | FX_TRF     |
      | SEED-EVENT-009 | partial-termination     | FX_TRF         | FX_TRF     |
      | SEED-EVENT-010 | portfolio-reassignment  | FX_TRF         | FX_TRF     |
      | SEED-EVENT-011 | allocation-full         | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-012 | amendment               | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-013 | withdrawal              | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-014 | termination             | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-015 | remaining-party-full    | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-016 | remaining-party-partial | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-017 | step-out-full           | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-018 | step-out-partial        | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-019 | partial-termination     | FX_PSCRIPT_TRF | FX_PSCRIPT |
      | SEED-EVENT-020 | portfolio-reassignment  | FX_PSCRIPT_TRF | FX_PSCRIPT |

  @api @seed-event
  Scenario Outline: [<case>] A live <variant> <composition> trade of product ID <product_id> is booked
    Given seed case '<case>' books a live '<variant>' trade of composition '<composition>' and product ID '<product_id>' via api

    Examples:
      | case            | variant        | composition    | product_id |
      | SEED-STEPIN-001 | stepin-full    | FX_TRF         | FX_TRF     |
      | SEED-STEPIN-002 | stepin-partial | FX_TRF         | FX_TRF     |
