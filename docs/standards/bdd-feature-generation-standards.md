# BDD Feature Generation Standards

Condensed, non-loss version of feature-generation contracts: source boundaries, naming/path rules, TC-ID formats, scenario grouping, language constraints, annotation contract, and final checks.

## 1. Source Boundaries

| Purpose | Source of truth | Fallback | Forbidden |
|---|---|---|---|
| Test point existence/layer/tags | Confirmed Phase 1 report | None | Re-parsing story/ACs |
| Coverage groups/scenario count | Approved Phase 1 TP fields + this doc | Split for readability/execution correctness | Assuming 1 TP = 1 scenario; inventing grouping intent from raw ACs |
| AC coverage | Phase 1 AC mapping | Original AC text (wording only) | Changing coverage decisions |
| Validation target/evidence | Phase 1 Validation Target + Observable Evidence | None | Dropping evidence because AC ID is covered |
| Feature naming/path | Story title + TP names | Description/persona; approved API operation wording (weak fallback) | Story module, class names, endpoints, story IDs |
| API details | Approved `solutionDesign.apiDesign` | Technical notes only if `solutionDesign` missing; existing feature patterns | UI assumptions; technical notes overriding solution design |
| API response contract | current-case JSON data and recorded expected response | Existing JSON contract patterns, reviewed against approved behavior | Treating a recording as a requirement; duplicating status/body assertions in feature steps |
| UI wording | TP names + persona + `solutionDesign.uiDesign` | Description | Endpoint names |
| Extra API assertions/test data | Phase 1 validation target + approved `solutionDesign.testDesignImplications` | Technical notes as weak fallback | Inventing DB/audit/event assertions without design evidence |

Rules:

- If no approved TP exists for a behavior, do not generate a scenario.
- When approved `solutionDesign` exists, do not treat `technicalNotes` as primary for API paths, UI wording, response contracts, extra assertions, or test data.
- If `technicalNotes` conflicts with `solutionDesign`, use `solutionDesign` and mention the conflict in derivation evidence.
- `Layer` in this document means execution layer only: `@api` or `@playwright`.
- UI composition layers (Page/Component/Flow/Scenario) are defined in `CLAUDE.md` and `docs/standards/bdd-feature-implementation-standards.md`.

## 2. Business Language Standards

Feature files are executable business specifications. Required:

- Domain language in Feature title/description, scenario names, and reusable steps.
- Consistent third-person voice using canonical actors.
- Business contract-level wording reviewable without Java/selectors/request builders/helpers.
- Tags classify execution layer, business module, capability, suite, polarity, and exclusions. TC IDs belong in scenario names, not tags.
- Steps must answer: (1) business action, (2) business outcome, (3) business-rule-compliant resulting state. Forbidden:
- Java/class/method names, snippet names, selectors/DOM IDs, endpoint paths, payload files, request builders, response matchers, page objects, API clients, fixtures/helpers, framework wording.
- Implementation detail wording in feature/scenario steps (including `page`, `component`, `click`, `type`, `fill`, `selector`, `dialog locator`, and similar UI mechanics).
- Tags for story IDs, AC IDs, plain `@TC-...`, case selection, or arbitrary metadata. Existing `@ADO#...` business/module tags are supported selection contracts.
- Changing the approved business intent merely to fit glue. Reuse existing public business steps where they express the same intent; do not invent synonymous steps unnecessarily.

### 2.1 Canonical actor names

Allowed actor names: `maker`, `checker`, `admin`, `user`.

Rules:

- Apply mapping consistently across the feature.
- If two personas map to `maker`, distinguish by action, not by introducing new actor names.

### 2.2 Then-step business outcome test

Every `Then` must answer: **What business goal has been achieved?** If the answer is a UI element instead of business result, rewrite.

| UI-level (forbidden) | Business-semantic rewrite |
|---|---|
| Then the trade blotter section is displayed | Then the trade blotter is accessible |
| Then the trade list loading indicator is displayed | Then the trades are available for selection |
| Then the Save button is enabled | Then can submit the trade |
| Then the error toast message appears | Then the submission is rejected with a reason |
| Then the Allocation tab is visible | Then can view trade allocations |

Never assert transient loading indicators/spinners; assert stable business end state.

## 3. Naming Rules

Derive identity fields:

