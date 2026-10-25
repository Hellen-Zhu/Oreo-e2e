<!-- Transcribed from IMG_6471–IMG_6473. Company branding and package prefix anonymized. No frontmatter was visible. Archival source text, not an active skill. The referenced genie_rest_glue_catalog.md was not photographed in this batch. -->

> **On load:** Output exactly: `[skill: qa-api-test-authoring loaded ✓]` then continue.

# Example E2E API Test Implementation

How to implement Genie API test artifacts from an `@api` `.feature` file. Covers the full authoring flow: snippets, Java glue and YAML test data.

## Sub-project Paths

Resolve `{E2E_DIR}` from root `CLAUDE.md` → `# Repos` table. Default: `e2e-test`. Skip any subproject marked `Optional: Yes` that does not exist on disk.

## Inputs

Use the context provided by the calling command:

- `{E2E_DIR}`
- target `@api` `.feature` file(s)
- scenario handoff details, if present
- `revisionInstructions`, if present
- `previousOutput`, if present

## Quick Workflow

Follow this order:

1. Read the target `@api` feature file and treat it as the scope boundary.
2. Read and apply `references/workflow-and-guardrails.md`.
3. Scan existing feature, snippet, YAML, and glue families first, then align with the closest local style before creating anything new.
4. Derive `featureTag`, TC IDs, actors, business preconditions, business actions, business outcomes, and stored-variable expectations from the feature and the repo baseline.
5. Read and apply `references/reuse-and-snippet-architecture.md`.
6. Build the reuse inventory for the changed or newly introduced behavior.
7. Read and apply `references/api-implementation-mechanics.md`, then pass the API verification gate before authoring or revising any API snippet, YAML, or Java glue. At minimum verify the real target behavior for method, resolved path, required headers/auth, required request fields, expected success status, and representative response-envelope or business-field shape.
8. Read `references/genie_rest_glue_catalog.md` only for built-in primitives used inside snippet composition.
9. Update only the required E2E assets.
10. Run the quality checks.
11. Return the implementation report using the exact output contract.

## Scope Boundary & Artifact Set

Treat the feature file as fixed scope unless revision instructions explicitly ask to change it. This skill implements API automation assets under `{E2E_DIR}` only.

Typical outputs are:

- API YAML data under `src/test/resources/data/api/{featureTag}.yml`
- API snippets under `src/main/resources/snippet/api/{featureTag}.snippet`
- API Java glue under `src/main/java/com/example/module/oreo/glue/api/*.java` only when `NEW_STEP` is truly required

## Reuse Inventory

Before authoring, build a table with:

- `step text`
- `status`
- `source`

Use the status values defined in `references/reuse-and-snippet-architecture.md`:

- `REUSE_PROJECT`
- `NEW_SNIPPET`
- `REUSE_BUILTIN`
- `NEW_STEP`

Use this as a decision aid, not busywork. The goal is to ensure every changed or newly introduced behavior has a clear implementation path before asset writing begins.

## References To Load

Use these references as the authoritative rule sources:

| Reference | Use it for |
|---|---|
| `references/workflow-and-guardrails.md` | scope boundary, local style scanning, naming-family reuse, identity binding, API ownership rules, and Cucumber 4.x execution syntax |
| `references/reuse-and-snippet-architecture.md` | reuse order, discovery sources, status model, mapping expectations, snippet placement, phase purity, and Java escalation |
| `references/api-implementation-mechanics.md` | contract lookup, mandatory API verification, YAML structure, schema-first response validation, json-unit usage, ctx injection, HTTP-action placement, custom-client bans, and Java glue constraints. Read this whenever API snippets, YAML, or API glue are created or revised. |
| `references/genie_rest_glue_catalog.md` | built-in primitive catalog for composing `NEW_SNIPPET` steps |

## Quality Checks

Before returning output, verify:

- every relevant Gherkin line was mapped before implementation
- only `{E2E_DIR}` assets were changed
- local naming, snippet, YAML, and glue ownership patterns were checked before introducing new assets
- the API verification gate passed before snippet, YAML, or glue edits, or the work was explicitly stopped as blocked
- featureTag, YAML stem, and snippet stem identities remain aligned
- no raw HTTP action was introduced into feature files
- response assertions validate the expected schema and core business fields, not only `code` and `status`
- no custom HTTP client was introduced into glue
- no `Thread.sleep` was introduced
- `@Then` snippets remain assertion-only
- `-API-` key identity and setup key conventions remain valid
- the implementation report includes verification evidence for the environment or explicitly states why verification was blocked
- the returned report explains what was created, updated, or reused

## Output report

When done implementing a feature's step defs, return the following structured summary:

```text
Authoring Complete — @api
────────────────────────────────────────
featureTag   : {featureTag}            e.g. reason_management
testTypeTag  : @api
TC-IDs       : @TC-{MODULE}-API-NNN, ...

Files created:
  YAML snapshots : src/test/resources/data/api/{featureTag}.yml [created|updated|reused]
  Snippets       : src/main/resources/snippet/api/{featureTag}.snippet [created|updated|reused]
  Java glue      : [path or "none required"]

Verification:
  status         : [verified|blocked]
  environment    : [rest.base.url or properties file used]
  contract check : [{rest.base.url}/v3/api-docs or equivalent evidence]
  endpoints      : [METHOD path, ...]
  observed drift : [none|details]
  blocked reason : ["none" or why verification could not be completed]
```
