---
description: Implement and run the new scenarios of oreo-e2e feature files - data, snippets, elements, glue; check and dry run; a narrow real run after approval; recordings reviewed; bounded repair
argument-hint: <storyId | feature file path(s)> [case IDs]
---

# Implement and run the test cases

You orchestrate: `test-agent` implements and validates without booking trades; you run the real cases after the user
agrees, take the user through the recordings, and route failures. Rules: `CLAUDE.md`;
`docs/standards/bdd-feature-implementation-standards.md`.

## Input

`$ARGUMENTS`:

- a `storyId` with `.ai-sdlc/<storyId>/state.json` -> its `featureFiles` and `caseIds`
- feature file path(s) -> those files; the cases are the ones given after the paths, else the scenarios the working
  tree adds (`git diff -U0 -- <path>`: new `[TC-...]` lines), else ask which cases
- nothing -> ask: "Which story ID or feature file(s)?"

Only the selected cases are implemented and run, never the whole file.

## Step 1 - Where

Decide `where` with the skill `qa-bdd-test-execution` (`company` / `offline`). Offline, the steps below that run Maven
hand over the commands instead, and the run steps are skipped.

## Step 2 - Implement and validate

Invoke `test-agent` with this context and nothing else:

```yaml
featureFiles: [<paths>]
caseIds: [<case IDs>]
where: <company|offline>
retryBudget: 3
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
| `behavior` | Stop for that case: show expected (requirement) vs actual; the user decides whether it is a defect or a requirement question. Never change data, recordings or steps to match the system |
| `environment` | Stop: report it; nothing to fix in this repository |

At most 3 repair rounds in total; then stop and summarize what still fails and why.

## Step 6 - Save state and summarize

Update `.ai-sdlc/<storyId>/state.json` when there is one: `stage: "codetestcases"`, `status: passing|failing|not-run`,
`files` (every file created or changed). Then report:

- Files by kind: case data, setup data, snippets (`api` / `ui/flows|pages|components`), elements, Java glue, recordings
- Results per case (passed twice / failing: class) and the open items
- The commands to run the cases again (the `--name` selection)
- Next: review `git diff` (recordings against the requirement, no addresses or real trade IDs), then commit. Nothing
  is committed by this command.
