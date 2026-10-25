<!-- Transcribed from IMG_6408–6413. Any content before the visible On load block was not supplied. Internal story ID and repository path in the JSON example are anonymized. This is archived source content, not an activated skill. -->

> **On load:** Output exactly: `[skill: qa-layer-test-design loaded ✓]` then continue.

# QA Layered Test Design

Use this skill to turn one user story into a reviewable layered test design that identifies what should be covered at API level, UI component level, UI integration level, and UI/E2E level, and what should not be duplicated across layers.

**Do Not:**

- resolve, validate, or inspect `{E2E_DIR}`
- run `Glob`, `Grep`, `Search`, `find`, or shell discovery against the E2E repository
- list source files, feature files, snippets, YAML data, page objects, components, or generated classes
- choose target `.feature` file paths or file modes
- infer reuse from existing implementation artifacts

---

## Senior QA Stance

- Test at the lowest layer that can prove the risk with enough confidence.
- Decompose combination-heavy requirements before designing cases. Do not flatten role/status/action/surface/business-variant combinations into E2E rows.
- Use E2E sparingly for business-critical journeys, not permutation coverage.
- Sparse E2E does not mean one E2E per feature. Split E2E by business-critical variants when the variant changes the visible workflow, required decisions, persisted outcome, role handoff, or compliance/revenue risk.
- Keep form-field validation, required-field checks, disabled states, and inline error behavior below E2E unless the requirement is explicitly a full journey-level recovery or trust scenario.
- Do not use mocks to prove real authorization, persistence, workflow state, audit, or cross-role handoff. Mocks are acceptable for UI/Component rendering states and for non-target external dependencies in E2E.
- Treat the data conditions, environment controllability, and observability a test needs as design inputs — they gate layer choice and oracle feasibility. Specify them as conditions/state, not as automation-framework fixtures.
- Make coverage tradeoffs explicit. A good test plan says what will not be tested and why.

## Inputs

Use the context provided by calling command:

- `story`
- `previousArtifactPath`, if present
- `revisionInstructions`, if present
- `previousOutput`, if present

Treat the story payload as authoritative. If fields are missing, infer conservatively from acceptance criteria and clearly list assumptions.

## Fresh Run

On a fresh run:

1. Read the complete story payload.
2. Extract story metadata:
   - story ID
   - title
   - module or business domain
   - actor or role
   - acceptance criteria
   - business rules
   - known tags or workflow state
3. Identify test conditions from explicit ACs and implied business rules.
4. Select the lowest reliable test layer for each condition.
5. Group related conditions into stable test points.
6. Assign test point IDs, tags, type, priority, and AC coverage.
7. Save the complete Phase 1 artifact locally.
8. Return a draft summary with artifact paths.

## Revision Run

If `revisionInstructions` are present:

1. Prefer `previousArtifactPath` and read the existing `test-layer-analysis.json` as the source of truth.
2. Use `previousOutput` only as fallback context when no artifact path is available.
3. Apply only the user's requested changes to the JSON structure.
4. Preserve unaffected TP IDs, tags, priorities, layers, AC mappings, assumptions, risks, and rationale.
5. Increment `revisionCount`.
6. Overwrite the same `test-layer-analysis.json` artifact path unless the caller explicitly requests a new artifact.
7. Return only a short revised review summary, not the full JSON and not a diff.

## Local Artifact Rules

Save the complete result under the directory: `.ai-sdlc/workflow-state/{storyId}/test-layer-analysis.json`

```json
{
  "artifactVersion": "1.0",
  "revisionCount": 1,
  "generatedAt": "2026-07-05",
  "story": {
    "id": 12345,
    "title": "Refdata – Marketers 1:1 migration",
    "state": "Open",
    "e2eDir": "./example-e2e"
  },
  "acceptanceCriteria": [
    {
      "id": "AC1",
      "text": "Given clients call GET /api/v1/refdata/marketers/active, when data is returned, then behavior and payload semantics remain equivalent to current implementation."
    }
  ],
  "testPoints": [
    {
      "id": "TP-API-001",
      "layer": "API",
      "mode": "regression|smoke",
      "type": "positive|negative",
      "priority": "high",
      "tags": ["@api", "@smoke", "@positive"],
      "summary": "GET /api/v1/refdata/marketers/active keeps 1:1 success behavior and payload semantics after path migration.",
      "covers": ["AC1", "AC5"],
      "rationale": "Release-blocking read path for active marketers; lowest reliable layer is API because scope is endpoint path/contract parity.",
      "oracles": ["Response status and success semantics match legacy behavior baseline."]
    }
  ],
  "layeringDecisions": [
    {
      "decision": "API layer is sufficient to prove AC1 because it covers the endpoint contract and payload semantics. No UI or E2E coverage is needed for this AC."
    }
  ],
  "risks": [],
  "uncoveredACs": []
}
```

