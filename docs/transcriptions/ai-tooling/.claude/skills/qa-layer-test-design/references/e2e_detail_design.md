<!-- Transcribed from IMG_6416–6418. -->

# UI E2E Detail Design

Use this reference when routed rows involve a critical user journey, cross-page workflow, role-based UI, persisted business outcome, or confidence that multiple systems work together.

## E2E Eligibility Gate

Route a row to UI E2E only when all of these are true:

- It represents a real user journey or role workflow, not an isolated control, field, component, or validation rule.
- It crosses a meaningful boundary such as navigation, authentication, backend persistence, authorization, workflow state, async processing, or another system.
- The oracle is a user-visible business outcome, durable backend state, or side effect that lower layers cannot prove with enough confidence.
- It is important enough to run as a small regression signal after deploy.

If any condition is false, keep the row at UI/Component, API/service, contract, integration, or exploratory.

## Variant And Role Selection

Do not collapse materially different business variants into one E2E row just because they share the same feature name. Select separate E2E rows when a variant changes:

- the page sequence, required screens, dialogs, or conditional sections;
- the role workflow, maker/checker handoff, approval queue, or ownership transfer;
- the persisted state model, audit record, notification, downstream event, or settlement path;
- a compliance, revenue, permission, or operational-risk decision;
- the user-visible confirmation or later-page visibility that proves the business outcome.

Keep variants below E2E when they only change field values, labels, enum payloads, copy, formatting, or validation permutations that API/service or UI/Component tests can prove.

## Combination Sampling

When a workflow has multiple trade types, roles, statuses, surfaces, and actions, select E2E rows by risk and representation instead of cartesian expansion. Do not force dual-layer duplication. Add a missing layer only when story risk, trust requirement, or defect history justifies it.

Preferred pattern:

- Cover one critical allowed action from one high-value surface.
- Cover a different critical action from the other high-value surface when both surfaces matter.
- Cover at least one real forbidden role path when access-control trust is user-visible.
- Add one E2E for each materially different business-variant or lifecycle class.
- Add extra E2E rows only when surfaces use independent implementations, a combination has high compliance/revenue/user harm, or there is defect history.

## Select E2E Scenarios For

- Revenue, activation, compliance, safety, or high-support-cost workflows.
- Critical happy paths that must work after every deploy.
- One representative negative or recovery path when user trust depends on the full journey behavior.
- Cross-service flows where lower layers cannot prove the integrated outcome.
- Role or tenant journeys where user-visible access control must be trusted.
- End-to-end confirmation that a completed UI action persists and is visible in a later page, session, role, notification, audit record, or downstream system.

## Avoid E2E For

- Required-field checks, invalid format messages, inline validation, disabled submit states, focus behavior, and error clearing. Put these in UI/Component unless the requirement is explicitly about a full journey-level recovery.
- Server-side request validation, required API fields, schema errors, status/error bodies, and boundary payloads. Put these in API/service or contract tests.
- Exhaustive field permutations better covered by unit, component, or API tests.
- Backend rule combinations already covered at API/service level.
- Single-component rendering states, dialog behavior, local client state, sorting/filtering, field formatting, and responsive layout unless they are part of a critical integrated journey.
- Pure visual styling unless it affects accessibility, trust, or compliance.
- Unstable third-party dependencies unless controlled by test doubles or seeded sandboxes.
- Assertions that require brittle selectors, timing, or implementation details.
- Proving real authorization, persisted workflow state, audit, or cross-role handoff with mocked auth/API responses. Put mocked permission display states in UI/Component, and prove enforcement at API/service plus a thin real-role E2E journey when user trust depends on it.
- Automation implementation needs such as locators, snippets, step definitions, fixture files, custom Java, or `snippet additions required`. Those belong to automation planning after BDD feature authoring.

## E2E Case Template

`id | persona | entry point | precondition/data | journey | expected business outcome | UI oracle | backend/side-effect oracle | priority | stability notes`

## Mock And Test Double Boundary

- Use real authenticated users, real role assignments, and real backend authorization for E2E rows whose target risk is access control, approval, persistence, audit, or workflow state.
- Use API seeding or direct database/test-data factories to create prerequisite data when that setup is not the target behavior.
- Mock or stub only non-target dependencies that would make the journey unstable, such as third-party notifications, market data, payment rails, or external sandboxes. Record the mocked boundary in stability notes.
- Do not assert that a maker lacks approve permission only by mocking `canApprove=false` in the browser. That belongs in UI/Component. E2E should verify the maker cannot perform the action through the real app path or direct navigation, and API/service should verify the backend rejects the unauthorized approval request.
- It is acceptable to pair one real-role E2E with broader API/service authorization matrix rows and UI/Component permission-display rows.

## Design Guidance

- Assert user-visible outcomes and durable business state.
- Keep the journey close to how a real user thinks.
- Define deterministic data setup, cleanup, and account ownership.
- State feature flags, browser/device scope, localization, and accessibility checks when relevant.
- Prefer one E2E scenario per critical journey variant plus lower-layer coverage for pure data permutations.
- When a journey contains form validation, cover only the journey-level consequence in E2E, such as "the user cannot submit an incomplete application and remains in the draft flow"; leave individual required fields and inline messages to UI/Component or API/service.
- Record stability risks: async processing, external systems, email/SMS, payments, time, and shared data.

## Common Re-Routing Examples

| Behavior | Preferred Layer | Why |
| --- | --- | --- |
| Required field shows inline error | UI/Component | Local form validation and accessibility oracle. |
| Required API field returns validation error | API/service | Request contract and error body. |
| Submit button disabled until required fields are valid | UI/Component | Local state and interaction. |
| User completes checkout and order appears in order history | UI E2E | Cross-page journey with persisted business outcome. |
| Unauthorized user cannot access admin workflow from navigation and direct URL | UI E2E plus API/service | E2E proves user-visible access flow; API proves enforcement details. |
| Maker creates trade, maker cannot approve, checker approves and status/audit updates | UI E2E plus API/service | Cross-role workflow and persisted approval outcome need real-role confidence; API covers authorization matrix. |
| Trade type changes visible workflow sections and approval/state behavior | UI E2E for representative materially different variants plus lower layers | Distinct business journeys need distinct E2E signals; simple permutations stay below E2E. |
| Role-specific button visibility from mocked permission state | UI/Component | Mock proves UI rendering for permission state, not real authorization. |
| Error banner appears after backend outage during critical submission | UI E2E only if recovery trust depends on the full journey | Otherwise component/API can prove the variants more stably. |
