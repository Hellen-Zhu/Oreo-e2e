---
name: qa-api-test-authoring
description: Use when the scenarios of an oreo-e2e @api feature file have to be made executable - case data rows, API snippets, rarely Java glue - or repaired after a failed run. Called by test-agent from /codetestcases; also "implement the new cases of trade_allocation.feature", "the step ... is undefined".
---

# QA API test authoring (oreo-e2e)

Makes `@api` scenarios run: test data, snippets, and Java only when nothing else can. The feature file is the approved
scope.

**The rules are `docs/standards/bdd-feature-implementation-standards.md` (read §1-§5 and §7 before starting) and
`docs/test-data.md` for the data format.** This skill is the procedure and the report. Where they disagree with the code,
the code wins: say so in the report.

## Inputs

From `test-agent`: the feature file, the case IDs to implement (only the new or changed scenarios), `where`
(`company` = the project builds and the environment is reachable; `offline` = this copy, nothing runs), and on a
repair round the failure output and `revisionInstructions`.

## Boundary

- Change only oreo-e2e test assets: `src/test/resources/data/`, `src/main/resources/snippet/api/`,
  `src/main/java/.../glue/api/` (last resort). Never the feature's business wording, never FE / BE code.
- A feature step whose meaning is wrong or unimplementable is reported back, not rewritten.
- Never create or edit `data/api/expected/` files, `Examples` rows or `ProductDatFiles` here: recordings come from a
  run, rows from `sync-examples`, products from the onboarding skill.

## Procedure

1. **Map every step** of the target scenarios to an implementation, before writing anything:

   ```shell
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh api
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh glue
   ```

   | Step | Status | Source |
   |---|---|---|
   | `a live '<product_type>' trade is created via api` | REUSE | `api/common_trade_action.snippet` |
   | `performs the Allocation event via api` | NEW_SNIPPET | `api/trade_allocation.snippet`, from `ApiRequestSteps` + REST built-ins |

   Statuses, in the order they must be tried: `REUSE` (an existing snippet step), `NEW_SNIPPET` (composed of existing
   snippet steps, REST built-ins `docs/genie_rest_glue_catalog.md`, existing glue), `NEW_STEP` (Java, only for one
   capability none of those has). A step you cannot map with confidence stops the work: report it.
2. **Contract.** For a `NEW_SNIPPET` that calls an endpoint no existing snippet calls, take method, path, headers and
   body shape from `solutionDesign` or the story. When `where: company` and the environment is reachable, confirm them
   against `{rest.base.url}/v3/api-docs` (read `rest.base.url` from `genie.properties`; never print or copy the address
   into a file). When neither gives them, stop and list exactly what is missing; never copy a neighbouring snippet and
   hope.
3. **Data**: follow `references/test-data-decisions.md` for every value (which file, which layer, merge traps,
   dates, masking rules). In short:
   - A case that runs on the event file's `defaults` needs no row; a row only for a case that differs, its `id` as the
     feature writes it; product differences in `byProduct`.
   - A new event needs `data/api/<event>.json` named after the feature, with `use` for shared blocks of
     `api/common.json`.
   - A rejected case: a row with the `request` change, plus `"dynamicFields": null` when `defaults` has rules for
     success fields the error response lacks.
   - A precondition payload: reuse or `extends` a `data/setup/*.json` before adding one.
   - Dates as `${date:T+N}`; never `basic.productId`, a `datFile` path or a real ID.
   - A case whose evidence is the database or audit, not the response: tag it `@expected#none` (feature authors' tag;
     report it if missing), no recording.
   - Read `TestDataTool case <ID>` for every case you added a row for (company machine).
4. **Snippets** (`snippet/api/<featureName>.snippet`, or `common_*.snippet` for cross-feature steps): sections
   `Build request / Make api call / Assertions`; HTTP only behind business steps; an assertion never changes state;
   ctx keys camelCase; reuse `ApiRequestSteps` (`build json request with body from current scenario ...`) instead of
   new request builders. A builder that is still needed starts with `start a new api request as user '<user>'`, never
   with `start building a new request` alone (it would miss the required headers).
5. **Java** only for `NEW_STEP`: the domain's existing `*ApiSteps` class first; POJO, `RestServiceResolver.from(scenario)`
   in `@Before`, no `@Component`, no inheritance, no RestAssured / other HTTP clients, no `Thread.sleep()`, no
   credentials or URLs; `ctx.write()` for useful report values.
6. **Report** (below). Validation is the caller's job (`qa-bdd-test-execution`).

## Recorded responses (what the first run does)

The first run of a new API case writes `data/api/expected/<event>/<case ID>.json` and fails once: expected, not a
defect. After it: `TestDataTool suggest <event>` lists the values that look generated and where each rule goes; run
again for the values a pattern misses (a value that changed between runs); each dynamic value gets a `dynamicFields`
rule in the event file (`type`, `type-tree`, `regex:`, `var:`, `ignore` last), record again with
`OREO_EXPECTED=update`, then `TestDataTool compact <event>`. Never mask a business message or value to make a case pass. The command reviews every
recording against the requirement before it is committed.

## Repair round

Fix only what the failure implicates (the case ID, its row, its snippet, its glue). Classify first:

| Signal | Fix here? |
|---|---|
| `Undefined step`, compile error, `check` problem on data you wrote | Yes |
| `no expected file` / first-run mismatch on a new case | No: recording workflow (above) |
| Status or value differs from the requirement | No: possible product defect, report it; never change the expectation |
| Connection, login, 5xx, timeout | No: environment, report it |

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
