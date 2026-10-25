---
description: Turn one user story into approved test points and business-readable oreo-e2e feature files, with an approval gate after each phase
argument-hint: <story file path | pasted story text>
---

# Write BDD feature files

You orchestrate: the `bdd-agent` analyses and authors, the user approves, you write the approved files and check them.
Rules: `CLAUDE.md`; the standards under `docs/standards/`.

## Input

`$ARGUMENTS`:

| Given | Do |
|---|---|
| A file path (`.md`, `.txt`, `.json`) | Read it |
| Pasted story text | Use it |
| Only a tracker ID | This repository has no tracker connection: ask for the story (title, ACs, and `solutionDesign` if there is one) as text or a file |
| Nothing | Ask: "Paste the user story, or give the path of a file that has it." |

The story needs a title and acceptance criteria. Use its ID as `storyId`; when it has none, ask for a short one
(letters, digits, `-`). Story text stays in the conversation and under `.ai-sdlc/` (git-ignored); never copy it into
the repository.

## Step 1 - Load

If `.ai-sdlc/<storyId>/state.json` exists and lists `featureFiles`, show them and ask "Feature files were already
written for <storyId>. Run again? (yes/no)". No: stop.

## Step 2 - Phase 1: test layering

Invoke `bdd-agent` with this context and nothing else (extra instructions override its skill):

```yaml
phase: 1
story: <the story, as given>
artifactPath: .ai-sdlc/<storyId>/test-layer-analysis.json
goal: design the layered test points of this story and return them for approval
```

Show the agent's output exactly as returned, then:

> **Approve the test points?**
> - **approve** - continue to the feature files
> - **revise** - say what to change (move a TP to another layer, change smoke, add or remove a TP ...)
> - **stop** - end here

- revise: invoke `bdd-agent` again with the same context plus `revisionInstructions: <the user's words, exactly>`;
  show the result and ask again.
- stop: "Stopped. The analysis is in `.ai-sdlc/<storyId>/`."

## Step 3 - Phase 2: feature content

```yaml
phase: 2
story: <the story, as given>
artifactPath: .ai-sdlc/<storyId>/test-layer-analysis.json
goal: author the feature content of the approved test points and return it for approval
```

Show the output exactly as returned, then the same approve / revise / stop prompt ("Approve the feature content?").
On revise, add `revisionInstructions` and `previousOutput: <the output just shown>`.

## Step 4 - Write the files

From the approved output only; do not re-derive names, paths, modes or IDs.

- `create`: write the complete content. If the file exists by now, stop and ask.
- `append`: add the approved Scenario / Scenario Outline blocks at the end of the file, one blank line before each;
  nothing else. Before writing, check that none of their case IDs exists yet
  (`grep -rn "<case ID>" src/test/resources/features src/test/resources/data`); if one does, stop and ask.
- `not generated`: nothing.

Keep the file's line endings and the two-space indentation of the existing features.

## Step 5 - Check

Use the skill `qa-bdd-test-execution` to decide `where`.

- `company`: when a product Outline was written, `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"`,
  then show `git diff` of the `Examples`; then `mvn -Ptest-data process-test-classes`. Expected: no problem other than
  one `no expected file` per new API case (they are recorded by `/codetestcases`). Anything else: show it and fix the
  written content with the user.
- `offline`: give the same commands for the company machine.

## Step 6 - Save state

Write `.ai-sdlc/<storyId>/state.json`:

```json
{ "storyId": "<id>", "stage": "writebddfeatures", "featureFiles": ["..."], "caseIds": ["..."] }
```

Append to existing arrays; never drop what an earlier run recorded.

## Step 7 - Summary

- Files created / appended (paths), and the case IDs per file
- Totals: `@ADO#smoke` / `@ADO#regression`, `@ADO#positive` / `@ADO#negative`
- Items for the FE repository, gaps, new steps the implementation must provide
- Check result (or the commands to run)
- Next: review `git diff`, then `/codetestcases <storyId>` to implement and run them. Nothing is committed.
