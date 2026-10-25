---
name: qa-bdd-feature-authoring
description: Use when approved oreo-e2e test points have to become Gherkin - which feature file, create or append, case IDs, tags, product Outlines and business-readable steps reused from the existing snippets. Phase 2 of /writebddfeatures (bdd-agent); also "write the scenarios for TP-001 to TP-004", "add a negative case to trade_allocation.feature".
---

# QA BDD feature authoring (oreo-e2e)

Turns approved test points into feature content and returns it for approval. It never writes `.feature` files: the
command does that after the user approves.

**The rules are `docs/standards/bdd-feature-generation-standards.md`. Read it in full before starting**; this skill is
the procedure, the step vocabulary and the output around it. Where they disagree, the standard wins. Also read
`references/step-wording-and-detail-placement.md`.

## Inputs

From the caller (`bdd-agent`, phase 2):

- `story`: the story payload (for `solutionDesign` and wording only, never for new scope)
- `artifactPath`: the approved `.ai-sdlc/<storyId>/test-layer-analysis.json`; it is the scope boundary
- on revision: `revisionInstructions` and `previousOutput`

No approved TP, no scenario. Do not add, drop, re-layer or re-scope a TP unless the revision instructions say so; a
problem with a TP (it cannot be expressed, its capability does not exist) is reported in the output.

## Procedure

1. **Read the artifact.** Take the `@api` and `@playwright` TPs; `belowThisRepo` items are listed in the output, never
   written as scenarios.
2. **Group TPs into scenarios** (standard §2): one behavior per scenario; TPs with the same Given, When and polarity
   share a scenario; `all supporting products` is one Outline over `<product_type>` with `@capability#<capability>`.
3. **Name and place each layer's file** (standard §3). Look at the real tree first:
   `ls src/test/resources/features/api/*/ src/test/resources/features/ui/*/`. An established stem wins over a newly
   derived one (`trade_approval_rejection`, `reason_management` ...). An existing file means `append`.
   `featureName` = file stem = event data file name = `@ADO#<featureName>` = case-ID prefix; change one, change all.
   For `create`, read one or two sibling features of the same domain and follow their header and title style.
4. **Assign case IDs.** `TC-{PREFIX}[-<product_type>|-{PRODUCT}]-{API|UI}-{NNN}`, three digits, continuing the highest
   number of the same full prefix found in features **and** case data:

   ```shell
   grep -rhoE "TC-TRADE-EARLY-TERMINATION(-[A-Z_<>a-z]+)?-API-[0-9]{3}" src/test/resources/features src/test/resources/data | sort -u
   ```

   On revision, keep every ID that was already shown; new scenarios take the next number.
5. **Build the step index and reuse first.** Regenerate it every run (it is cheap and never stale):

   ```shell
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh api
   bash .claude/skills/qa-bdd-feature-authoring/scripts/gen-snippet-index.sh ui
   ```

   Read `target/ai/api-step-index.md` / `target/ai/ui-step-index.md`. For each intended step, reuse a pattern whose
   business meaning matches, filling only its parameters; keep its wording and tense. A `@playwright` scenario uses
   only `ui` (flow) steps; an `@api` scenario only `api` steps. The index, not a search of other feature files, is the
   vocabulary: features may contain steps that have no definition.
6. **New wording** only when nothing matches. Keep it business-level and general enough to be reused, follow the
   patterns of `references/step-wording-and-detail-placement.md`, and list every new step in the output for the
   implementer. Never bend the business intent to fit an existing step.
7. **Write the content** (standard §4-§7): header (create only), scenario tags, case ID and title, steps, trace
   comments, optional `# handoff:` notes (reference). A product Outline ends with an `Examples` table that has the
   header only; `TestDataTool sync-examples` writes the rows:

   ```gherkin
       Examples:
         | product_type |
   ```

8. **Check** (below), then return the output.

## Output

Return only this. The command shows it verbatim and asks for approval; on approval it writes exactly the Gherkin
blocks, at the paths and in the modes listed.

````markdown
# BDD Feature Generation - <storyId>

## Feature files to write
- `src/test/resources/features/api/<domain>/<featureName>.feature` -> create | append | not generated - N scenarios (@ADO#smoke N, @ADO#regression N; @ADO#positive N, @ADO#negative N)
- `src/test/resources/features/ui/<domain>/<featureName>.feature` -> ...

## Generation context
- Naming: <featureName, domain, prefix and how they were derived; the sibling style followed>
- Grouping: <TP -> case ID, with the reason for any shared scenario>
- AC coverage: <AC -> case IDs>
- New steps (not in the index): <step - layer - why no existing step fits> | None
- Product Outlines: <case ID - @capability#...> (rows come from sync-examples) | None
- Below this repository (FE): <FE-001 ...> | None
- Gaps / conflicts: <...> | None

## API feature content
```gherkin
<complete file for create; only the new Scenario / Scenario Outline blocks for append; omitted when not generated>
```

## UI feature content
```gherkin
<same>
```
````

## Checks before returning

- Standard §9 checklist passes.
- Every approved `@api` / `@playwright` TP is in exactly one scenario; no scenario without a TP.
- Append blocks contain no `Feature:`, feature tags, description or `Background:`.
- Each step either matches an index pattern of its layer or is listed under "New steps".
- Case IDs are new (step 4), three digits, in `[...]` at the start of the name; no `@TC-` tag anywhere.
- Every `@capability#` name exists in `Capability.java`; product Outlines have the header-only `Examples`.
- No endpoint, HTTP verb, payload, status code, element key, file name or UI mechanic in a step.
