<!-- Transcribed from IMG_6478–IMG_6481; overlapping passages merged. Company package prefix anonymized. Archival source text only. The referenced genie_rest_glue_catalog.md is not included in this batch. -->

# Reuse and Snippet Architecture

Use this reference after scope and local style are understood. It defines reuse order, API snippet composition, phase purity, and the point where Java becomes justified.

## Status Model

Use these statuses to reason about changed or newly introduced behavior before writing assets:

- `REUSE_PROJECT` — an existing project snippet or existing Java/API step already covers the intent
- `NEW_SNIPPET` — the behavior should be implemented by composing reusable sub-actions in a new or updated API snippet
- `REUSE_BUILTIN` — a built-in primitive from `references/genie_rest_glue_catalog.md` used only inside `NEW_SNIPPET` composition
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

1. `src/main/resources/snippet/api/*.snippet`
2. `src/main/java/com/example/module/oreo/glue/api/*.java`
3. `references/genie_rest_glue_catalog.md`
4. runtime contract and, where safe and practical, real API verification of the impacted endpoint behavior before finalizing reuse or extension

Record the result as a working table such as:

| step text | status | source |
|---|---|---|
| admin provisions a live trade | REUSE_PROJECT | `snippet/api/common_api.snippet` or existing trade snippet |
| maker creates a new reason | NEW_SNIPPET | compose business request and action in `snippet/api/{featureTag}.snippet` |

## Mapping Expectations

Before asset writing begins, extract and track:

- `featureTag`
- TC IDs (`@TC-...-API-...`)
- actors
- business preconditions
- business actions
- business outcomes
- stored-variable expectations
- verification checkpoints for method, path, request essentials, expected statuses, and response-envelope or business-field shape

Use this as a decision aid, not busywork. The goal is to ensure every changed or newly introduced behavior has a clear implementation path.

Favor reuse from the nearest existing API snippet or glue family before inventing a new one. Reuse discovery does not replace API verification. A `REUSE_PROJECT` match proves that an implementation pattern exists, not that the current endpoint still behaves the same way.

## Placement Decision Rule

Before adding or composing a snippet step:

```text
1) Existing project snippet/step already implements it?
   -> Reuse directly
2) API business action/assertion used by scenario?
   -> create/update snippet/api/{featureTag}.snippet
3) Cross-domain/common request or assertion behavior?
   -> extend snippet/api/common_api.snippet when the repo already uses it that way
4) Still not expressible?
   -> use built-in primitive or minimal Java as last resort
```

Module snippets may call `common_api.snippet`. They must not depend on unrelated module snippets. Before reusing or extending an existing snippet family, confirm the real API still matches the expected method, path, status behavior, and response shape.

## Identity Binding

Keep this core binding aligned:

`@featureTag = data/api/{featureTag}.yml = snippet/api/{featureTag}.snippet`

## Snippet Organization

Path: `src/main/resources/snippet/api/{featureTag}.snippet`

Recommended section order:

1. `# — Provisioning —`
2. `# — Build request —`
3. `# — Make api call —`
4. `# — Assertions —`

## HTTP Action Placement

In this project, HTTP built-in steps are implemented inside snippets, not directly in feature files.

- allowed in snippet `@When` blocks
- allowed in provisioning `@Given` blocks
- forbidden in `@Then` blocks
- forbidden in feature files

## Phase Purity

- `@When` blocks should contain action steps only.
- `@Then` blocks should contain assertions only.
- Provisioning `@Given` blocks may include `When post/get/...` plus response-status checks as setup implementation details.

## Token and Data Handling

- DB DataTable tokens in snippets must be escaped as `\${token}`.
- Use scenario ctx variables for runtime IDs such as `createdTradeId` and `createdTaskId`.
- Keep business test data in YAML; avoid hardcoded payload literals in snippet bodies.

## Escalation to `NEW_STEP`

Only escalate to `NEW_STEP` after snippet composition is insufficient. Typical examples include:

- business-specific response extraction or transformation not supported by built-ins
- assertion of a computed field or custom comparison not expressible through existing steps
- reusable project-specific helper behavior that built-ins and snippets cannot express cleanly

When escalation is necessary, continue with `references/api-implementation-mechanics.md`.

## Hard Stops

| Rule | Consequence |
|---|---|
| HTTP call inside feature files or `@Then` snippets | Blurs business/protocol boundaries and makes failures hard to diagnose |
| Unescaped `${token}` in snippet DataTable | Token expands at parse time; runtime value never injects |
| Cross-module snippet coupling | Fragile dependencies and refactor breakage |
| Missing stem binding between feature/YAML/snippet | `TestDataLoader` lookup failures |
| New Java step created before checking existing snippets and built-ins | Duplicates existing capability |
| Reusing or revising API automation before verifying the live contract or API behavior | Carries stale assumptions forward into new tests |
