---
description: Turn one user story into approved test points and business-readable oreo-e2e feature files, with an approval gate after each phase
argument-hint: <ADO story ID | workflow-state JSON file | story file path | pasted story text>
---

# Write BDD feature files

You orchestrate: the `bdd-agent` analyses and authors, the user approves, you write the approved files and check them.
Rules: `CLAUDE.md`; the standards under `docs/standards/`.

**Scope: feature files only.** From the approved test points, business-language scenarios with their case IDs, tags,
product Outlines and trace annotations. This command never implements a step or writes test data, snippets,
elements, glue or recordings; a step without a definition is listed for `/codetestcases`, which makes the cases run.

## Input

`$ARGUMENTS`, detected in this order:

| Mode | Detection | Story and workflow state come from |
|---|---|---|
| **ADO** | an ADO User Story ID: a pure number, `#1234` or `ado:1234` | `ado-agent` |
| **JSON file** | a path that ends with `.json` (a workflow-state file, e.g. `./workflow-state/L001.json`) | the file |
| **Story file** | any other path (`.md`, `.txt`) | the file; state in `.ai-sdlc/<storyId>/state.json` |
| **Story text** | pasted text | the text; state in `.ai-sdlc/<storyId>/state.json` |
| **Empty / invalid** | - | ask: "Give an ADO User Story ID (e.g. `#1234`), a workflow-state JSON file (e.g. `./workflow-state/L001.json`), or paste the story." |

The story needs a title and acceptance criteria (and `solutionDesign` when there is one). `storyId` is the ADO ID,
the file's story ID, or, when the text has none, a short ID you ask for (letters, digits, `-`). Story text stays in
the conversation, in ADO or under `.ai-sdlc/` (git-ignored); never copy it into the repository.

**ADO mode needs `ado-agent`**, the company `ai-tooling` agent (`.claude/agents/ado-agent.md`). If it is not installed
here, say "ado-agent is not available in this repository: paste the story or give a workflow-state JSON file" and
continue in the mode of what the user gives; never guess the story from the ID.

## Step 1 - Load workflow context

- **ADO**: `ado-agent` (`FETCH_STORY`) for each story ID. If the story's `bddFeatureFiles` is non-empty, show them and
  ask "BDD feature files already generated for #<id> (tag: bdd-ready): <files>. Run again? (yes/no)".
- **JSON file**: read it. If `lastCompletedStage` is `writebddfeatures` or later, show its `bddFeatureFiles` and ask
  "BDD feature files already generated for <storyId>: <files>. Run again? (yes/no)".
- **Story file / text**: if `.ai-sdlc/<storyId>/state.json` exists and lists `bddFeatureFiles`, ask the same.

No: stop. Yes: continue with Step 2.

## Step 2 - Phase 1: test layering

Invoke `bdd-agent` with this context and nothing else (extra instructions override its skill):

```json
{
  "phase": 1,
  "story": "<the story, as given>",
  "artifactPath": ".ai-sdlc/<storyId>/test-layer-analysis.json",
  "goal": "design the layered test points of this story and return them for approval"
}
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

```json
{
  "phase": 2,
  "story": "<the story, as given>",
  "artifactPath": ".ai-sdlc/<storyId>/test-layer-analysis.json",
  "goal": "author the feature content of the approved test points and return it for approval"
}
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

## Step 5 - Check the feature files

Use the skill `qa-bdd-test-execution` to decide `where`.

- `company`: when a product Outline was written, `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"`
  (it fills the `Examples` rows, part of the feature file), then show `git diff` of the `Examples`; then
  `mvn -Ptest-data process-test-classes`. Read only the problems about the feature files: a case ID whose prefix or
  channel does not match its file, a duplicated case ID, `Examples` that do not match the registry. Fix those in the
  written content with the user.
- Expected and left alone, for each new case: its event data file or row not found, its expected file missing. They
  are `/codetestcases`'s work; never create data to silence them.
- `offline`: give the same commands for the company machine.

## Step 6 - Save workflow context

The same fields in every mode; append to existing arrays, never drop what an earlier run recorded:

```json
{
  "lastCompletedStage": "writebddfeatures",
  "bddFeatureFiles": ["<every feature file created or appended>"],
  "tcTags": ["<the case IDs written>"]
}
```

`tcTags` keeps the company field name; in this project it holds the case IDs from the scenario names, which are not
tags.

- **ADO**: `ado-agent` (`ADD_COMMENT`) with the coverage summary of Step 7, then (`UPDATE_TAGS`) add `bdd-ready` to
  `System.Tags`, then (`SAVE`) the fields above plus `branch` (the current branch).
- **JSON file**: edit the source file: the fields above.
- **Story file / text**: write `.ai-sdlc/<storyId>/state.json`: `storyId` and the fields above.

## Step 7 - Summary

- Files created / appended (paths), and the case IDs per file
- Totals: `@ADO#smoke` / `@ADO#regression`, `@ADO#positive` / `@ADO#negative`
- Items for the FE repository, gaps, new steps the implementation must provide
- Check result (or the commands to run)
- Next: review `git diff`, then `/codetestcases <storyId>` (the ADO ID or the JSON file in those modes) to implement
  and run them; `/writebddfeatures #<next-story-id>` for the next story. Nothing is committed.