| Field | Rule |
|---|---|
| `featureName` | snake_case business capability, usually `{primary_business_object}_{primary_business_action}` |
| `featureTag` | bare business stem, normally `featureName`; emitted as `@ADO#{featureTag}`, e.g. `@ADO#trade_creation` |
| `featureModule` | uppercase business stem with `_` → `-`, e.g. `TRADE-CREATION`; do not include `@` or `ADO#` |

`featureModule` must derive only from `featureTag`; never from story module, Java/package names, story IDs, existing TC IDs, or `TP-###`. An existing broad selection tag such as `@ADO#trade_lifecycle` need not equal every event filename. Preserve established selection tags; derive a new event's TC-ID prefix from its own business stem, not from a broad suite/module tag.

### 3.1 `primary_business_object` conventions

- Use stable domain entity noun, singular (`trade`, `allocation`, `portfolio`, `user`), never technical artifacts.
- Keep qualifiers out of object segment (`partial` / `early` belong in action segment).
- Use nearest stable parent entity (all trade lifecycle events use `trade`).
- No plurals.

### 3.2 `primary_business_action` conventions

- Use base verb or established domain noun form (`create`, `cancel`, `approve`; or `cancellation`, `novation`, `allocation`).
- Forbidden generic verbs: `manage`, `handle`, `process`, `validate`, `execute`.
- This is a net-new naming rule; preserve established stems such as `reason_management` and `product_ids_management` during maintenance.
- Event-driven stories use event concept in snake_case.
- Preserve meaningful qualifiers (`partial`, `early`, `outgoing`, `remaining`).
- For net-new naming, `full` may be dropped when default; in append/maintenance mode keep legacy stems unchanged.

### 3.3 Trade event `featureName` derivation

For trade lifecycle or trade-event scenarios, derive `featureName` by rule first, not by fixed lookup.

**Default derivation**

Use:

```text
featureName = {primary_business_object}_{normalized_action}
```

Where:

- `primary_business_object` is usually `trade` for trade lifecycle/event operations.
- `normalized_action` is the canonical business action inferred from story title + approved TP names.
- Ignore product variants in the feature name (`FX TRF`, `NDF`, `Swap`, etc.).
- Preserve meaningful qualifiers in the action segment such as:
  - `partial`
  - `early`
  - `remaining`
  - `outgoing`
  - `full` (only when needed for compatibility or disambiguation)

**Action normalization guidance**

Prefer the canonical business noun/verb form:

- `create` → `creation`
- `amend` / `amendment` → `amendment`
- `checker approve` / `checker approval` → use established `trade_approval_rejection` for the existing approval/rejection capability; otherwise derive from the approved scope
- `audit` → `audit`
- `cancel` / `cancellation` → `cancellation`
- `early terminate` → `early_termination`
- `partial terminate` → `partial_termination`
- `allocate` / `allocation` → `allocation`
- `portfolio reassign` / `portfolio reassignment` → `portfolio_reassignment`
- `step out` → `step_out`
- `novation remaining` → `novation_remaining`
- `partial novation remaining` → `partial_novation_remaining`

Drop generic verbs such as: `manage`, `handle`, `process`, `validate`, `execute`

Normalize:

- `configure` / `configuration` → `config`

**Compatibility exceptions**

Only use an explicit compatibility mapping when the repository already has an established legacy stem that must be preserved. Known examples:

- `StepOutFull` → `trade_step_out_full`
- `StepOutPartial` → `trade_step_out_partial`
- Existing trade approval/rejection coverage → `trade_approval_rejection`

**Decision rule**

1. Extract the stable business object and real business action from story title + TP names.
2. Remove product-specific wording and generic verbs.
3. Normalize the action into canonical snake_case.
4. Build `{object}_{action}`.
5. If an established repository baseline uses a legacy variant, prefer the baseline name.
6. If the business identity or domain is still uncertain, report the gap before creating a new stem/domain; do not silently route it to `general`.

### 3.4 `businessDomain` routing (separate from feature naming)

Current feature directories are channel-specific:

| Domain | API | UI | Existing scope examples |
|---|---|---|---|
| `trading` | Yes | Yes | Trade creation, approval/rejection, lifecycle; audit currently API |
| `reference_data` | Yes | No | Reference mappings, product IDs |
| `product_management` | Yes | No | Cortex formula management |
| `risk_management` | Yes | No | Risk management coverage |
| `compose` | No | Yes | Product composition |
| `notification` | No | Yes | Notifications |
| `blotter` | Yes | Yes | Blotter behavior |
| `user_management` | No | Yes | User preference/configuration |

