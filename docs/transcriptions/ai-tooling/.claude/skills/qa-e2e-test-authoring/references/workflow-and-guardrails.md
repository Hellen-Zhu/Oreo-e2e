<!-- Transcribed from IMG_6440–IMG_6442; overlapping passages merged. Company package prefix anonymized. Archival source text only. -->

# Workflow and Guardrails

Use this reference first. It defines scope, local-style alignment, naming consistency, ownership boundaries, and the authoring decisions that must be made before low-level implementation starts.

## Scope Boundary

- Work inside `{E2E_DIR}` only.
- Do not modify FE or BE application code when implementing UI automation.
- Treat the target `@playwright` `.feature` file as the approved behavior contract unless revision instructions explicitly say otherwise.

## UI Automation Boundary

- UI scenarios must stay within UI automation assets.
- Do not call `snippet/api/*` directly from UI scenarios.
- When trade-state or business preconditions are needed, prefer the shared provisioning flows in `snippet/ui/flows/common_trade_action.snippet` and other existing UI-common assets.

## Scenario Tag Placement

Keep execution and classification tags such as `@smoke`, `@regression`, `@positive`, and `@negative` at `Scenario` or `Scenario Outline` level, not at `Feature` level.

## Scan Existing Local Style First

Before creating a new feature asset or naming family, inspect the existing repo patterns across:

- `src/test/resources/features/ui/**/*.feature`
- `src/main/resources/snippet/ui/flows/*.snippet`
- `src/main/resources/snippet/ui/pages/*_page.snippet`
- `src/main/resources/snippet/ui/components/*_component.snippet`
- `src/test/resources/elements/pages/*.json`
- `src/test/resources/elements/components/*.json`
- `src/main/java/com/example/module/oreo/glue/ui/*.java`

Look for the dominant local style in:

- file stem families
- page/component names
- locator key prefixes
- flow naming
- glue class ownership
- business wording style in existing snippets

When the repo already makes the nearest family obvious, follow that family directly. Only derive or name a `businessDomain` explicitly when it helps choose between multiple plausible local baselines.

## Mandatory Browser Verification Before UI Authoring

Before changing any UI flow, page snippet, component snippet, locator JSON, or UI Java step, inspect the impacted UI in the live browser with Playwright MCP.

- use repo scanning to understand local naming, layering, and likely reuse targets
- use live browser exploration to confirm the current DOM, route, visible controls, dialog states, and assertion states
- do not infer selectors, button availability, table actions, validation messages, or dynamic state transitions solely from old snippets, JSON, or feature wording
- when the behavior depends on a modal, drawer, row-action menu, async refresh, or conditional rendering, verify that state explicitly in the browser before writing or revising assets

## Naming Priority Rules

When more than one plausible name or target file exists, choose in this order:

1. existing approved target file for the same capability
2. established naming family already present in the nearest local feature/snippet family
3. nearest comparable entity pattern in that family
4. a newly derived business name only when there is no credible local baseline

Repo-local consistency beats a newly invented but semantically plausible label.

## Identity Binding

Treat these as one identity family unless local repo convention clearly requires a justified exception:

- feature file stem
- feature-level `@featureTag`
- UI YAML stem
- flow snippet stem
- TC prefix terminology

If one of these changes during revision, update the related identity fields in the same pass.

## Asset Ownership

Keep each concern in its owned layer:

- business behavior remains in the `.feature` file
- selectors live in locator JSON
- business test data lives in YAML
- flow composition lives in layered snippets
- page and component mechanics stay in page/component snippets
- Java glue exists only for behavior the project cannot express through reuse or snippet composition

Browser verification does not replace repo-style scanning, and repo-style scanning does not replace browser verification. Use both before introducing or revising UI mechanics.

## Glue Ownership Check

Before adding or extending Java glue:

- inspect existing domain-aligned step classes such as `TradePortalSteps`, `AllocationSteps`, or `ComposeProductSteps`
- prefer the class that already owns the closest page/component or business capability
- create a new glue class only when no existing class is a credible home

## When Divergence Is Allowed

Introduce a new naming variant or ownership split only when:

- there is no credible local baseline, or
- the new capability would be misleading if forced under the existing family

If you intentionally diverge from the repo's local style, state the reason in the implementation report instead of diverging silently.

## Hard Stops

| Rule | Why it fails |
|---|---|
| Editing FE/BE code while using this skill | Breaks the skill's responsibility boundary |
| UI scenario directly depends on `snippet/api/*` | Couples UI automation to API implementation details |
| Feature-level execution tags | Broadens scope unintentionally and conflicts with scenario-level selection |
| Glue class uses Spring component wiring or inheritance | Diverges from existing project glue conventions |
| Revising UI snippets or locators without Playwright MCP verification of the impacted UI state | Encourages stale assumptions and brittle selectors |
