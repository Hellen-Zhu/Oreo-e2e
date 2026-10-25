<!-- Transcribed from IMG_6414–6415. -->

# API Detail Design

Use this reference when routed rows involve service behavior, endpoints, contracts, auth, integrations, events, or state changes.

## Source Order

Design API tests from business behavior first, then calibrate the implementation contract.

1. Acceptance criteria, business rules, roles, state model, and side effects.
2. Swagger/OpenAPI or equivalent API contract for endpoint shape.
3. Existing API tests, production incidents, analytics, logs, and consumer contracts.

Swagger/OpenAPI answers "what does the interface expose?" It does not fully answer "what business risk must be proven?"

## Swagger/OpenAPI Calibration

Use Swagger UI, the OpenAPI JSON/YAML, or generated API docs when an endpoint is new, changed, unclear, or being automated for the first time.

Check:

- path, method, version, and deprecation status;
- auth scheme, required headers, cookies, tenant/account context;
- path/query/header parameters and required/optional request fields;
- enum values, nullable fields, defaults, formats, min/max, arrays, and nested objects;
- documented status codes, error body shape, and examples;
- response schema, pagination, sorting/filtering contract, and generated identifiers;
- backward compatibility risk for consumers, mobile clients, events, or reports.

Record any mismatch between AC and Swagger/OpenAPI as a clarification or contract gap. Do not silently follow Swagger when it conflicts with the story.

## Case Categories

- Contract: endpoint, method, required fields, optional fields, enum values, response schema, error schema, versioning, backwards compatibility.
- Authentication and authorization: unauthenticated, unauthorized, wrong role, wrong owner, wrong tenant, expired token, permission change, cross-tenant access.
- Input validation: missing fields, malformed values, boundary values, normalization, uniqueness, unsupported media type, oversized payload, invalid enum.
- Business rules: eligibility, limits, calculations, state-dependent behavior, feature flags, policy decisions, rule priority.
- State transitions: create, update, cancel, expire, retry, rollback, duplicate request, idempotency key, concurrent request, stale version.
- Error handling: status code, error code, human/debug message, retryability, dependency timeout, partial failure, compensation.
- Data visibility: filtering, pagination, sorting, masking, tenancy, audit fields, deleted/archived records.
- Integration: downstream request, event emitted, webhook payload, queue behavior, eventual consistency, reconciliation.
- Observability: logs, metrics, trace ID, audit record, support diagnostics.

## Action Availability And Authorization

When UI actions depend on role, status, ownership, tenant, or business variant, API/service owns the authoritative matrix if the backend returns available actions or enforces the action.

Design rows for:

- allowed action for valid role/status/context;
- forbidden action for wrong role, wrong owner, wrong tenant, or terminal status;
- state unchanged on forbidden action;
- correct state transition, audit record, event, or notification on allowed action;
- available-actions response, if exposed, for representative roles/statuses/business variants.

Do not duplicate UI surfaces here. If blotter and trade details call the same approve/reject API, one API authorization matrix covers both surfaces. Surface-specific button behavior belongs in UI/Component; one or more real surface journeys may be sampled in E2E. Do not force UI E2E duplicates for these unless new story risk explicitly requires a user-visible integrated journey.

## API Case Template

`id | endpoint/event | precondition | request/input | expected response/output | side effect | oracle | priority | notes`

## Design Rules

- Include status codes and payload shape when they are external contract behavior.
- Assert durable side effects when the API changes state.
- Trace every high-priority API case back to an AC, business risk, production risk, or consumer contract.
- Separate authorization, validation, and business-rule failures unless the rule combines them.
- For role workflows, cover the backend authorization matrix directly. Example: maker can create a trade, maker cannot approve the same trade, checker can approve it, wrong checker/tenant cannot approve it, and each allowed action produces the expected state/audit side effect.
- Do not rely on mocked UI permission responses as proof of authorization. UI/Component may prove presentation; API/service must prove enforcement, and UI E2E may add one real-role workflow for integrated confidence.
- Include idempotency and concurrency for write operations that may be retried.
- Include backwards compatibility when consumers, mobile clients, or events depend on the schema.

## Common Mistakes

- Only testing 200/201 success paths.
- Checking response status but not persisted state or emitted events.
- Overusing API tests for pure calculations better covered at unit level.
- Ignoring race conditions, duplicate requests, stale versions, and retry behavior.
- Treating API tests as a replacement for contract tests when multiple consumers depend on the shape.
