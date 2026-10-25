<!-- Transcribed from photos; overlapping passages merged. Any content before the visible H1 (frontmatter, On load block) and after the Output Contract template was not supplied. Archival source text only, not an activated skill. -->

# QA BDD Feature Authoring

## Overview

Use this skill to author maintainable Gherkin feature files from understood behavior. Treat API BDD and UI E2E BDD as authoring modes, not separate requirement models. Keep automation details out of feature files.

## Inputs

Use the context provided by the calling command:

- `{E2E_DIR}`
- `story` payload
- `approvedTestPoints`
- `layeringArtifactPath`, if present
- `revisionInstructions`, if present
- `previousOutput`, if present

## Quick Workflow

Follow this order:

1. Read `approvedTestPoints` or `layeringArtifactPath` and treat it as the scope boundary.
2. Select only approved API and UI/E2E test points for BDD authoring.
3. Decide, for each layer, whether the result is `create`, `append`, or `not generated`.
4. Determine `businessDomain`, `featureTag`, target path, and TC ID prefix before writing content.
   - Apply the naming priority rules before inventing a new stem.
   - For `create` mode, inspect the same `businessDomain` first and align with the local repo naming style unless the approved scope explicitly requires a new convention.
5. Read and apply `references/gherkin-authoring-rules.md`.
6. Read and apply `references/detail-sinking-and-validation-placement.md`.
7. Read and apply `references/canonical-step-patterns.md`.
8. Generate or read the snippet catalog for each generated layer before introducing new wording.
9. Write scenarios using the selected layer patterns, snippet reuse rules, and detail-sinking rules.
10. Read and apply `references/handoff-annotations.md`, then preserve only concise implementation-bridging detail in scenario-scoped `# handoff:` comments.
11. Run the quality checks.
12. Return the approval payload using the exact output contract.

## Scope & Layer Decision

Treat `approvedTestPoints` or `layeringArtifactPath` as the scope boundary. When `layeringArtifactPath` is provided, prefer it and read the Phase 1 JSON artifact before authoring. Do not add, remove, or relayer test points unless the revision instructions explicitly request it.

Only API and UI/E2E test points produce `.feature` content. UI Component and UI Integration test points remain part of the approved test strategy, but as lower-layer tests they must not be converted into Gherkin feature files by this skill.

Use `not generated` when:

- that layer has no approved test points
- that layer has only UI Component or UI Integration test points, which are not authored as BDD `.feature` files by this skill

## File Mode Rules

Use `create` when:

- no existing suitable feature file is known, or
- the approved scope describes a new feature capability.

Use `append` when:

- an existing feature file is known from story context, workflow state, repo inspection, or prior output, and
- the new scenarios belong under that existing `Feature:`.

For append mode, output only new `Scenario` or `Scenario Outline` blocks for that layer. Do not output top-level tags, `Feature:`, description, or `Background:`.

## Naming Priority Rules

When choosing `featureTag`, file stem, and target path, apply this priority order:

1. reuse an existing approved target file when the approved scope clearly belongs under that file
2. reuse the established naming pattern already present in the same `businessDomain`
3. reuse the nearest comparable entity pattern in that domain, even if a newly derived semantic label seems more precise
4. derive a new business name from the approved scope only when no credible local baseline exists

Do not prefer a newly invented semantic label such as `lookup`, `search`, `retrieval`, or similar over an already established domain convention unless the approved scope explicitly requires that distinction.

If the repo baseline and the purely derived business label differ, prefer the repo baseline for file naming and tags, and keep the narrower behavior distinction in scenario titles and steps.

## Same-Domain Naming Consistency Check

Before finalizing any new `create`-mode file:

1. inspect existing `.feature` files in the same `businessDomain`
2. identify the dominant stem pattern for comparable entities or capabilities
3. keep the new file in that family unless there is a clear reason not to

Examples of consistency signals:

- sibling files in the same domain use `{entity}_management`
- sibling files use a shared actor/description style
- sibling files use a stable `Feature:` title pattern such as `{Entity} Management API`

If a same-domain baseline exists, do not silently introduce a parallel naming family such as `{entity}_lookup` or `{entity}_search` just because it is semantically plausible.

## File Stem, `featureTag`, and TC Prefix Binding

Treat these three identity fields as one bound unit:

- file stem
- `featureTag`
- TC ID prefix

Rules:

- `featureTag` must exactly match the file stem
- TC ID prefix must derive only from that `featureTag`
- if the file stem changes during review or revision, update `featureTag` and every TC prefix to match in the same pass
- do not mix identities such as `counterparty_management.feature` with `@counterparty_lookup` or `TC-COUNTERPARTY-LOOKUP-...`

