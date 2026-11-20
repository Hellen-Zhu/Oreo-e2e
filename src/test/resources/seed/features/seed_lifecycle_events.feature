Feature: SIT seed - lifecycle events
  Books each lifecycle event of seed/catalog.json on a new live trade, approved by the checker when it makes a task,
  for UAT (docs/sit-seed.md). The event's request is its E2E case's (the catalog's "case"). Not a test: each booking,
  or its failure, is a line of target/seed/manifest-<date>.jsonl.

  @api @seed-event
  Scenario Outline: [SEED-EVENT-<event>-<trade>] <event> is booked on a new live <trade> trade
    Given seed event '<event>' is booked on a new live seed trade '<trade>' via api

    Examples:
      | event                   | trade          |
      | allocation-full         | fx-trf         |
      | termination             | fx-trf         |
      | remaining-party-full    | fx-trf         |
      | remaining-party-partial | fx-trf         |
      | step-out-full           | fx-trf         |
      | step-out-partial        | fx-trf         |
      | partial-termination     | fx-trf         |
      | portfolio-reassignment  | fx-trf         |
      | allocation-full         | fx-pscript-trf |
      | termination             | fx-pscript-trf |
      | remaining-party-full    | fx-pscript-trf |
      | remaining-party-partial | fx-pscript-trf |
      | step-out-full           | fx-pscript-trf |
      | step-out-partial        | fx-pscript-trf |
      | partial-termination     | fx-pscript-trf |
      | portfolio-reassignment  | fx-pscript-trf |
