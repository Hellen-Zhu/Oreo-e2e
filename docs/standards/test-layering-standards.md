# Test Layering Standards

Define execution-layer selection, test-point fields, tags, and the Phase 1 report contract for Example E2E coverage.

Repository alignment checked on 2026-10-23 against the current workspace. This document owns test design, [feature generation](bdd-feature-generation-standards.md) owns executable Gherkin/identity/path rules, and [implementation standards](bdd-feature-implementation-standards.md) own JSON data, snippets, glue, and execution.

## 1. Source Boundaries

| Purpose | Source of truth | Fallback | Forbidden |
|---|---|---|---|
| Behavior scope | Confirmed Story Contract ACs and business fields | Approved assumptions | Inventing behavior not present in the Story Contract |
| Observable evidence | Story Contract `observableEvidence` | AC expected outcome when explicit evidence is absent | Ignoring evidence because it is awkward to automate |
| Test-relevant design | Approved `solutionDesign` | `technicalNotes` only as weak trace context | Letting technical notes override approved solution design |
| Layer selection | Validation target from methodology | None | Selecting layer because an endpoint, page, or module name exists |
| Domain risk | Story scope plus FX structured products judgement | Domain gap report | Adding unapproved product rules or pricing formulas |

Terminology note:

- This standard decides **execution layer** only (`@api` vs `@playwright`).
- It does not define UI snippet composition layers (Page Snippet / Component Snippet / Flow Snippet / Scenario).
- Current coverage illustrates available mechanisms; it is not permission to invent behavior outside an approved Story Contract.

## 2. Test Point Field Rules

Every test point must include:

- `TP-###`: sequential, neutral, and stable within the report.
- `Layer` (Execution Layer): exactly one of `@api` or `@playwright`.
- `Scenario Name`: English, descriptive, and specific. This is a test-point name, not final Gherkin.
- `Tags`: exactly one polarity tag and exactly one suite-selection tag; the execution tag is recorded separately in `Layer`.
- `AC Mapping`: one or more AC IDs from the Story Contract.
- `Validation Target`: what must be proven.
- `Observable Evidence`: how the outcome is observed.
- `Reasoning`: concise layer/risk rationale.

Do not include feature names, feature tags, TC IDs, file paths, step wording, snippets, Java classes, endpoints, story modules, or implementation artifacts.

## 3. Tag Rules

Layer tag:

- `@api` — REST API contract tests via genie-rest (no browser).
- `@playwright` — browser end-to-end tests via genie-playwright; `genie.properties` configures Microsoft Edge. The active runner/environment properties determine the actual browser configuration.

Select by validation target and evidence, not by business capability name:

- Backend rules, request/response contracts, permissions, and maker-checker enforcement belong at API level when API/backend evidence proves the requirement.
- Browser interactions, available actions, and displayed business state belong at UI level when browser evidence is required.
- Approval/rejection already has both API and UI trading coverage. Backend self-approval prevention and browser approval/action availability are distinct targets, not automatic duplication.
- Audit currently has API trading coverage using dedicated persistence/audit evidence. Do not infer a new UI audit scenario without an approved UI-visible requirement.
- A UI precondition may provision trades via the dedicated UI API-provisioning infrastructure. This does not turn the scenario into `@api`, and a `@playwright` scenario must not call API snippets.
- Some notification scenarios use an API event trigger and verify browser notifications. Classify by the browser evidence, not solely by how the event was triggered.

Actual feature placement and channel-specific domains are defined in the generation standard; Phase 1 does not output file paths.

Polarity tag:

- `@ADO#positive`
- `@ADO#negative`

Suite-selection tag:

- `@ADO#smoke`
- `@ADO#regression`

Each test point must include exactly one execution tag in `Layer`, and exactly one polarity plus one suite-selection tag in `Tags`.

The `Tags` field restriction applies to the Phase 1 report only. Generated features additionally use business/module tags such as `@ADO#trade_creation` and supported `@capability#...` tags where required. Those are assigned during feature generation, not in this report. Case IDs are later placed in scenario names, never added as plain `@TC-...` tags.

## 4. Domain And Design Gaps

Report gaps when safe test design requires information not available in the confirmed Story Contract or approved solution design.

For FX TRF and derivatives stories, common gap areas include:

- target redemption and early termination behavior
- fixing, settlement, maturity, tenor, holiday calendar, and value date rules
- currency pair, direction, strike, notional, leverage, and product variant constraints
- payoff, precision, rounding, and FX rate source
- maker-checker permissions and self-approval prevention
- trade lifecycle state transitions
- persistence, audit, downstream events, confirmation, and reporting visibility

Do not fill these gaps by inventing product behavior.

## 5. Required Output Checks

Before returning:

- Every AC appears in at least one test point.
- Every observable evidence item is covered by at least one test point or explicitly justified in reasoning.
- Compound ACs are split when behaviors, validation targets, layers, or evidence differ.
- Every test point has exactly one execution tag in `Layer`, plus one polarity and one suite-selection tag in `Tags`.
- API/UI layer assignment follows the methodology's validation-target rules.
- Test layering decisions do not imply snippet composition layering decisions.
- No UI-visible behavior is incorrectly pushed to API.
- No backend-only validation is unnecessarily promoted to UI.
- Duplicate API/UI coverage is justified by distinct value.
- API-based UI setup or notification triggering is not mistaken for API-layer validation.
- Domain and design gaps are reported instead of guessed.
- No Gherkin, TC IDs, feature paths, step wording, snippets, or automation implementation details appear.
- Scenario names must be English, descriptive, and specific.
- `TP-###` is sequential and neutral; never include module/domain/file naming.
- The report's `Tags` field is limited to `@ADO#positive`, `@ADO#negative`, `@ADO#smoke`, `@ADO#regression`; `Layer` is separately limited to `@api` or `@playwright`.
- Each test point must include exactly one polarity tag (`@ADO#positive` or `@ADO#negative`) and exactly one selection tag (`@ADO#smoke` or `@ADO#regression`).
