---
name: qa-e2e-test-authoring
description: Use when the scenarios of an oreo-e2e @playwright feature file have to be made executable - flow, page and component snippets, element repository entries, UI case data, rarely Java glue - or repaired after a failed run. Called by test-agent from /codetestcases; also "implement the new UI cases of trade_cancellation.feature", "the locator for ... is wrong".
---

# QA E2E (Playwright) test authoring (oreo-e2e)

Makes `@playwright` scenarios run through the layered snippets. The feature file is the approved scope. This skill is
the procedure and the report; the rules are read, not repeated:

- `docs/standards/bdd-feature-implementation-standards.md` §1, §3.1 (UI layering), §3.3-§3.4, §4 (elements, glue,
  waiting, existing capabilities)
- `docs/test-data.md`, "Where a value goes", "Case data", "Setup data"; built-ins: `docs/genie_playwright_glue_catalog.md`
- `CLAUDE.md` §3-§4 (where a change goes, hard stops)

Where they disagree with the code, the code wins: say so in the report.

## Inputs

From `test-agent`: the feature file, the case IDs to implement (only these), `where` (`company` or `offline`), and on
a repair round the classified failures and `revisionInstructions`.

## Boundary

Change only `snippet/ui/{flows,pages,components}/`, `src/test/resources/elements/`, `data/ui/`, `data/setup/` and,
last, `glue/ui/`. Never the feature's wording or FE / BE code. Preconditions use the UI provisioning steps, never
`snippet/api/`.

## Procedure

1. **Map every step** before writing anything:

   ```shell
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh ui
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh glue
   ```

   Read the page and component snippets the flow will need. Statuses in the order of `CLAUDE.md` §3: `REUSE` (flow
   step), `NEW_SNIPPET` (flow from existing page / component steps, built-ins, glue), `NEW_STEP` (Java). Decompose each
   `NEW_SNIPPET` into interaction rows:

   | Sub-action | Page / component | Element | Action | Exists? |
   |---|---|---|---|---|
   | open the row's action menu | trade portal page | `trade_portal.row_actions_btn` | click | element exists |
   | choose the new direction | dynamic action dialog | (none yet) | select | new element: see step 2 |

2. **See the real UI before writing mechanics** (a new element, page / component step or changed locator). On the
   company machine with a browser tool: open `playwright.base.url` (from `genie.properties`; never copy the address
   into a file), log in with the configured test account, reach the exact state (dialog open, row menu, conditional
   section) and read the accessibility tree for test IDs, roles and labels. Otherwise take the test IDs from the FE
   source or the story; neither has them: the element is an open item, never a guessed selector.
3. **Elements, snippets, waits, Java**, by the implementation standard §3-§4. A shared business step goes into the
   existing shared flow (`common_trade_action`, `lifecycle_event`, `trade_portal`) rather than a copy.
4. **Data**, by `docs/test-data.md`: rows only for cases that differ from `defaults`; trade preparation through the
   `data/setup/` names the provisioning steps already use. An amount from the trade's notional is a share, turned
   into the amount by the flow (implementation standard §3.3, "Event amounts from the trade's notional").
5. **Report** (below). Validation is the caller's.

## Repair round

Fix only what an `automation` failure implicates (undefined step, a locator matching nothing or several elements, a
wrong layer), after seeing the real state again. Other classes are not fixed here (`qa-bdd-test-execution`,
"Reading the result").

## Report

```text
Authoring complete - @playwright - <feature path>
Cases      : TC-..., TC-...
Mapping    : <n> REUSE, <n> NEW_SNIPPET, <n> NEW_STEP
Elements   : elements/pages|components/<file>.json [created|updated|unchanged] (+ <names>)
Snippets   : snippet/ui/flows|pages|components/<file>.snippet [created|updated|unchanged]
Data       : data/ui/<event>.json, data/setup/<name>.json [created|updated|unchanged]
Java glue  : <class.method> | none
UI checked : <state seen in the browser for each new element | source used | not seen: why>
Open       : <unmapped steps, unknown test IDs, gaps> | none
```
