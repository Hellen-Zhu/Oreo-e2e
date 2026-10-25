<!-- Transcribed from IMG_6482–IMG_6484; overlapping passages merged. Company package prefix anonymized. Archival source text only. -->

# Workflow and Guardrails

Use this reference first. It defines scope, local-style alignment, naming consistency, ownership boundaries, and the authoring decisions that must be made before low-level implementation starts.

## Scope Boundary

- Work inside `{E2E_DIR}` only.
- Do not modify FE or BE application code when implementing API automation.
- Treat the target `@api` `.feature` file as the approved behavior contract unless revision instructions explicitly say otherwise.

## API Automation Boundary

- API scenarios must stay within API automation assets.
- Keep raw HTTP protocol details in API snippets, not in feature files.
- Prefer existing provisioning snippets for preconditions instead of inlining repeated create/approve/setup flows.

## Scenario Tag Placement

Keep execution and classification tags such as `@smoke`, `@regression`, `@positive`, and `@negative` at `Scenario` or `Scenario Outline` level, not at `Feature` level.

## Scan Existing Local Style First

Before creating a new feature asset or naming family, inspect the existing repo patterns across:

- `src/test/resources/features/api/**/*.feature`
- `src/main/resources/snippet/api/*.snippet`
- `src/test/resources/data/api/*.yml`
- `src/main/java/com/example/module/oreo/glue/api/*.java`

Look for the dominant local style in:

- file stem families
- provisioning key naming
- business wording style in snippets
- assertion phrasing
- glue class ownership

When the repo already makes the nearest family obvious, follow that family directly. Only derive or name a domain explicitly when it helps choose between multiple plausible local baselines.

## Mandatory API Verification Before Authoring

Before changing any API snippet, API YAML, or API Java step, verify the current API behavior against the real target environment.

- use feature text and scenario handoff to understand business intent
- use repo scanning to understand local naming, reuse, and ownership patterns
- use runtime contract evidence and, where safe and practical, real API behavior to confirm method, path, request essentials, auth or headers, status codes, and response shape
- do not infer final request or response assertions solely from old snippets, YAML, glue, or handoff notes
- when revising existing API automation, re-verify the current endpoint behavior first; passing or pre-existing automation is not proof that the contract still matches reality

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
- API YAML stem
- API snippet stem
- TC prefix terminology

If one of these changes during revision, update the related identity fields in the same pass.

## Asset Ownership

Keep each concern in its owned layer:

- business behavior remains in the `.feature` file
- business request/response data lives in YAML
- HTTP action and protocol composition live in API snippets
- Java glue exists only for behavior the project cannot express through existing snippets, built-in steps, or snippet composition

Runtime verification does not replace repo-style scanning, and repo-style scanning does not replace runtime verification. Use both before introducing or revising API mechanics.

## Glue Ownership Check

Before adding or extending Java glue:

- inspect existing domain-aligned step classes such as `TradeApiSteps`, `AllocationApiSteps`, or other nearest owning classes
- prefer the class that already owns the closest business capability
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
| HTTP action steps written directly in feature files | Leaks protocol mechanics into business behavior |
| Feature-level execution tags | Broadens scope unintentionally and conflicts with scenario-level selection |
| Glue class uses Spring component wiring or inheritance | Diverges from existing project glue conventions |
| Revising API snippets, YAML, or glue without verifying the current API behavior first | Encourages stale contract assumptions and brittle automation |
