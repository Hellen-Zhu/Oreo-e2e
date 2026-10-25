<!-- Transcribed from IMG_6429–IMG_6433; overlapping passages merged. Company package prefix anonymized. Archival source text only. -->

# Reuse and Snippet Architecture

Use this reference after scope and domain are understood. It defines reuse order, snippet layering, decomposition, and the point where Java becomes justified.

This is the canonical reference for reuse decisions, feature-to-implementation mapping, snippet placement, and escalation inside `qa-e2e-test-authoring`. If an older summary or mapping-only document exists, treat this file as the source of truth.

## Status Model

Use these statuses to reason about changed or newly introduced behavior before writing assets:

- `REUSE_PROJECT` — an existing project snippet or existing Java/UI step already covers the intent
- `NEW_SNIPPET` — the behavior should be implemented by composing reusable sub-actions in a new or updated UI snippet
- `REUSE_BUILTIN` — a built-in primitive from `references/genie_playwright_glue_catalog.md` used only inside `NEW_SNIPPET` composition
- `NEW_STEP` — new Java glue is required because neither project reuse nor snippet composition can express the behavior

If any relevant line cannot be mapped confidently, stop and surface the ambiguity instead of guessing.

## Strict Reuse Order

Apply this order without skipping layers:

1. existing project snippet or existing project step
2. `NEW_SNIPPET` composition
3. inside `NEW_SNIPPET`: existing project snippet/step -> built-in primitive -> new Java step
4. `NEW_STEP` in Java only if the behavior still cannot be expressed

This order prevents the skill from introducing avoidable glue code.

## Discovery Sources

Build the reuse inventory by searching in this order:

1. `src/main/resources/snippet/ui/flows/*.snippet`
2. `src/main/resources/snippet/ui/pages/*_page.snippet`
3. `src/main/resources/snippet/ui/components/*_component.snippet`
4. `src/main/java/com/example/module/oreo/glue/ui/*.java`
5. `references/genie_playwright_glue_catalog.md`
6. Playwright MCP browser exploration of the impacted UI states before finalizing composition or creating new mechanics

Record the result as a working table such as:

| step text | status | source |
|---|---|---|
| user opens the trade portal | REUSE_PROJECT | `snippet/ui/pages/trade_portal_page.snippet` |
| user confirms the dynamic action | NEW_SNIPPET | compose dialog actions in `snippet/ui/flows/{featureTag}.snippet` |

## Mapping Expectations

Before asset writing begins, extract and track:

- `featureTag`
- TC IDs (`@TC-...-UI-...`)
- pages or components touched
- business actions
- assertions
- trade-state or other provisioning preconditions
- browser checkpoints needed to confirm rendered controls, dialogs, validation states, and transitions

Use this as a decision aid, not busywork. The goal is to ensure every changed or newly introduced behavior has a clear implementation path. Every relevant line in the feature should have a traceable implementation path.

Favor reuse from the closest layer that already owns the behavior. Page mechanics should usually be reused from page snippets, not reintroduced in flow snippets or Java. Confirm that ownership decision against the rendered UI structure, not from file names alone.

## Canonical Call Graph

Use this call graph as the default architecture:

```text
Scenario (.feature)
  -> snippet/ui/flows/*.snippet only
       -> snippet/ui/flows/*.snippet (shared business flow, when justified)
       -> snippet/ui/pages/*_page.snippet
       -> snippet/ui/components/*_component.snippet
            -> built-in steps or minimal Java glue
```

Do not let page/component snippets call flow snippets.

## Placement Decision Rule

Before adding or composing a snippet step:

```text
1) Existing project snippet/step already implements it?
   -> Reuse directly
2) Business workflow orchestration used by scenario?
   -> create/update snippet/ui/flows/{featureTag}.snippet
3) Page-scoped atomic action/assertion?
   -> create/update snippet/ui/pages/{page_name}_page.snippet
4) Reusable cross-page component/dialog/widget behavior?
   -> create/update snippet/ui/components/{component_name}_component.snippet
5) Still not expressible?
   -> use built-in primitive or minimal Java as last resort
```

