<!-- Transcribed from photos; overlapping passages merged. The Quick Authoring Checklist runs past the last photo: items after the last one shown, and anything after it, were not supplied. The photos join at "Business Readability Rules"; items between the end of Step Granularity Rules and that heading, if any, were not visible. Archival source text only. -->

# Gherkin Authoring Rules

Use this reference as the authoritative content-writing standard for `.feature` files.

These rules govern **feature content** only:

- what belongs in a feature file
- how scenarios should be scoped and structured
- how step wording should read at business level
- when to use `Background` and `Scenario Outline`
- how to keep scenarios coherent, stable, and reviewable

These rules do **not** cover file paths, naming, tags, TC IDs, snippet catalog generation, or handoff annotations. Those remain defined in `SKILL.md`.

## Core Principle

Write business-readable Gherkin. Feature files are executable specifications for business behavior. They are not API documentation, HTTP contract tests, UI automation scripts, or implementation traces. Scenario text must be understandable by product, QA, and engineering reviewers without knowing endpoint paths, payload structure, page object names, or automation code.

## Feature Scope Rules

- A feature file should represent one coherent business capability or workflow area.
- All scenarios in the same feature should share the same business intent, not just the same screen, endpoint, or technical module.
- Do not mix unrelated capabilities in one feature file even if they belong to the same story.
- Do not split a single coherent capability into multiple feature files unless layer separation or scale clearly requires it.

## Feature Description Rules

Feature description is allowed only in `create` mode.

When a feature description is included, write exactly one short statement below `Feature:` in **user-story style** using:

```
As a {role}...
I want to ...
so that ...
```

Use this statement to describe the **module/domain-level business intent** shared by the scenarios in the file, not a single scenario or one narrow functional path.

Rules:

- The statement should express stable business context for the feature file.
- Prefer a broad business persona or role when multiple scenarios cover different actions, validations, or outcomes.
- The `I want to` clause should describe the business capability or workflow area, not one specific scenario step.
- The `so that` clause should describe business value, control, traceability, visibility, or operational outcome.
- Do not use feature description in `append` mode.
- Do not restate a happy path scenario as the feature description.
- Do not include implementation detail, automation detail, API contract detail, or handoff content in the description.

## Scenario Framing Rules

- These rules do not redefine approved test scope; they constrain how one approved case is expressed as one Gherkin scenario.
- Each scenario should prove one primary business rule, outcome, or decision point.
- Keep a scenario focused on one business path: happy path, validation failure, permission boundary, duplicate handling, or state transition.
- Do not combine multiple independent approved cases into one scenario.
- If two outcomes can fail independently, they should usually be separate scenarios.
- Scenario titles should describe the behavior under test directly. Avoid titles framed as `after migration`, `after refactor`, or similar delivery-history wording unless the approved scope explicitly requires migration-validation language.

## Scenario Structure Rules

- `Given` should establish only the business context required for the scenario.
- `When` should capture the triggering action or decision being exercised.
- `Then` should verify the primary business outcome.
- Additional `And` steps should support, not replace, the main `Given` / `When` / `Then` flow.
- Do not place assertions in `Given`.
- Do not place setup details in `Then`.
- Prefer a single business trigger in `When`; if multiple actions are required, ensure they are truly part of one cohesive user or business action.
- Usually keep one primary outcome in `Then`, with only directly supporting `And` assertions.

## Step Granularity Rules

- Each step should express one meaningful business precondition, action, or observable outcome.
- Avoid collapsing an entire workflow into one vague step.
- Avoid decomposing a business action into low-level UI or transport mechanics.
- Prefer one business intent per step rather than one mechanical interaction per step.
- Steps should be specific enough to distinguish business behavior, but abstract enough to survive UI or API implementation changes.

## Business Readability Rules

Keep Gherkin at business-behavior level.

Do:

- describe business actions and outcomes in domain language
- keep wording stable across UI or API implementation changes
- use direct, declarative, business-readable language

Avoid:

- implementation detail, transport detail, locator detail, or schema detail
- vague steps such as `Then it works`
- vague verbs unless clearly qualified by business context

Implementation-supporting detail belongs in `# handoff:` comments, not in Gherkin.

## Background Usage Rules

- Use `Background:` only when multiple scenarios truly share the same stable business preconditions.
- Do not place scenario-specific setup in `Background:`.
- Do not use `Background:` to hide excessive setup complexity.
- If the shared setup is long, technical, or relevant to only a subset of scenarios, keep it inside the individual scenarios instead.

## Scenario Outline Usage Rules

- Use `Scenario Outline:` only when the business flow is the same and only data variations change.
- Do not use `Scenario Outline:` to merge scenarios with different business intent.
- Keep example tables small and meaningful; include only variations that materially affect the business rule.
- Example values should be business-readable, not purely technical placeholders.

## Outcome and Assertion Rules

- `Then` steps should verify observable business outcomes, not internal implementation behavior.
- Prefer outcomes visible to business stakeholders: state, status, permission result, visible confirmation, rejection, or unchanged business record.
- Avoid asserting multiple secondary outcomes unless they directly support the primary business outcome.
- When a scenario proves failure behavior, make both the rejection and the preserved business state explicit when relevant.

## Negative Scenario Rules

- Negative scenarios should express a meaningful business rule or guardrail, not just malformed input permutations.
- Prefer negative scenarios that prove permission boundaries, invalid state transitions, duplicate prevention, mandatory business validations, or blocked actions.
- Make the business reason for rejection clear in the scenario wording.
- When relevant, state what must remain unchanged after the rejected action.

## Scenario Independence Rules

- Each scenario should be readable and meaningful on its own.
- Do not make one scenario depend on the execution outcome of another scenario.
- Shared business context may be repeated or placed in `Background:`, but scenario meaning must remain self-contained.
- Do not use wording such as `the trade created in the previous scenario`.

## Consistency Within a Feature Rules

- Within one feature file, use the same business nouns, actor labels, and state terms consistently.
- Do not alternate between synonymous business terms unless the distinction is intentional.
- Similar scenarios in the same feature should use parallel structure where possible.
- If one scenario refers to `trade portal`, other scenarios in the same feature should not switch to different labels for the same concept without reason.

## Scenario Ordering Rules

- Order scenarios from the clearest business baseline to edge cases.
- Prefer to place the core successful path before negative or exceptional paths, unless business risk clearly suggests otherwise.
- Group related negative scenarios near the happy path they qualify.
- Keep scenario order readable for human reviewers, not optimized for automation execution.

## Quick Authoring Checklist

Before finalizing feature content, verify that:

- the feature represents one coherent business capability
- each scenario proves one primary business rule or outcome
- steps are business-readable and appropriately granular
- `Background:` is used only for truly shared business context
- `Scenario Outline:` is used only for meaningful data variation
- outcomes are observable at business level
- negative scenarios make the rejection reason and preserved state clear

<!-- Transcription ends here: the rest of the checklist, if any, was not supplied. -->
