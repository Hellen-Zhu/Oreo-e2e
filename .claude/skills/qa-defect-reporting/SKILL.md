---
name: qa-defect-reporting
description: Use when a failure found by an oreo-e2e run is a product defect to raise in Azure DevOps (ADO) - "raise a bug for TC-...", "log this failure as a defect", "file it in ADO", or after /codetestcases stops on a `behavior` failure and the user says it is a defect. Drafts one ADO Bug under the configured parent work item through the Azure DevOps MCP server and creates it only after the user approves the draft.
---

# QA defect reporting (oreo-e2e)

One defect is one ADO **Bug**, a child of the parent work item set in the local configuration, with the case ID,
the requirement it breaks and the evidence of the run. The Bug is outward-facing: it is drafted in the chat and created
only after the user approves that draft. Nothing in the repository changes.

## 1. Is it a defect?

Raise a Bug only when all of these hold; otherwise stop and say which one is missing:

- **Class `behavior`**: a status or business value differs from what the requirement says (the classes are in
  `.claude/skills/qa-bdd-test-execution/SKILL.md`, "Reading the result"). `automation` and `recording` failures are
  fixed in the repository; `environment` (login, connection, 5xx across unrelated cases, timeouts) is reported to whoever
  runs the environment, not as a product Bug, unless the user says otherwise.
- **A requirement says what should happen**: the acceptance criterion, story or BA answer, quoted with its source. A
  recorded response shows what the system did, not what it should do (`CLAUDE.md` §5): a case that only disagrees
  with its recording is a question for the user, not a Bug.
- **It fails again**: the case failed in two runs, or the user confirms it is not a one-off. A second run books trades:
  ask before running it, with the narrowest `--name` selection.

Failures with one cause (the same wrong value in several products or cases) are one Bug listing every case.

## 2. Configuration and tools

The ADO settings are in `ado-defects.local.properties` at the project root, git-ignored (`**/*.local.properties`):
the project, the parent work item ID and the tags are company data and never enter the repository. Missing file:
copy `ado-defects.local.properties.example` to it and ask the user for each empty value; never guess one.

| Key | Use |
|---|---|
| `ado.project` | The ADO project of the Bug and of the parent |
| `ado.defect.parentId` | The parent work item (a Feature) every Bug is linked under |
| `ado.defect.areaPath`, `ado.defect.iterationPath` | Optional; empty: the parent's `System.AreaPath` / `System.IterationPath` |
| `ado.defect.tags` | Tags on every Bug, `;`-separated |

The tools are those of the Azure DevOps MCP server (their names end in `wit_get_work_item`, `wit_create_work_item`,
`wit_work_items_link`, `search_workitem`, `wit_add_work_item_comment`; the prefix depends on how the server is
registered). Not in this session: say "the Azure DevOps MCP server is not connected", and give the draft of step 4
ready to paste into ADO by hand. Never call the ADO REST API another way, and never ask for or handle a token.

## 3. Duplicates

Before drafting, look for an open Bug on the same failure:
- `search_workitem` with the case ID (and the product) in the project, work item type Bug, state not Closed / Removed;
- the parent's children: `wit_get_work_item` on `ado.defect.parentId` with its relations.

A match: show it and propose a comment on it (`wit_add_work_item_comment`: this run's date, command and result) instead
of a new Bug. The user decides.

## 4. Draft

Collect, from the run and the repository:

| Evidence | From |
|---|---|
| Case ID, title, feature file and line, layer (`@api` / `@playwright`), product | The feature file |
| Steps in business words | The scenario's steps, with the Examples values filled in |
| Expected, with its source | The requirement (step 1) |
| Actual | `target/results/failed-scenarios-*.log` (newest of the run), the comparison message (`Expected file: ...` and the difference) |
| Request sent (API) | `mvn -Ptest-data process-test-classes "-Dexec.args=case <case ID>"` |
| Trade or task the run created | The run's log (`createdTradeId`); it goes into the Bug, never into the repository |
| Screenshot / trace (UI) | `target/traces/*.zip` and the failure screenshot: named in the Bug as local files; the user attaches them |
| Environment | The environment's name (`qa`), browser and mode from `genie.properties` (`oreo.risk.engine.mode`), the commit (`git rev-parse --short HEAD`), the date and the exact run command |

Remove from every value: `Authorization` / cookie headers, tokens, passwords, user credentials. A server address only
when the user wants it in the Bug.

The fields (ADO Agile / CMMI Bug):

| Field | Value |
|---|---|
| `System.Title` | `[E2E] <case ID>: <what fails, in business words>`, under 120 characters; several cases: the shared cause and `(<n> cases)` |
| `Microsoft.VSTS.TCM.ReproSteps` (HTML) | **Preconditions** (trade state, user role, product), **Steps** (numbered), **Expected** (with the requirement's source), **Actual**, **Evidence** (the rows above), **Automation** (case ID, feature file, the `--name` command to run it again) |
| `Microsoft.VSTS.TCM.SystemInfo` (HTML) | Environment, browser, risk engine mode, commit, run date |
| `Microsoft.VSTS.Common.Severity` | Proposed with one line of reason: `1 - Critical` (booking or a lifecycle event blocked, wrong economics), `2 - High` (wrong status or value, no workaround), `3 - Medium` (workaround exists), `4 - Low` (cosmetic) |
| `Microsoft.VSTS.Common.Priority` | Proposed `2`; the user decides |
| `System.AreaPath`, `System.IterationPath` | From the configuration, else the parent's |
| `System.Tags` | `ado.defect.tags`, the case ID(s), the product(s) |

Write the Bug in English. Show the draft in the chat as it will be sent (the fields above and the parent), and ask
for approval: **create / edit / stop**. A severity or priority the user has not seen is not approved.

## 5. Create

Only after the user approved this draft in the chat:

1. `wit_create_work_item`: project `ado.project`, type `Bug`, the approved fields (`ReproSteps` and `SystemInfo` as
   HTML).
2. `wit_work_items_link`: the new Bug to `ado.defect.parentId`, type `parent`.
3. `wit_get_work_item` on the new Bug with its relations: the parent link is there.

A step that fails after the Bug exists: report the Bug's ID and what failed; never create it a second time. Never
change a Bug's state, assignee or another work item without asking.

## 6. Report

- The Bug: ID, title, link, parent, severity and priority; or the comment added to an existing Bug.
- The case stays failing. Do not change its data, recording, steps or tags to make it pass, and do not add `@wip`:
  skipping a case with a known defect is the user's decision. Do not write the Bug ID into a feature file or any other
  file of the repository.
- With `/codetestcases` workflow state: the Bug ID goes into its open items (`e2eStatus` stays `failing`).

## Common mistakes

| Mistake | Instead |
|---|---|
| A Bug for a missing step, a wrong locator or an unmasked dynamic value | Those are `automation` / `recording`: fix them in the repository |
| "Expected" taken from the old recording | Expected comes from the requirement; without one, ask |
| A Bug created right after drafting it | Show the draft, wait for "create" |
| One Bug per product for the same wrong value | One Bug, every case listed |
| The parent ID or project written into the skill, a feature or a commit | `ado-defects.local.properties`, git-ignored |
| A token or `Authorization` header pasted from the log | Removed from every value |