For shared trade setup and common workflow helpers, prefer the existing flow-level shared assets such as `snippet/ui/flows/common_trade_action.snippet` and other local common families already present in the repo.

## Identity Binding

Keep this core binding aligned:

`@featureTag = data/ui/{featureTag}.yml = snippet/ui/flows/{featureTag}.snippet`

Page and component snippets follow their own local stems and should align with the page/component family already used by the repo.

## File Organization by Layer

### Flow snippets

Path: `src/main/resources/snippet/ui/flows/{featureTag}.snippet`

Recommended section order:

1. `# — PRECONDITIONS / NAVIGATION —`
2. `# — BUSINESS ACTIONS —`
3. `# — BUSINESS ASSERTIONS —`
4. `# — CLEANUP —` (when needed)

Flow snippets should:

- orchestrate business behavior
- call page/component snippets or shared flow snippets
- keep business-readable intent

Flow snippets should not:

- expose locator keys
- contain raw primitive UI mechanics such as direct click/type/select steps when a page/component snippet should own them
- absorb dialog- or page-specific mechanics that belong lower in the stack

### Page snippets

Path: `src/main/resources/snippet/ui/pages/{page_name}_page.snippet`

Recommended sections:

- `# — PAGE ACTIONS —`
- `# — PAGE ASSERTIONS —`

Page snippets own page-scoped atomics and page-level assertions.

### Component snippets

Path: `src/main/resources/snippet/ui/components/{component_name}_component.snippet`

Recommended sections:

- `# — COMPONENT ACTIONS —`
- `# — COMPONENT ASSERTIONS —`

Component snippets own reusable cross-page component behavior such as dialogs, popovers, and toast-like widgets.

## `NEW_SNIPPET` Decomposition

For each `NEW_SNIPPET`, decompose the business step to interaction-level rows before touching locators or glue. Do the decomposition against verified browser states, not against assumptions from legacy snippets.

Suggested working table:

| Sub-action | Page/Component | Element intent | Action |
|---|---|---|---|
| open row action menu | trade portal page | row actions button | click |
| confirm action | dynamic action dialog | confirm button | click |

Use this breakdown to decide which existing page/component snippets, locators, built-in primitives, or helper methods are already available. Also note which browser state or dialog snapshot confirmed each row when the UI is dynamic or conditional.

## Escalation to `NEW_STEP`

Only escalate to `NEW_STEP` after the decomposition shows that snippet composition is insufficient. Typical examples include:

- runtime ctx-driven row filtering or scoped selection
- capture or stash of runtime value into ctx
- project-specific helper flows such as response-aware click helpers

When escalation is necessary, continue with `references/ui-implementation-mechanics.md`.

## Composition Rules

- `@Then` blocks are assertion-only.
- Keep business wording in the feature and flow layer; locator mechanics belong in page/component snippets or Java.
- Reuse existing project snippet/step before creating `NEW_SNIPPET` composition.
- In `NEW_SNIPPET`, each sub-action must follow: existing project snippet/step -> built-in step -> new Java step.
- UI scenarios must not call `snippet/api/*` directly.
- Flow snippets may compose page/component snippets; page/component snippets must not reverse-call flow snippets.
- A new or revised flow/page/component snippet must be justified by verified browser behavior, not only by repo history.

## Hard Stops

| Rule | Consequence |
|---|---|
| Action/navigation inside `@Then` | Mixed concern, brittle assertions |
| Reimplementing shared login/search/open-menu flow in every module snippet | Duplication and drift |
| Feature file directly includes locator keys | Business spec polluted by implementation detail |
| Flow snippet directly owns page mechanics that belong in page/component snippets | Breaks layered UI abstraction |
| New Java step that only wraps click/type/assert visible | Should be built-in/snippet composition instead |
| Creating or revising snippet mechanics before checking the live UI state in Playwright MCP | Layering decisions and selectors drift from the rendered application |
