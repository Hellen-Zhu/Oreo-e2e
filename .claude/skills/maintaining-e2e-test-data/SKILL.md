---
name: maintaining-e2e-test-data
description: Use when the test data, feature files, recorded API responses or product registry of oreo-e2e change, before committing them - "check the test data", "what does case X send", "compact the expected files", "write the Examples", "a product or capability was added", "the API added a field to the response" - or when a data mistake got past mvn test.
---

# Maintaining the test data (oreo-e2e)

The offline tool is `TestDataTool` (`src/test/java/com/example/module/oreo/tools/`), run only through
`mvn -Ptest-data process-test-classes "-Dexec.args=<command>"` (no `exec.args`: `check`). A plain `mvn test` does not
check the data.

**Read `docs/test-data.md`, "Commands", before running it**: every command, what each writes, what `check` checks,
and the Maven troubleshooting. The data format and the procedures for recordings and shared parts are in the same
file. This skill only picks the command for the change.

Run Maven only on the company machine (`qa-bdd-test-execution`, "Where it runs"); elsewhere hand over the commands.

## Which command after which change

| Change | Run, in order | Procedure |
|---|---|---|
| Event or setup data, a row, `byProduct`, `use`, `datFile`, `productId`, an amount as a share, a `${date:...}` | `case <ID>` for each case touched, then `check` | `docs/test-data.md`, "Where a value goes", "Case data" |
| A product or capability in `ProductDatFiles`, a `@capability#` tag | `sync-examples`, review `git diff`, `check` | a new product: skill `onboarding-e2e-product` |
| API responses recorded (first run, or `OREO_EXPECTED=update`) | `suggest <event>`, add the rules you agree with, record again; `compact <event>`, review `git diff`, `check` | "Recorded responses" |
| The API changed a response on purpose | — | "Updating a recorded response" |
| The API adds a field to every product's response or request | — | "A new response field", "Maintenance" |
| A shared part (`basic`, `instrument`) changed | — | "Updating a shared part" |
| Feature files | `check` (`sync-examples` first when a product Outline was added) | — |
| Steps or snippets deleted or renamed | `unused-steps` (on the company copy before deleting) | "Commands" |

## When `check` fails

Fix the data, never the check: a name added to `check.knownMissingSetups`, a loosened rule or `-DskipTests` is the
user's decision, not a fix. Read each `[FAIL]` line against the numbered list in "Commands"; `no expected file` for an
API case that has not run yet is expected until its first recording.