On revision, increment `revisionCount` if an existing JSON artifact is available. If not available, set `revisionCount` to `1`.

## Layer Selection Rules

Use the lowest useful layer that proves the behavior.

- Refer to `references/api_detail_design.md` for API/service layer guidance.
- Refer to `references/ui_component_detail_design.md` for UI component/integration layer guidance.
- Refer to `references/e2e_detail_design.md` for UI/E2E layer guidance

Choose multiple layers only when the story has distinct risks that require distinct proof:

- a business rule that should be proven directly at API level, and
- a component behavior that should be proven locally, and/or
- a page integration behavior that should be proven with mocked APIs/state, and/or
- a user journey or deployed-environment risk that cannot be covered by lower layers.

Avoid duplicate coverage. If a lower layer proves a rule fully, higher layers should verify only the integration or journey risk that lower layers cannot prove.

## Test Point Types

Classify each test point as one of:

- `positive`: happy path, guard, or business-critical success scenario
- `negative`: failure, error, or business-critical guard scenario
- `smoke`: primary happy path, critical guard, or production-safe availability check that should block release if it fails
- `regression`: previously fragile or high-value behavior that is not a primary happy/guard path

## Test Point ID Rules

Use stable IDs:

- API: `TP-API-001`, `TP-API-002`, ...
- UI Component: `TP-UIC-001`, `TP-UIC-002`, ...
- UI Integration: `TP-UII-001`, `TP-UII-002`, ...
- UI/E2E: `TP-E2E-001`, `TP-E2E-002`, ...

Keep numbering stable during revision. Add new IDs at the end of the relevant layer sequence.

## Tag Rules

Use tags that translate into feature file annotations:

- API test points must include `@api`
- UI Component test points must include `@ui-component`
- UI Integration test points must include `@ui-integration`
- UI/E2E test points must include `@playwright`
- smoke test points must include `@smoke`
- regression test points must include `@regression`
- include type tags where useful, for example `@positive`, `@negative`

## Coverage Rules

Every acceptance criterion must be either:

- covered by at least one test point, or
- listed in `Uncovered ACs` with a reason.

Do not invent acceptance criteria. If an implied rule matters, label it as an assumption or implied condition.

## Quality Checks

Before returning output, verify:

- every TP maps to at least one AC or explicit assumption
- every AC appears in the coverage matrix
- no UI/E2E test duplicates API, component, or integration coverage without a user-journey reason
- component and integration candidates are not escalated to UI/E2E unless real browser/deployed workflow confidence is required
- smoke set is intentionally small
- negative and permission cases focus on business-critical guards
- IDs are stable and layer-specific
- tags are present and consistent
- assumptions and gaps are explicit

## Output Contract

Produce structured JSON, save it with the renderer script, then return a short review summary directly in your response.

Return a review summary with the actual decisions the user must approve. Keep it concise, but include enough detail for approval without opening the JSON artifact.

```markdown
# Test Layering Draft Summary

Artifact: `.ai-sdlc/workflow-state/{storyId}/test-layer-analysis.json`

## Proposed Layering

API:
- TP-API-001 [smoke/positive] Summary here. Covers: AC1, AC2.

UI Component:
- TP-UIC-001 [regression/negative] Summary here. Covers: AC3.

UI Integration:
- TP-UII-001 [regression/permission] Summary here. Covers: AC4.

UI/E2E:
- TP-E2E-001 [smoke/positive] Summary here. Covers: AC1.

## Approval Focus

- Layering decision to confirm: [specific cross-layer or downshift decision]
- Smoke scope to confirm: [specific smoke scenarios]
- Duplicate coverage intentionally avoided: [specific API vs UI/E2E split]

## Assumptions / Risks

- [Assumption or risk that affects approval, or "None"]

## Uncovered ACs

- [AC and reason, or "None"]

## Suggested Action

Approve this layering plan, or revise specific TP IDs/layers/smoke scope.
```