## References To Load

Use these references as the authoritative rule sources:

| Reference | Use it for |
|---|---|
| `references/gherkin-authoring-rules.md` | feature content rules, scenario framing, step granularity, business readability, titles, `Background:`, `Scenario Outline:`, and content-level quality |
| `references/detail-sinking-and-validation-placement.md` | what stays in Gherkin, what moves to YAML or downstream automation data, when `# handoff:` is warranted, and API/UI validation placement |
| `references/canonical-step-patterns.md` | layer-specific scenario selection, API/UI step patterns, role usage, and canonical wording |
| `references/feature-file-organization-and-traceability.md` | target paths, grouping, `businessDomain`, `featureTag`, feature tags, scenario tags, and TC IDs |
| `references/handoff-annotations.md` | handoff scope, placement, lifecycle, and concise note shape |

## Snippet Reuse Rules

Before authoring Gherkin, generate or read the snippet catalog for each relevant API or UI/E2E layer.

**Generate Catalogs**

Build reuse candidates before introducing new wording. Generate and read snippet indexes for similar business intent:

- API mode: if API rows exist and `references/api-snippet-index.md` is missing or stale, run `E2E_DIR={E2E_DIR} bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh api`, then use `references/api-snippet-index.md`.
- UI mode: if UI rows exist and `references/ui-snippet-index.md` is missing or stale, run `E2E_DIR={E2E_DIR} bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh ui`, then use `references/ui-snippet-index.md`.

**Reuse Policy**

Prefer existing snippets over new wording.

For each intended Given/When/Then step:

1. Search the relevant layer catalog first:
   - API scenarios use the API snippet catalog: `references/api-snippet-index.md`.
   - UI/E2E scenarios use the UI/E2E snippet catalog: `references/ui-snippet-index.md`.
   - **Do not** search the project's `.feature` files for snippet reuse; only the generated catalog is authoritative.
2. Reuse an existing snippet when its business meaning matches the intended step.
3. Adapt only parameter values, quoted text, identifiers, and examples.
4. Preserve snippet wording, tense, and placeholder style as much as possible.
5. Do not create a semantically duplicate step with different wording.
6. Create new wording only when no suitable snippet exists.

When creating new wording:

- keep it business-readable
- keep it general enough to become reusable
- avoid implementation details
- note it in `Handoff Annotation` as a new or unmatched step

## Authoring Principles

- Feature files are not a place to discover what should be tested; use `qa-layer-test-design` first when coverage is unclear.
- API and UI E2E features should be split when they prove different risks.
- Do not convert every test case into BDD. Convert only approved API/E2E test points that express business behavior or regression intent.
- Acceptance criteria may provide useful business action details. Capture them as optional `# handoff:` comments or downstream data, not as locator, fixture, snippet, or Java-step implementation notes.

## Quality Checks

Before returning output, verify:

- paths and modes are present for API and UI/E2E entries
- UI Component and UI Integration test points are not converted into `.feature` scenarios
- `not generated` layers have no Gherkin block
- create mode includes complete feature content
- append mode includes only new scenario blocks
- no unapproved test points were added
- AC coverage matrix is consistent with approved test points
- reference-backed checks have been applied for content, tags, detail placement, and handoff usage
- naming priority rules were applied before introducing a new file stem
- `create`-mode outputs were checked against same-domain repo style and aligned unless an explicit reason was documented
- file stem, `featureTag`, and TC prefixes are identical in identity and terminology

## Output Contract

Do not create a Phase 2 intermediate artifact. Return the feature authoring result directly in the chat for user review.

The returned Markdown is the approval payload. If the user approves it, `/writebddfeatures` will immediately write the `.feature` files from it.

Return this structure:

````markdown
# BDD Feature Generation Result

## Feature Files To Write

- `{targetPath}` -> mode: create | append | not generated | N scenarios (@smoke: N, @regression: N)

## Lower-Layer Test Points Not Authored As Feature Files

- TP-UIC-001 [UI Component] Summary. Covers: AC1. Reason: component-level test.
- TP-UII-001 [UI Integration] Summary. Covers: AC2. Reason: integration-level test.

## API Feature Content

```gherkin
{complete API feature content for create mode; new Scenario/Scenario Outline blocks only for append mode; omit when API mode is not generated}
```

## UI Feature Content

```gherkin
{complete UI feature content for create mode; new Scenario/Scenario Outline blocks only for append mode; omit when UI mode is not generated}
```
````

<!-- Transcription ends here: the rest of the file, if any, was not supplied. -->
