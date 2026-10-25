<!-- Transcribed from IMG_6392–6395. Commands and agent instructions below are source-document content. -->

# Generate and Run E2E Test Cases

> **Sub-project paths:** Resolve `{E2E_DIR}` from root `CLAUDE.md` → `# Repos` table before running any shell commands or constructing file paths. Check the `Optional` column — **skip all steps for any sub-project marked `Optional: Yes` that does not exist on disk.** Pass resolved values and optional flags when invoking sub-agents. Defaults: `be-app`, `fe-app`, `e2e-test`.

You are orchestrating E2E test generation and the two-track repair loop for a {ADO_DISPLAY_NAME} User Story. Delegate domain work to sub-agent.

> ⚠️ **This command requires `{E2E_DIR}`.** If `{E2E_DIR}` is `Optional: Yes` in CLAUDE.md and does not exist on disk, stop and output: "E2E tests directory not present — /codetestcases skipped."

## Input

The user will provide one or more ADO User Story IDs or `.feature` file paths. If not provided, ask: "Please provide ADO User Story ID(s) or feature file path(s)."

## Step 0 — Load Workflow Context

- **ADO story ID:** use **ado-agent** (operation: LOAD) to fetch workflow state.
- **JSON mode:** use **Read** to load the JSON file directly — skip ado-agent.
- **Feature file path:** no workflow state available — skip this step entirely.

If `e2eRepairAttempts` ≥ 5 and `e2eStatus` ≠ `"passing"`, display the repair-loop-exhausted message and pause.

If `lastCompletedStage` is already `"codetestcases"` and `e2eStatus` is `"passing"`:

```text
✅ E2E tests already passing for #[story-id]. Run again? (yes/no)
```

Wait for confirmation.

## Step 1 — Locate Feature Files

Determine `bddFeatureFiles` based on the input type:

- **ADO story ID** (numeric, e.g. `12345`): invoke **ado-agent** (operation: FIND_BDD_FILE) to resolve the feature file path(s).
- **Feature file path** (e.g. `e2e-test/features/api/foo.feature`): use the path directly — skip ado-agent.
- **JSON mode** (user passes a JSON object or array with `featureFile` / `bddFeatureFiles` keys): extract the path(s) from the JSON — skip ado-agent.

## Step 2 — Execute Authoring Flow

Invoke **test-agent** with:

- `{E2E_DIR}`
- all feature file paths from Step 1
- `env : sit` if the user specified SIT when invoking this command; otherwise omit (defaults to `dev`)
- workflow context fields needed for retry budgeting if available

> **Prompt discipline:** pass ONLY `{E2E_DIR}` and the feature file path to the agent. Do NOT add instructions about exploring the project, reading conventions, or looking at existing files — that is entirely the skill's responsibility.
> Extra context in the prompt overrides the skill's read budget.

### Step 2 Output

Relay `test-agent` output verbatim.

`test-agent` must append a machine-readable `VALIDATION_HANDOFF` JSON block. Step 3 uses that block as the source of truth for the final standard execution targets.

If the final validation result still fails after retries are exhausted, stop and surface the blocker summary.

In Step 2, post-generation validation is a **dry-run-only sanity gate**. It is not the final user-facing test run.

When Step 2 passes, append:

```text
→ Proceed to run tests? (yes / no / edit first)
```

- **yes** — continue to Step 3
- **no** — stop; user will re-invoke manually after making changes
- **edit first** — wait for user edits, then continue to Step 3 when user says ready

## Step 3 — Run Tests

Invoke the **qa-bdd-test-execution** skill via Skill Tool to trigger **Standard execution** mode, passing:

- `authoring_results`: the `VALIDATION_HANDOFF.targets` entries from Step 2
- `env : sit` if the user specified SIT when invoking this command; otherwise omit (defaults to `dev`)

`qa-bdd-test-execution` will decide the final tag scope, build the standard execution command(s), and run them sequentially.

Capture the skill's full output for use in the repair loop.

## Step 4 — Two-Track Repair Loop

If the **Step 3** standard execution reports failures, invoke **debug-agent** passing:

- full execution output from Step 3
- Story ID and failing `@TC-*` tags
- Current `e2eRepairAttempts` and `e2eTrackBConsecutive` from workflow state
- Workflow state file path

**debug-agent manages the loop internally:**

- Track A: debug-agent applies E2E layer fixes (element locators, YAML snapshots, snippets, Java glue) and re-runs
- Track B: debug-agent issues a REFACTOR signal → you route it to `/codeuserstory #[id]` Re-entry Mode → after fix, debug-agent re-runs

Relay escalation messages (consecutive Track B, exhaustion) directly to the user and wait for their choice.

## Step 5 — Update ADO Work Item

When all targeted scenarios pass: Use **ado-agent** (operation: ADD_COMMENT) with E2E test results (N passed, tags covered, run command).

## Step 6 — Save Workflow Context

Use **ado-agent** (operation: SAVE) to update:

- `lastCompletedStage: "codetestcases"` (only if passing; keep `"codeuserstory"` if exhausted)
- `e2eFiles`: array of all files created — YAML snapshots, element locator JSON, snippets, Java glue
- `e2eRepairAttempts`, `e2eTrackBConsecutive`, `e2eStatus`

## Step 7 — Summary

Output:

- Files created by category:
  - YAML snapshots: `data/api/` or `data/ui/`
  - Element locators: `elements/pages/` or `elements/components/`
  - Snippets: `snippet/api/` or `snippet/ui/`
  - Java glue: `glue/api/` or `glue/ui/`
- Test results: N passed, N failed, N skipped.
- TC IDs covered
- Run commands:

```text
Run smoke     : cd {E2E_DIR} && mvn clean test -Dcucumber.options="--tags @smoke"
Run module    : cd {E2E_DIR} && mvn clean test -Dcucumber.options="--tags @[module]"
Run single TC : cd {E2E_DIR} && mvn clean test -Dcucumber.options="--tags @TC-[MOD]-[NNN]"
```

- Next step: "Run `/codereview` to review the implementation before committing."
