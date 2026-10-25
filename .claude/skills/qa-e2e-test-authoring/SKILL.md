---
name: qa-e2e-test-authoring
description: Use when the scenarios of an oreo-e2e @playwright feature file have to be made executable - flow, page and component snippets, element repository entries, UI case data, rarely Java glue - or repaired after a failed run. Called by test-agent from /codetestcases; also "implement the new UI cases of trade_cancellation.feature", "the locator for ... is wrong".
---

# QA E2E (Playwright) test authoring (oreo-e2e)

Makes `@playwright` scenarios run through the layered snippets. The feature file is the approved scope.

**The rules are `docs/standards/bdd-feature-implementation-standards.md` (read §1-§4 and §7 before starting),
`docs/test-data.md` for case data, `docs/genie_playwright_glue_catalog.md` for built-ins.** This skill is the procedure
and the report. Where they disagree with the code, the code wins: say so in the report.

## Inputs

From `test-agent`: the feature file, the case IDs to implement (only the new or changed scenarios), `where`
(`company` or `offline`), and on a repair round the failure output and `revisionInstructions`.

## Boundary

- Change only oreo-e2e test assets: `snippet/ui/{flows,pages,components}/`, `src/test/resources/elements/`,
  `data/ui/`, `data/setup/`, `glue/ui/` (last resort). Never the feature's business wording, never FE / BE code.
- A `@playwright` scenario never calls `snippet/api/`. Its preconditions use the UI provisioning steps
  (`a Live '<product_type>' trade exists in the blotter` ..., `flows/common_trade_action.snippet`).

## Layers (hard rules)

```text
.feature -> flows/*.snippet -> flows / pages/*_page.snippet / components/*_component.snippet -> built-ins / focused glue
```

- Flows: business orchestration only; no element keys, no `click` / `type` / `fill` / `select` / visible checks.
- Pages own a page's element keys and atomic actions / assertions; components own dialogs, toasts, panels used on
  several pages. Neither calls a flow.
- `@Then` blocks only assert (read-only refresh / search inside an assertion is fine).

## Procedure

1. **Map every step** before writing anything:

   ```shell
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh ui
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh glue
   ```

   Then read the page and component snippets the flow will need (`ls src/main/resources/snippet/ui/pages
   src/main/resources/snippet/ui/components`). Statuses, in order: `REUSE` (existing flow step), `NEW_SNIPPET`
   (flow composed of existing page / component steps, built-ins, existing glue), `NEW_STEP` (Java). For each
   `NEW_SNIPPET`, decompose to interaction rows:

   | Sub-action | Page / component | Element | Action | Exists? |
   |---|---|---|---|---|
   | open the row's action menu | trade portal page | `trade_portal.row_actions_btn` | click | element exists |
   | enter the amount | dynamic action dialog | `dynamic_action.amount_input` | fill | element exists |
   | choose the new direction | dynamic action dialog | (none yet) | select | new element: see step 2 |

   The element prefix is the one already used in the file (`dynamic_action.` in `dynamic_action_dialog.json`), not
   the file name.

2. **See the real UI before writing mechanics** (new element, new page / component step, changed locator). When
   `where: company` and a browser tool is available, open `playwright.base.url` (from `genie.properties`; never copy
   the address into a file), log in with the configured test account, reach the exact state (dialog open, row action
   menu, conditional section) and read the accessibility tree for test IDs, roles and labels. Record which state you
   checked. When the UI cannot be seen (offline, no access), take the test IDs from the FE source or the story; if
   neither has them, the element is an open item, not a guessed selector.
3. **Elements** (`elements/pages/<page>.json` for a surface with its own URL, `elements/components/<component>.json`
   otherwise): `{"name": "<page_or_component>.<element>", "lookupDetails": {"findBy": "testId", "value": "..."}}`;
   priority `testId` -> role (unique only) -> label -> CSS; never XPath; button by text with
   `findBy: "locator"` and `button:has-text('...')`. Add to the existing file of that page / component.
4. **Snippets**: flow sections `PRECONDITIONS / BUSINESS ACTIONS / BUSINESS ASSERTIONS`; page / component
   `ACTIONS / ASSERTIONS`. A shared business step goes into the existing shared flow (`common_trade_action`,
   `lifecycle_event`, `trade_portal`) rather than a copy. Three or more `Then` lines on the same object become one
   aggregated `@Then`.
5. **Waiting**: never `Thread.sleep()` or `waitForTimeout`. Eventual assertions of `CommonE2EAssertion`
   (`assert that '<element>' is eventually visible / hidden / enabled / disabled`, `... eventually has value '...'`),
   Playwright auto-wait, or the response helpers (`ctx.clickAndWaitForResponse(...)`) for server-driven refreshes.
6. **Data**: follow `.claude/skills/qa-api-test-authoring/references/test-data-decisions.md` sections 1-4 (UI data
   has no `dynamicFields`, `expectedBase` or recordings). The hook flattens the case's `request` into ctx, and a flow
   reads it by path: `defaults.request.earlyTermination.amount` in `data/ui/trade_early_termination.json` is read as
   `from stored variable 'earlyTermination.amount'`. Rows only for cases that differ from `defaults`; a field only
   some products' forms have goes into their `byProduct` with an `... if it is set` step. Trade preparation goes
   through the `data/setup/` names the provisioning steps already use. UI and API data never read each other.
7. **Java** only for `NEW_STEP` (runtime-scoped row selection, a value captured into ctx, a response-aware helper):
   the domain's existing `*Steps` class first; POJO, `PlaywrightServiceResolver.from(scenario)` in `@Before`;
   `ctx.service().getLocator("<name>")` or parameterized locators, never a selector or a test ID built in Java; a step
   that only clicks, fills, reads or checks visibility is a built-in, not Java.
8. **Report** (below). Validation is the caller's job.

## Repair round

Fix only what the failure implicates. `Undefined step`, a locator that matches nothing or several elements, a wrong
layer: fix here, after seeing the real state again. A business outcome that differs from the requirement: possible
defect, report it. Login failure, environment down, timeouts across unrelated cases: environment, report it. Flaky
(passes on retry): find the missing wait condition; never add a sleep or a retry.

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
