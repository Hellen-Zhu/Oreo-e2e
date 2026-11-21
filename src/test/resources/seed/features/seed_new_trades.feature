Feature: SIT seed - new trades
  Books one live trade per seed case (composition, product ID, taxonomy) for UAT (docs/sit-seed.md). Not a test:
  each booking, or its failure, is a line of target/seed/manifest-<date>.jsonl, which TestDataTool seed-report summarises.

  @api @seed-trade
  Scenario Outline: [<case>] A live <composition> trade of product ID <product_id> (<taxonomy>) is booked
    Given seed case '<case>' books a live trade of composition '<composition>' and product ID '<product_id>' with taxonomy '<taxonomy>' via api

    Examples:
      | case           | composition     | product_id      | taxonomy                   |
      | SEED-TRADE-001 | FX_ACCUMULATOR  | FX_ACCUMULATOR  | ComplexExotic              |
      | SEED-TRADE-002 | FX_ARO          | FX_ARDO         | SimpleExotic:Digital       |
      | SEED-TRADE-003 | FX_ARO          | FX_ARF          | ComplexExotic              |
      | SEED-TRADE-004 | FX_ARO          | FX_ARO          | VanillaOption              |
      | SEED-TRADE-005 | FX_ASO          | FX_ASF          | ComplexExotic              |
      | SEED-TRADE-006 | FX_ASO          | FX_ASO          | ComplexExotic              |
      | SEED-TRADE-007 | FX_ASO          | FX_ASO_ADJ      | ComplexExotic              |
      | SEED-TRADE-008 | FXBO            | FX_BO_MULTIDIGI | Metals:Precious:Exotic     |
      | SEED-TRADE-009 | FXBO            | FX_BO_MULTIDIGI | ComplexExotic              |
      | SEED-TRADE-010 | FXBO            | FX_BO_MULTIRA   | ComplexExotic              |
      | SEED-TRADE-011 | FXBO            | FX_BO_WORSTOF   | ComplexExotic              |
      | SEED-TRADE-012 | FX_FF           | FX_FLEXIFWD_AM  | ComplexExotic:Generic      |
      | SEED-TRADE-013 | FX_FVA          | FX_FVA          | Exotic:ForwardVolAgreement |
      | SEED-TRADE-014 | FX_ARO          | FX_IARF         | ComplexExotic              |
      | SEED-TRADE-015 | FX_ARO          | FX_IARO         | VanillaOption              |
      | SEED-TRADE-016 | FX_OOS          | FX_OPT_CANFWD   | ComplexExotic              |
      | SEED-TRADE-017 | FX_OOS          | FX_OPT_ON_STRUC | ComplexExotic              |
      | SEED-TRADE-018 | FX_OOS          | FX_OPT_RES_NCF  | ComplexExotic              |
      | SEED-TRADE-019 | FX_OOS          | FX_OPT_RES_STRU | ComplexExotic              |
      | SEED-TRADE-020 | FX_CO           | FX_PSCRIPT      | ComplexExotic              |
      | SEED-TRADE-021 | FX_PSCRIPT_CCK  | FX_PSCRIPT      | ComplexExotic:Barrier      |
      | SEED-TRADE-022 | FX_FSB          | FX_PSCRIPT      | ComplexExotic:Barrier      |
      | SEED-TRADE-023 | FX_PSCRIPT_BOBO | FX_PSCRIPT      | ComplexExotic              |
      | SEED-TRADE-024 | FX_PSCRIPT_TRF  | FX_PSCRIPT      | ComplexExotic              |
      | SEED-TRADE-025 | FX_PSCRIPT_VBD  | FX_PSCRIPT      | ComplexExotic              |
      | SEED-TRADE-026 | FX_PSCRIPT_EASO | FX_PSCRIPT_EASO | ComplexExotic              |
      | SEED-TRADE-027 | FX_PSCRIPT_CCK  | FX_PSCRIPT_FSKO | ComplexExotic:Barrier      |
      | SEED-TRADE-028 | FX_FSB          | FX_PSCRIPT_FSKO | ComplexExotic:Barrier      |
      | SEED-TRADE-029 | FX_PSCRIPT_TRF  | FX_PSCRIPT_TRF  | ComplexExotic              |
      | SEED-TRADE-030 | FX_RAO          | FX_QUANTO_RA    | ComplexExotic              |
      | SEED-TRADE-031 | FX_RAO          | FX_QUANTO_RA_DI | ComplexExotic              |
      | SEED-TRADE-032 | FX_RAO          | FX_RA           | ComplexExotic              |
      | SEED-TRADE-033 | FX_RAO          | FX_RA_DI        | ComplexExotic              |
      | SEED-TRADE-034 | FXStructure     | FX_STRUCT_ACC   | ComplexExotic              |
      | SEED-TRADE-035 | FX_TRF          | FX_TRF          | Metals:Precious:Exotic     |
      | SEED-TRADE-036 | FX_TRF          | FX_TRF          | ComplexExotic              |
      | SEED-TRADE-037 | FX_TRF          | FX_TRF_DLYSTTL  | ComplexExotic              |
      | SEED-TRADE-038 | FX_RVO          | FX_VAROPTION    | ComplexExotic:Generic      |
