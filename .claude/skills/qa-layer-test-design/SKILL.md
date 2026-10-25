---
name: qa-layer-test-design
description: Use when one oreo-e2e user story has to become test points - which behaviors to prove, on @api or @playwright, with which evidence and product scope - before any feature file is written. Phase 1 of /writebddfeatures (bdd-agent); also "design the tests for this story", "which layer should this AC be tested on", "revise test point TP-003".
---

# QA layered test design (oreo-e2e)

Turns one user story into reviewable **test points** (TP) and saves them as a JSON artifact that phase 2
(`qa-bdd-feature-authoring`) reads. It is test analysis: no Gherkin, file names, case IDs, steps or code.

**The rules are `docs/standards/test-layering-standards.md`. Read it in full before starting**; this skill is the
procedure, the artifact and the output around it. Where they disagree, the standard wins.

## Inputs

From the caller (`bdd-agent`, phase 1):

- `story`: the story payload (ID, title, ACs; optional business rules, `observableEvidence`, `solutionDesign`,
  `technicalNotes`)
- `artifactPath`: where to save, `.ai-sdlc/<storyId>/test-layer-analysis.json`
- on revision: `revisionInstructions`, and the existing artifact at `artifactPath`

The story is the authority. A missing field is not filled in: it becomes an assumption (stated) or a gap (asked).

## What you may read

- `docs/standards/test-layering-standards.md` and `references/oreo-layer-heuristics.md`
- `src/main/java/com/example/module/oreo/testdata/Capability.java` and `ProductDatFiles.java`, to name the
  capability and the products a product scope means

Nothing else in the repository: existing features, snippets, data or glue do not decide what must be tested
("the other events have it" is not a requirement). Reuse is decided in phase 2.

## Senior QA stance

- Test each behavior on the lowest layer that can produce its evidence. In this repository the layers are `@api` and
  `@playwright` only.
- Split before you design: compound ACs, role × status × action × product combinations are decomposed, not flattened
  into UI rows.
- UI coverage is sparse but not one-per-feature: a separate `@playwright` TP only where a variant changes what the user
  sees or does (screens, dialogs, maker-checker handoff, end state).
- Real behavior needs real evidence: authorization, persistence, workflow state and audit are proven against the real
  backend (API), never by a mocked UI state.
- Data conditions and environment control are inputs to the design: a TP whose evidence cannot be observed in the test
  environment is a gap, not a TP with an invented oracle.
- Say what will not be tested and why.

## Procedure

### Fresh run

1. Read the story; list its ACs with IDs (`AC-1` ... in the story's order when it has none) and its metadata.
2. Apply the analysis method (standard §2) to each AC: split, partitions and boundaries, state model, roles, product
   scope, risk. Use `references/oreo-layer-heuristics.md` for OREO's roles, states, surfaces and products.
3. Choose the layer of each TP by its evidence (standard §3, §4).
4. Behavior that belongs below this repository (a single component's rendering, client-side field validation with
   mocked data, an FE unit rule) is not a TP here: list it under `belowThisRepo` as `FE-001` ... with the reason, so
   that it reaches the FE team instead of being dropped or promoted to a UI test.
5. Fill the TP fields (standard §5); number `TP-001` ... in the order they will be reviewed (positive before negative,
   API before UI within an AC).
6. Check AC coverage and gaps (standard §6), then the checklist (standard §7).
7. Save the artifact (below) to `artifactPath`, creating the folder; `revisionCount: 0`.
8. Return the review summary (Output).

### Revision run

1. Read the artifact at `artifactPath`; it is the source of truth.
2. Apply only the requested changes. Keep every other TP ID, field, decision and gap unchanged; a new TP takes the next
   free number, a removed TP's number is not reused.
3. Increment `revisionCount`, overwrite the artifact, return the review summary of the whole result (not a diff).

## Artifact

`.ai-sdlc/` is git-ignored: the story text may be internal. JSON, no comments:

```json
{
  "artifactVersion": "oreo-1",
  "revisionCount": 0,
  "story": { "id": "<storyId>", "title": "<title>" },
  "acceptanceCriteria": [{ "id": "AC-1", "text": "<as given>" }],
  "testPoints": [
    {
      "id": "TP-001",
      "scenarioName": "Maker terminates a live trade early",
      "layer": "@api",
      "tags": ["@ADO#positive", "@ADO#smoke"],
      "productScope": "all supporting products",
      "capability": "earlyTermination",
      "acs": ["AC-1"],
      "validationTarget": "A live trade can be early-terminated",
      "observableEvidence": "Response accepted; trade read back as Terminated",
      "evidenceStrength": 2,
      "risk": "High - core lifecycle rule, backend-owned",
      "reasoning": "Backend rule; the read-back proves the state change"
    }
  ],
  "layeringDecisions": ["AC-2 is proven at API only: the UI shows no outcome of its own"],
  "belowThisRepo": [{ "id": "FE-001", "acs": ["AC-4"], "summary": "...", "reason": "client-side field rule" }],
  "assumptions": [],
  "gaps": [{ "question": "...", "blocks": ["TP-004"] }],
  "uncoveredACs": [{ "ac": "AC-5", "reason": "..." }]
}
```

- `productScope`: `all supporting products` / `one representative product` / `product-independent` /
  `<PRODUCT> only`. `capability` (a `Capability` tag name) only for `all supporting products`; a capability the
  enum does not have is a gap, not a new name.
- `evidenceStrength`: `1`, `2`, `3` or `"UI"` (standard §4).

## Output

Return only this, in the user's language for prose and English for IDs and scenario names:

```markdown
# Test Layering - <storyId> <title>

Artifact: `.ai-sdlc/<storyId>/test-layer-analysis.json` (revision <n>)

| TP | Scenario Name | Layer | Tags | Product Scope | AC | Evidence | Risk |
|---|---|---|---|---|---|---|---|
| TP-001 | ... | @api | @ADO#positive @ADO#smoke | all supporting products (earlyTermination) | AC-1 | read-back (2) | High |

## Decisions to approve
- Layering: <each cross-layer or downshift decision, one line>
- Smoke: <the smoke TPs and why they are the critical path>
- Product scope: <which TPs run per product, which on one product>

## Below this repository (FE)
- FE-001 <summary> - AC-4 - <reason> | None

## Assumptions / Gaps
- <assumption, or gap with its question and the TPs it blocks> | None

## Uncovered ACs
- <AC and reason> | None
```

Then stop. The command asks for approval.

## Checks before returning

- Standard §7 checklist passes.
- Every TP has exactly one layer, one polarity and one suite tag; no case IDs, `@TC-` or business tags.
- `capability` exists in `Capability.java` for every `all supporting products` TP.
- The artifact is valid JSON and the summary matches it.
