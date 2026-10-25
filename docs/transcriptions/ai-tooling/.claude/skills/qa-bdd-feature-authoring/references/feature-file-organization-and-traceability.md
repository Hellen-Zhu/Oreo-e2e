<!-- Transcribed from photos; overlapping passages merged. Anything after the Create-mode repo-style alignment check was not supplied. Archival source text only. -->

# Feature File Organization and Traceability

Use this reference to determine feature file location, grouping, `businessDomain`, `featureTag`, feature tags, scenario tags, TC IDs, and scenario-title traceability.

## Feature file location

Feature file paths must be relative to the project feature root unless project context states otherwise. In many projects, that root is `{E2E_DIR}/src/test/resources/features/`.

Use these default path shapes:

- API: `{E2E_DIR}/src/test/resources/features/api/{businessDomain}/{featureTag}.feature`
- UI/E2E: `{E2E_DIR}/src/test/resources/features/ui/{businessDomain}/{featureTag}.feature`

## Naming priority order

When more than one plausible name exists, choose in this order:

1. existing approved target file for the same capability
2. established naming family already present in the same `businessDomain`
3. nearest comparable entity pattern in that domain
4. freshly derived business name from the current approved scope

This means repo-local consistency beats a newly invented but semantically plausible label.

If the domain already uses a stable family such as `{entity}_management`, do not switch to a sibling family such as `{entity}_lookup` unless the approved scope explicitly requires a new convention.

## Same-domain consistency check

Before finalizing a new file in `create` mode:

- inspect sibling feature files in the same `businessDomain`
- identify title, stem, tag, and TC-prefix patterns used by comparable files
- align to that style by default

Use a new naming family only when:

- no credible same-domain baseline exists, or
- the approved scope clearly establishes a distinct capability that would be misleading under the existing family

## Derive naming inputs

Derive:

- `businessDomain` from the story module or domain, normalized to snake_case
- `featureTag` usually as `{primary_business_object}_{primary_business_action}`, normalized to snake_case

Do not create separate feature files for every scenario. Group scenarios by coherent business capability.

## `businessDomain` Routing

| Signal | Domain |
|---|---|
| trade/trf/cancellation/amendment/checker approval/novation/partial novation/termination/early termination/portfolio reassignment/step out/allocation/lifecycle/audit | `trading` |
| reference data/mapping | `reference_data` |
| product/composer/compose | `compose` |
| risk | `risk_management` |
| user/permission/user configuration | `user_management` |
| notification | `notification` |
| login/authentication | `authentication` |
| otherwise | `general` |

Known trading events (`Create`, `Amendment`, `CheckerApproval`, `Audit`, `PartialNovationRemaining`, `PartialTermination`, `PortfolioReassignment`, `Cancellation`, `EarlyTermination`, `NovationOutgoing`, `NovationRemaining`, `PartialNovationOuting`, `StepOutFull`, `StepOutPartial`, `Allocation`) all map to `trading`.

Rules:

- If multiple signals match, prefer the most business-facing object in the title, description, or confirmed test points.
- Technical module naming must not override business domain.
- Never use story module, Java classes, endpoints, story IDs, TC IDs, or `TP-###` for feature naming.

## `featureTag` Derivation

### `primary_business_object` Conventions

- Use a stable singular domain entity noun (`trade`, `product`, `portfolio`, `user`), never a technical artifact.
- Use the nearest stable parent entity (all trade lifecycle events use `trade`).
- Do not use plurals.

### `primary_business_action` Conventions

- Use a base verb or established domain noun form (`creation`, `cancellation`, `novation`, `allocation`, `management`).
- Forbidden generic verbs: `manage`, `handle`, `process`, `validate`, `execute`.
- Event-driven stories use the event concept in snake_case.

### Binding rule

`featureTag` is not an independent label. It is the canonical identity shared by:

- the file stem
- the feature-level tag
- the TC prefix stem

Therefore:

- file `counterparty_management.feature` requires `@counterparty_management`
- its TC IDs must use `TC-COUNTERPARTY-MANAGEMENT-...`
- if the file stem changes, update all three together

## Feature-level tags

Feature-level tags must include:

- exactly one layer tag:
  - `@api` for API feature files
  - `@playwright` for UI/E2E feature files
- exactly one `featureTag` tag:
  - `@trade_creation`
  - `@user_management`

The `featureTag` must exactly equal the file stem. Never let the file name and feature-level tag drift apart.

## Scenario-level tags

Scenario-level tags must include:

- exactly one priority tag:
  - `@smoke` or `@regression`
- exactly one test type tag:
  - `@positive` or `@negative`
- exactly one TC ID tag:
  - for example `@TC-{FEATURE_MODULE}-API-001`: `{FEATURE_MODULE}` comes from `featureTag`
  - for example `@TC-{FEATURE_MODULE}-UI-001`: `{FEATURE_MODULE}` comes from `featureTag`

`{FEATURE_MODULE}` must derive from the same terminology as the file stem and `featureTag`; do not mix naming families across these identity fields.

## Scenario Title Traceability

Assign stable TC IDs:

- Scenario title format should keep TC traceability, for example:
  - `Scenario: [TC-<MODULE>-API-001] ...`
  - `Scenario Outline: [TC-<MODULE>-<var>-API-001] ...`
  - `Scenario: [TC-<MODULE>-UI-001] ...`
  - `Scenario Outline: [TC-<MODULE>-<var>-UI-001] ...`

Use the module tag or story module to derive `<MODULE>`. Normalize it to uppercase alphanumeric. If the module is unknown, use `MOD`.

## TC ID Stability Rules

Keep TC IDs stable during revision. Add new IDs at the end of the relevant sequence.

## Create-mode repo-style alignment check

For every newly created feature file, run a final style check against sibling files in the same `businessDomain`:

- file stem family is aligned
- `Feature:` title shape is aligned where a stable local pattern exists
- actor/description sentence style is reasonably aligned
- TC prefix naming family matches the file stem and local baseline

If the new file intentionally breaks from repo style, state the reason explicitly in the generation output rather than silently diverging.

<!-- Transcription ends here: the rest of the file, if any, was not supplied. -->
