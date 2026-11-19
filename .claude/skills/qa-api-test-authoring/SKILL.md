---
name: qa-api-test-authoring
description: Use when the scenarios of an oreo-e2e @api feature file have to be made executable - case data rows, API snippets, rarely Java glue - or repaired after a failed run. Called by test-agent from /codetestcases; also "implement the new cases of trade_allocation.feature", "the step ... is undefined".
---

# QA API test authoring (oreo-e2e)

Makes `@api` scenarios run: test data, snippets, and Java only when nothing else can. The feature file is the approved
scope. This skill is the procedure and the report; the rules are elsewhere and are read, not repeated:

- `docs/standards/bdd-feature-implementation-standards.md` §1, §3.2-§3.4 (API snippets, shared steps), §4.2-§4.4 (glue)
- `docs/test-data.md`, "Where a value goes", "Case data", "Recorded responses"
- `CLAUDE.md` §3-§4 (where a change goes, hard stops)

Where they disagree with the code, the code wins: say so in the report.

## Inputs

From `test-agent`: the feature file, the case IDs to implement (only these), `where` (`company` or `offline`), and on
a repair round the classified failures and `revisionInstructions`.

## Boundary

Change only `src/test/resources/data/`, `src/main/resources/snippet/api/` and, last, `glue/api/`. Never the feature's
wording, `data/api/expected/`, `Examples` rows, `ProductDatFiles` or FE / BE code. A step whose meaning is wrong or
unimplementable is reported back, not rewritten.

## Procedure

1. **Map every step** of the target scenarios before writing anything:

   ```shell
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh api
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh glue
   ```

   | Step | Status | Source |
   |---|---|---|
   | `a live '<product_type>' trade is created via api` | REUSE | `glue/api/ApiTradeProvisioningSteps` |
   | `performs the 'EarlyTermination' event via api` | REUSE | `api/trade_lifecycle_event.snippet` |
   | `the trade notional is reduced by the terminated amount` | NEW_SNIPPET | `api/trade_partial_termination.snippet` |

   Statuses in the order of `CLAUDE.md` §3: `REUSE`, `NEW_SNIPPET`, `NEW_STEP` (Java). A step you cannot map with
   confidence stops the work: report it.
2. **Contract.** For an endpoint no snippet calls yet, take method, path, headers and body from `solutionDesign` or
   the story; on the company machine confirm them against `{rest.base.url}/v3/api-docs` (never copy the address into
   a file). Neither gives them: stop and list what is missing; never copy a neighbouring snippet and hope.
3. **Data**, by `docs/test-data.md`: a row only for a case that differs from `defaults`; a new event gets
   `data/api/<event>.json` named after the feature. An amount that depends on the trade's notional is a share
   ("Where a value goes"), never a value per product. Then `case <ID>` for every case you added a row for (company machine).
4. **Snippets** in `snippet/api/<featureName>.snippet` (or `common_*.snippet`), by the implementation standard §3.2-§3.4.
5. **Java** only for `NEW_STEP`, in the domain's existing `*ApiSteps` class, by §4.2.
6. **Report** (below). Validation is the caller's (`qa-bdd-test-execution`). A new API case's first run records its
   response and fails once: expected, handled by the command.

## Repair round

Fix only what an `automation` failure implicates (its row, snippet or glue). Other classes are not fixed here
(`qa-bdd-test-execution`, "Reading the result"); never change an expectation to match the system.

## Report

```text
Authoring complete - @api - <feature path>
Cases     : TC-..., TC-...
Mapping   : <n> REUSE, <n> NEW_SNIPPET, <n> NEW_STEP
Data      : data/api/<event>.json [created|updated|unchanged] (rows: TC-..., ...)
            data/setup/<name>.json [created|reused]
Snippets  : snippet/api/<file>.snippet [created|updated|unchanged]
Java glue : <class.method> | none
Contract  : <source of each new endpoint: solutionDesign | api-docs (company machine) | blocked: what is missing>
Open      : <unmapped steps, missing data values, gaps> | none
```
