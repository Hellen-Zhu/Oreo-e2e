---
description: Implement and run the new scenarios of oreo-e2e feature files - data, snippets, elements, glue; check and dry run; a narrow real run after approval; recordings reviewed; bounded repair
argument-hint: <ADO story ID | workflow-state JSON file | storyId | feature file path(s)> [case IDs]
---

# Implement and run the test cases

You orchestrate: `test-agent` implements and validates without booking trades; you run the real cases after the user
agrees, take the user through the recordings, and route failures. Rules: `CLAUDE.md`;
`docs/standards/bdd-feature-implementation-standards.md`.

## Input

`$ARGUMENTS`, one or more of:

| Mode | Detection | Feature files and cases |
|---|---|---|
| **ADO** | a pure number, `#1234` or `ado:1234` | `ado-agent` (`FIND_BDD_FILE`); cases: the story's `tcTags` |
| **JSON file** | a path ending in `.json` | its `bddFeatureFiles` and `tcTags` |
| **Local story** | a `storyId` with `.ai-sdlc/<storyId>/state.json` | its `bddFeatureFiles` and `tcTags` |
| **Feature file** | a `.feature` path | that file; the cases given after the paths, else the scenarios the working tree adds (`git diff -U0 -- <path>`: new `[TC-...]` lines), else ask which cases |
| **Empty** | - | ask: "Give ADO User Story ID(s), a workflow-state JSON file, or feature file path(s)." |

ADO mode needs `ado-agent` (company `ai-tooling`); if it is not installed here, say so and ask for the feature file
paths or a JSON file instead. Only the selected cases are implemented and run, never the whole file.

## Step 0 - Load workflow context

- **ADO**: `ado-agent` (`LOAD`). **JSON file** / **local story**: read it. **Feature file**: no workflow state; skip.
- `lastCompletedStage` is `codetestcases` and `e2eStatus` is `passing`: ask "E2E tests already passing for <storyId>.
  Run again? (yes/no)".
- `e2eStatus` is `failing` and `e2eRepairAttempts` has reached 3: show the last summary and ask whether to start again
  with a new budget; a repeat of the same blocker is a decision for the user, not another round.

## Step 1 - Where

Decide `where` with the skill `qa-bdd-test-execution` (`company` / `offline`). Offline, the steps below that run Maven
hand over the commands instead, and the run steps are skipped.

## Step 2 - Implement and validate

Invoke `test-agent` with this context and nothing else:

```json
{
  "featureFiles": ["<path>"],
  "caseIds": ["<case ID>"],
  "where": "company | offline",
  "retryBudget": 3
}
```

Show its output exactly as returned. If `validationStatus` is `failed`, show the blocker summary and stop. If `open`
has items, ask the user for them before going on.

## Step 3 - Approve the real run (company only)

Show, from `VALIDATION_HANDOFF.targets`, the exact command(s) and the cases they select, then:

> **These cases run against the environment of `qa.properties` and book real trades. The first run of each new API
> case records its response and fails once.**
> - **yes** - run them
> - **no** - stop here (the commands stay above)
> - **edit first** - wait until you say ready

## Step 4 - Run and record

With the skill `qa-bdd-test-execution`, mode `real-run`, one invocation for all targets.

- New API cases (`expectedRecordings`): their first run writes `data/api/expected/<event>/<case ID>.json` and fails.
  Run the same selection a second time. A case that fails again on a value that changed between the runs (an ID, a
  date, a version) needs a `dynamicFields` rule: add it to the event file (`docs/test-data.md`, "Recorded responses"),
  then run once with mode `record` (ask first) and once more normally.
- **Review every new recording with the user before it is trusted**: for each file, show the business values that
  matter to the scenario (status, trade state, amounts, messages) next to what the requirement says. A value the
  requirement contradicts is a `behavior` finding, never "the expected value".
- Then `mvn -Ptest-data process-test-classes "-Dexec.args=compact <event>"` per event and the offline check.
- Done when each selected case passes twice in a row.

## Step 5 - Failures

Route each failed case by its class (the execution report gives it):

| Class | Route |
|---|---|
| `automation` | `test-agent` repair round with the failures; then Step 4 again for those cases |
| `recording` | Step 4's recording flow |
| `behavior` | Stop for that case: show expected (requirement) vs actual; the user decides whether it is a defect or a requirement question. A defect: skill `qa-defect-reporting` drafts the ADO Bug and creates it on the user's approval. Never change data, recordings or steps to match the system |
| `environment` | Stop: report it; nothing to fix in this repository |

At most 3 repair rounds in total; then stop and summarize what still fails and why.

## Step 6 - Save workflow context and summarize

Fields, in every mode that has workflow state (not for feature-file input):

```json
{
  "lastCompletedStage": "codetestcases",
  "e2eStatus": "passing | failing | not-run",
  "e2eRepairAttempts": 0,
  "e2eFiles": ["<every file created or changed>"]
}
```

`lastCompletedStage` moves to `codetestcases` only when every selected case passed twice; otherwise it keeps its value.
`not-run`: offline, or the user declined the real run.

- **ADO**: when all selected cases pass, `ado-agent` (`ADD_COMMENT`) with the results (cases passed, the `--name`
  selection to run them again); then (`SAVE`) the fields above, whatever the result.
- **JSON file**: edit the source file. **Local story**: update `.ai-sdlc/<storyId>/state.json`.

Then report:

- Files by kind: case data, setup data, snippets (`api` / `ui/flows|pages|components`), elements, Java glue, recordings
- Results per case (passed twice / failing: class) and the open items
- The commands to run the cases again (the `--name` selection)
- Next: review `git diff` (recordings against the requirement, no addresses or real trade IDs), then commit. Nothing
  is committed by this command.
