<!-- Transcribed from IMG_6419–6420. -->

# UI/Component Detail Design

Use this reference when routed rows involve screens, components, forms, dialogs, client state, validation, conditional rendering, accessibility behavior, or user interaction that can be proven below full E2E.

## Purpose

UI/Component tests prove local user-facing behavior with controlled data and dependencies. They should reduce E2E scope by covering rendering states, interactions, validation, accessibility basics, and edge variants close to the UI surface. Treat `UI/Component` rows as design-time ownership for local UI behavior; implement them via stable snippet-level assertions and targeted UI scenarios when no standalone component runner is available.

## Inputs To Look For

- Acceptance criteria and business rules that affect visible UI.
- UX/design notes, component names, page/module/dialog surfaces, or Storybook stories.
- Props, state, role/permission inputs, feature flags, and supported variants.
- Business variants that change visible sections, labels, enabled actions, warnings, or local interaction behavior.
- Client-side validation rules and formatting.
- API/mock data shapes needed to render states.
- Empty, loading, error, retry, disabled, readonly, and partial-data states.
- Accessibility expectations: labels, roles, keyboard flow, focus, announcements, contrast-sensitive states.

## Coverage Dimensions

Design rows across these dimensions when relevant:

- Rendering: default, empty, populated, boundary values, long text, optional fields, formatting, localization-sensitive values.
- Interaction: click, type, select, filter, sort, expand/collapse, dialog open/close, keyboard interaction, focus movement.
- Client validation: required fields, invalid format, boundary length/value, disabled submit, inline errors, error clearing.
- State transitions: draft to valid, loading to success, loading to error, retry to success, saved to dirty.
- Permissions and flags: visible, hidden, disabled, readonly, alternate copy, restricted actions, mocked role/permission states.
- Business variant rendering: trade type, product type, account type, tenant type, region, or feature-flag variants that change local UI composition but do not require a full persisted journey to prove.
- Accessibility: semantic role/name, label association, keyboard reachability, focus trap/restore, aria-live or status messaging.
- Dependency behavior: mocked API success/error, delayed response, partial response, missing optional fields.
- Visual/layout risk: responsive breakpoints, overflow, truncation, modal sizing, sticky headers, table density, critical alignment.

## Action Visibility And Surface Matrix

Use UI/Component tests for button visibility, enabled/disabled state, action menu contents, dialogs, warnings, and local interactions across surfaces.

First identify the action source:

- Backend-driven `availableActions`: mock API responses for role/status/action combinations and assert the surface renders the correct actions.
- Frontend action resolver: unit test the full resolver matrix, then component-test representative rows on each surface.
- Mixed source: test the frontend-owned presentation here and leave backend enforcement to API/service.

For multi-surface workflows such as blotter and trade details:

- Cover each independently implemented surface at UI/Component level.
- Include representative maker/checker/status states per surface.
- Include approve and reject dialogs, confirmation, loading, error, and no-action states when the surface owns them.
- Do not claim button visibility proves authorization. It proves only the UI projection of action state.

## Handoff Notes

Include a UI/Component handoff section when any rows use the `Component/UI` layer:

- Target components, pages, modules, or dialogs.
- Source UX/design references used or missing.
- Required mocked data and dependency states.
- Testability needs such as stable accessible names, test ids, or component harness support.
- Component rows that should remain below E2E and the reason.
- E2E rows still needed for cross-page confidence.
- Open UX/accessibility questions.

## Layer Boundaries

- Do not use UI/Component tests to prove server-side business rules unless the UI owns the rule.
- Do not duplicate API contract assertions here; mock service behavior when the UI outcome is the target.
- Use mocked role, permission, API, or feature-flag states to prove UI presentation and local interaction only. Do not claim mocked component tests prove real authorization, persisted state, workflow handoff, audit, or downstream effects.
- Do not turn UI/Component rows into click-by-click BDD scenarios.
- Do not promote every component variant into E2E; Keep E2E for business-critical journeys and integration confidence.
- Promote a variant to E2E when the variant changes the real user journey, cross-page flow, role handoff, approval/state lifecycle, or business outcome. Keep simple rendering or validation differences here.
- If a row depends on real navigation, auth, backend persistence, or cross-page side effects, classify it as UI E2E or integration instead.