Known trading events (`Create`, `Amendment`, `CheckerApproval`, `Audit`, `PartialNovationRemaining`, `PartialTermination`, `PortfolioReassignment`, `Cancellation`, `EarlyTermination`, `NovationRemaining`, `StepOutFull`, `StepOutPartial`, `Allocation`) all map to `trading`. Rules:

- This table records existing directories, not a restriction on approved future coverage. Inspect the actual tree before choosing a path.
- Do not route all product work to `compose`; API product IDs and formula management already live in different domains.
- `authentication` and `general` are not current feature domains. Escalate a new domain when no existing glue/snippet/data contract fits; do not create a directory merely from a keyword.
- If multiple signals match, prefer the most business-facing object in title/description/confirmed TPs.
- Technical module naming must not override business domain.
- Never use story module, Java classes, endpoints, story IDs, TC IDs, or `TP-###` for feature naming.

## 4. File Rules

Default paths:

- API: `{E2E_DIR}/src/test/resources/features/api/{businessDomain}/{featureName}.feature`
- UI: `{E2E_DIR}/src/test/resources/features/ui/{businessDomain}/{featureName}.feature`
- API and UI are always separate files. Rules:
- Generate files only for layers with approved TPs.
- Filenames are snake_case; no TC IDs/story IDs and no `_api` / `_ui` / `_e2e` suffixes.
- Same `featureName` may exist under both `api/` and `ui/`.
- Case data routes to `data/{api|ui}/{featureName}.json` through the event in the case ID. A snippet filename is not part of this binding and may be shared across features.
- Ignore legacy source fields (`bddFeatureFile`, `apiFeatureFile`, `uiFeatureFile`).
- If target file exists: read it, determine mode per layer (`create` / `append` / `not generated`), continue TC numbering by matching prefix.
- In `append` mode: output scenario/scenario-outline blocks only; do not add top-level tags/Feature/description/Background.
- In `append` mode: do not introduce a new top-level `Background:`; put setup explicitly in appended scenarios.

## 5. Feature Level

Target structure for a newly created feature:

```gherkin
@{layerTag} @ADO#{featureTag}
Feature: {Business Object} {Capability} — Example FX Trading Platform
  As a {canonical actor}
  I want to {one-sentence business goal}
  So that {business value}
```

Rules:

- Use the `— Example FX Trading Platform` suffix for new feature titles. Existing `API` / `E2E` titles and personas are legacy conventions; do not rewrite them in append mode.
- Use canonical actor names only; never `Admin` (capitalized) or story-specific role names.
- API layer: `@api`; no API `Background:`.
- UI layer: `@playwright`; `Background:` optional and only for stable reusable setup (for example login).
- Background consolidation: if 2+ scenarios share identical `Given` (or identical `Given` + navigation `When`), move shared setup to `Background:`; keep assertions/dynamic data prep out of `Background:`.

## 6. Coverage Grouping

Coverage Group standards:

- One Coverage Group maps to one `Scenario` or `Scenario Outline`.
- Every approved TP appears in exactly one Coverage Group unless human-approved plan says otherwise.
- Grouping inputs: approved layer/tags/validation target/evidence/AC mapping/scenario name/reasoning.
- Preserve each group's validation target and observable evidence.
- One group must stay in one layer, one executable flow, one clear business purpose.
- Do not mix positive and negative behavior in the same scenario or scenario outline.
- `Grouping Basis` must justify compatibility using approved Phase 1 fields. Mandatory same-precondition grouping:
- If 2+ TPs share identical `Given`, same `When`, and same polarity, they must be grouped into one scenario with multiple `Then` assertions unless:
  - assertions require materially different data setup, or
  - grouped scenario would exceed 8 steps, or
  - observable evidence types are incompatible.

## 7. Scenario Level

TC-ID formats:

