<!-- Transcribed from IMG_6421–IMG_6423. Company branding and package prefix anonymized. Only photographed content is included; no frontmatter was visible. This is archival source text, not an active skill. -->

> **On load:** Output exactly: `[skill: qa-e2e-test-authoring loaded ✓]` then continue.

# Example E2E Playwright UI Test Implementation

How to implement Genie UI test artifacts from a `@playwright` `.feature` file. Covers the full authoring flow: element locators (JSON), snippets, Java glue and YAML test data.

## Inputs

Use the context provided by the calling command:

- `{E2E_DIR}`
- target `@playwright` `.feature` file(s)
- `revisionInstructions`, if present
- `previousOutput`, if present

## Quick Workflow

Follow this order:

1. Read the target `@playwright` feature file and treat it as the scope boundary.
2. Read and apply `references/workflow-and-guardrails.md`.
3. Scan existing feature, snippet, element, and glue families first, then align with the closest local style before creating anything new.
4. Derive `featureTag`, touched pages/components, TC IDs, business actions, assertions, and trade-state preconditions from the feature and the repo baseline.
5. Read and apply `references/reuse-and-snippet-architecture.md`.
6. Build the reuse inventory for the changed or newly introduced behavior.
7. Read and apply `references/ui-implementation-mechanics.md`, then use Playwright MCP browser exploration to inspect the impacted routes, dialogs, and state transitions before authoring or revising any UI snippet, locator JSON, or UI Java glue.
8. Read `references/genie_playwright_glue_catalog.md` only for built-in primitives used inside snippet composition.
9. Update only the required E2E assets.
10. Run the quality checks.
11. Return the implementation report using the exact output contract.

## Scope Boundary & Artifact Set

Treat the feature file as fixed scope unless revision instructions explicitly ask to change it. This skill implements UI automation assets under `{E2E_DIR}` only.

Typical outputs are:

- element locator JSON under `src/test/resources/elements/pages/*.json` or `src/test/resources/elements/components/*.json`
- UI YAML data under `src/test/resources/data/ui/{featureTag}.yml`
- UI snippet flow under `src/main/resources/snippet/ui/flows/{featureTag}.snippet`
- UI page snippets under `src/main/resources/snippet/ui/pages/*_page.snippet` when page-level mechanics are added or reused
- UI component snippets under `src/main/resources/snippet/ui/components/*_component.snippet` when reusable component behavior is added or reused
- Java glue under `src/main/java/com/example/module/oreo/glue/ui/*.java` only when `NEW_STEP` is truly required

## Reuse Inventory

Before authoring, build a table with:

- `step text`
- `status`
- `source`

Use the status values defined in `references/reuse-and-snippet-architecture.md`:

- `REUSE_PROJECT`
- `NEW_SNIPPET`
- `REUSE_BUILTIN`
- `NEW_STEP`

Use this as a decision aid, not busywork. The goal is to ensure every changed or newly introduced behavior has a clear implementation path before asset writing begins.

## References To Load

Use these references as the authoritative rule sources:

| Reference | Use it for |
|---|---|
| `references/workflow-and-guardrails.md` | scope boundary, local style scanning, naming-family reuse, identity binding, ownership rules, glue ownership, and Cucumber 4.x execution syntax |
| `references/reuse-and-snippet-architecture.md` | reuse order, discovery sources, status model, mapping expectations, flow/page/component layering, `NEW_SNIPPET` decomposition, and Java escalation |
| `references/ui-implementation-mechanics.md` | locator schema, file placement, naming, mandatory browser discovery, YAML structure, ctx injection, wait strategy, Java locator access, helper usage, and Java shape constraints. Read this whenever UI mechanics, snippets, or locators are created or revised. |
| `references/genie_playwright_glue_catalog.md` | built-in primitive catalog for composing `NEW_SNIPPET` steps |

## Quality Checks

Before returning output, verify:

- every Gherkin line was mapped before implementation
- only `{E2E_DIR}` assets were changed
- local naming, layering, and ownership patterns were checked before introducing new assets
- Playwright MCP browser exploration was performed for each impacted UI state before snippet, locator, or UI-glue edits
- locator entries use the project JSON schema and sit in the correct page/component file
- scenario to flow to page/component layering remains intact
- flow snippets do not absorb page mechanics or raw locator-level primitives
- YAML, flow snippet, and `featureTag` identities remain aligned
- no direct API-snippet usage was introduced into UI scenarios
- no sleep-based synchronization was introduced
- Java glue was added only where `NEW_STEP` remained necessary after reuse and snippet composition
- snippet wording, page/component placement, and locator choices were validated against live browser snapshots rather than inferred only from repo history
- the returned report explains what was created, updated, or reused

## Output Contract

Return this structure:

```text
Authoring Complete — @playwright
───────────────────────────────
featureTag  : {featureTag}
testTypeTag : @playwright
TC-IDs      : @TC-{MODULE}-UI-NNN, ...

Files updated:
  Element locators   : src/test/resources/elements/pages|components/*.json [created|updated|reused]
  YAML snapshots     : src/test/resources/data/ui/{featureTag}.yml [created|updated|reused]
  Snippets           : src/main/resources/snippet/ui/flows|pages|components/*.snippet [created|updated|reused]
  Java glue          : [path or "none required"]
```
