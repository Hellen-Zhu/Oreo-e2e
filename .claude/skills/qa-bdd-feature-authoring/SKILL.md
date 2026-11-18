---
name: qa-bdd-feature-authoring
description: Use when approved oreo-e2e test points have to become Gherkin - which feature file, create or append, case IDs, tags, product Outlines and business-readable steps reused from the existing snippets. Phase 2 of /writebddfeatures (bdd-agent); also "write the scenarios for TP-001 to TP-004", "add a negative case to trade_allocation.feature".
---

# QA BDD feature authoring (oreo-e2e)

Turns approved test points into feature content and returns it for approval. It never writes `.feature` files: the
command does that after the user approves. It never implements either: the case data, snippets, elements and glue a
scenario needs come later from `/codetestcases`. Its job is business language, the right annotations (case IDs, tags,
`@capability#`, trace comments) and the list of steps the implementation must provide.

**The rules are `docs/standards/bdd-feature-generation-standards.md`. Read it in full before starting**; this skill is
the procedure and the output around it (§6 is the step language, §7 the trace and handoff comments). Where they
disagree, the standard wins.

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
   `ls src/test/resources/features/api/*/ src/test/resources/features/ui/*/`. An existing file means `append` unless
   the new cases take it over its limit (§3.3): then `create` a split by business behavior, and report it. For
   `create`, follow the header and title style of one or two sibling features of the same domain.
4. **Assign case IDs** (standard §5.1, with its grep over features and data). On revision, keep every ID already
   shown; new scenarios take the next number.
5. **Reuse steps from the index** (standard §6): regenerate it every run (`scripts/gen-snippet-index.sh api` / `ui`,
   cheap and never stale) and fill only the parameters of a matching pattern. New wording only when nothing matches;
   list every new step for the implementer.
6. **Write the content** (standard §4-§7). A product Outline ends with a header-only `Examples`; `sync-examples`
   writes the rows:

   ```gherkin
       Examples:
         | product_type |
   ```

7. **Check** (below), then return the output.

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
- Append blocks contain no `Feature:`, feature tags, description or `Background:`.
- Each step either matches an index pattern of its layer or is listed under "New steps".