- API: `TC-{FEATURE_MODULE}[-{PRODUCT}]-API-{NNN}`
- UI: `TC-{FEATURE_MODULE}[-{PRODUCT}]-UI-{NNN}`
- Example: `[TC-TRADE-CREATION-FX_TRF-API-001]`; product-independent example: `[TC-REASON-MANAGEMENT-API-001]`. Rules:
- `{FEATURE_MODULE}` comes from `featureTag`-derived `featureModule`.
- `CaseId.fromScenarioName` reads the bracketed ID from the expanded scenario name. Use a three-digit sequence for new IDs; the parser accepts numeric sequences of other lengths in existing data.
- Product outlines use `[TC-TRADE-CREATION-<product_type>-API-001]`; Examples must resolve registered products. Product-independent cases omit the product segment.
- Numbered outlines are also supported, e.g. `[TC-TRADE-CREATION-FX_TRF-API-<case>]`; data rows use the expanded concrete IDs. Do not use `<case>` as a JSON row ID.
- Before assigning an ID, check existing features and both `data/api` and `data/ui`, including product-template rows. A concrete case must not match multiple rows.
- One Coverage Group normally becomes one scenario.
- One scenario proves one behavior; target 3–8 executable steps when practical.
- Preserve TP order within each layer.
- UI steps must remain business flow level (no page/component/atomic contracts).
- Every scenario should read as: business action → business result → business-rule-compliant state validation.
- TC sequence scope is per full prefix, including product when present (`TC-{FEATURE_MODULE}[-{PRODUCT}]-{API|UI}`). Check expanded outline IDs as well as literal IDs.
- In append mode, start from existing max sequence + 1 for matching prefix; if no matching prefix exists, start at `001`.
- Scenario tags: one of `@ADO#positive` / `@ADO#negative`, then one of `@ADO#smoke` / `@ADO#regression`. Never generate `@TC-...` tags.
- Layer/business tags may be feature-level; suite/polarity tags are scenario-level. For a `<product_type>` outline, use exactly one supported, case-sensitive `@capability#...` tag and the registry-backed product Examples (see implementation standards).
- Scenario title format: `Scenario: [TC-ID] {specific English behavior}`. Scenario naming rules:
- Describe business behavior/outcome, not technical implementation.
- Include context + action + outcome.
- Be specific; avoid vague wording.
- Use user/business perspective.
- No `and` in scenario name (split into multiple scenarios if needed).
- Must be understandable by non-technical business readers.
- Use active business wording.
- Use consistent third-person role language.
- Negative names must identify rejected condition. Scenario structure rules:
- Use `Scenario Outline` for one behavior repeated over examples.
- Do not combine positive and negative behavior in one scenario/outline.

## 8. API Step Pattern Standards

API feature steps describe business API behavior, not HTTP/glue mechanics. Rules:

- Use business language: actor, request/action, accepted/rejected outcome, persisted state, audit/event outcome, downstream business effect.
- Do not expose HTTP methods/paths/headers, payload files, request builders, response matcher names, Java classes, service clients, fixtures/helpers.
- Reuse existing public business steps without weakening the approved intent. Only add language/implementation when the existing steps cannot express it.
- Status code and current-scenario schema/body validation must not appear as Gherkin steps.
- `Given`: business precondition/data state; `When`: business request/action; `Then`: business outcome/evidence approved in Phase 1.
- `Then` must not claim stronger evidence than planned implementation verifies.
- Downstream automation uses Genie request/response helpers, JSON test data, recorded expected responses, and focused assertions. These implementation choices do not belong in feature steps. Contract outcome patterns:
- Existing shared success step: `Then the 'trade' should be 'created' successfully` (object/action vary by business operation).
- Existing shared rejection step: `Then failed to 'create' 'trade'`; there is also a variant with an expected status.
- Preserve an existing domain-specific business outcome when it already expresses the approved evidence. Do not assume every illustrative sentence is an implemented step. (The shared outcome steps encapsulate status plus the current case's expected response; `trade_audit` uses its dedicated evidence rather than a response snapshot.) Business evidence patterns (use only when approved):
- inclusion evidence: `Then the created {business object} should be included in the response`.
- state evidence: `Then the {business object} should have status "{status}"`.
- reason evidence: `Then the rejection reason should identify {business reason}`.
- persistence/audit/event/side-effect evidence: `Then the {business object} should be recorded for {business purpose}`. Do not use data-completeness claims (for example “all configured reasons returned”) unless explicitly required by Phase 1 and implemented as business-field checks. API Business Language reference (canonical wording):
- Given preconditions: valid/invalid submission data by actor; pending-approval/live/draft/dead trade provisioning; live trade with pending `<EventName>`.
- When actions: Trade Creation/Amendment/event via API; approval/rejection/bulk approval; maker retrieves trade; actor-specific valid data setup.
- Then outcomes: created/persisted with status; validation rejection; permission denial; amended terms updated; conflict rejection; event executed/rejected; final trade status; self-approval denied.

## 9. UI Step Pattern Standards

UI feature steps contain business intent only. Examples:

- Existing role/login pattern: `Given maker is logged in to the trade portal`, then `When creates a new '<product_type>' trade`.
- Business outcome pattern: `Then the trade status should be 'Live' in the blotter` (map to an existing flow step; do not add a synonym solely for this example).
- Bad: `When user clicks the Create button`; `When user fills 'FX TRF' into product type`; `When user completes full trade lifecycle`. Rules:
- One step = one user intent or one business-visible assertion.
- No raw glue/selectors/clicks/fills/page-object names/fixtures/helpers.
- Scenario-layer language must map to flow-level actions/assertions, not page/component contracts.
- Use domain language and consistent third-person role language.
- Multi-actor lifecycle flows are allowed.
- Use `Given` for setup/state, `When` for action, `Then` for assertions. UI Business Language reference:
- Given login/state: maker/checker login; Draft Pending Approval trade exists; Live trade exists; Pending Approval trade with `<EventType>` task exists.
- When actions: create trade; approve/reject from portal/detail; perform cancellation from portal/detail; perform `<EventName>` on trade; perform amendment for `<FieldName>`; mid-scenario checker login for actor switch.
- Then assertions: trade/event status in blotter/detail; bulk status results; event completed; approved/rejected; request rejected with error; action available/not available; lifecycle actions available/not available. Outcome wording conventions:
- `completed successfully` for lifecycle/business event completion.
- `approved successfully` / `rejected successfully` for checker decisions.
- `submitted successfully` only for explicit submission into pending-approval workflow.
- `request is rejected with an error` for negative execution outcomes. Role usage rule:
- Role names appear only in login steps.
- Non-login UI action/assertion steps are role-agnostic; active actor is implied by the most recent login step.
- This rule applies to new UI wording, not API actor/permission steps. Some existing UI provisioning/event-trigger steps still name an actor; reuse their public contract where needed rather than expanding the legacy pattern.

## 10. Feature Annotation Standards

Purpose: keep trace and test-design context next to executable specs; not automation instructions. These are authoring requirements for newly generated scenarios based on approved TPs, not metadata consumed or enforced by the runtime. Existing features do not consistently contain these annotations; do not invent TP/AC IDs when maintaining them without an approved source. Feature-level annotation:

- Required in `create`; not added in `append`.
- Place after Story/Goal/Scope and before `Background:`/scenarios.
- Allowed keys: `businessDomain`, `validationScope`, `source`, `implementationBoundary`.
- `implementationBoundary` must be generic and must not name implementation artifacts. Scenario-level annotation:
- Required before every generated `Scenario` / `Scenario Outline` in both `create` and `append`.
- Place immediately before scenario tags.
- Allowed keys: `tp`, `ac`, `validationTarget`, `observableEvidence`, `testDataIntent`.
- `tp` / `ac` must reference approved IDs; validation/evidence must come from approved Phase 1 fields.
- `testDataIntent` describes business data intent only (no fixtures/builders/payload files/DB rows/helper names). Forbidden in annotations:
- step definition names, snippet names, Java class/method names,
- page objects/API clients/fixtures/helpers/request builders/response matchers,
- selectors/endpoints/payload files/DB tables/environment IDs/implementation commands,
- instructions to reuse/create specific automation artifacts.

## 11. Required Output Checks

Before returning, ensure:

- Every approved TP appears once in Coverage Grouping Plan, Scenario Blueprint, and Breakdown.
- No scenario exists without an approved TP.
- Scenario count is minimized via valid grouping without losing traceability.
- Scenario language follows methodology + business-language standards.
- API/UI scenarios keep implementation mechanics out of feature steps.
- UI scenarios remain scenario-layer business language (no page/component/atomic contracts).
- New UI role names appear only in login steps; existing public setup/trigger contracts are reused without inventing aliases. API actor-specific wording remains allowed.
- Coverage grouping is justified by approved Phase 1 fields and grouping rules.
- Every approved validation target + observable evidence is asserted or explicitly covered.
- Generation Context explains evidence for object/action/module/tag/domain/path.
- Generation Context declares each layer file mode: `create` / `append` / `not generated`.
- API/UI files are separate.
- Case IDs are in scenario names, resolve to the correct JSON event/channel, and do not duplicate existing IDs or match multiple rows; no plain `@TC-...` tags are added.
- Domain routing uses the actual channel-specific tree; missing domain/product contracts are escalated rather than guessed.
- Existing files are append-only.
- AC Coverage Matrix covers every AC referenced by Phase 1.
