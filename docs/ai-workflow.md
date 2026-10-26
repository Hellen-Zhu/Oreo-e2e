# AI Workflow (QA)

How an agent takes a user story to running oreo-e2e cases. It is the QA part of the company's AI-SDLC tooling (the
`ai-tooling` repository's commands, agents and skills), adapted to this repository's actual framework. The archived
company text is in `docs/transcriptions/ai-tooling/`; this page says what the project version does and why it differs.

## Pipeline

```text
story ──/writebddfeatures──► bdd-agent ── phase 1: qa-layer-test-design ──► test points (.ai-sdlc/<id>/)  ◄ approve
                                       └─ phase 2: qa-bdd-feature-authoring ► feature content              ◄ approve
                             command writes the .feature files, sync-examples, offline check

feature files ──/codetestcases──► test-agent ── qa-api-test-authoring / qa-e2e-test-authoring ► data, snippets, elements, glue
                                             └─ qa-bdd-test-execution: check + dry run (no trades)  ► VALIDATION_HANDOFF
                                  command: real run (after approval) ► recordings reviewed ► compact ► passes twice
```

| Piece | Kind | Job |
|---|---|---|
| `/writebddfeatures` | command (`.claude/commands/`) | Story in; two approval gates; writes feature files |
| `/codetestcases` | command | Feature files in; approval before booking trades; routes failures |
| `bdd-agent` | agent (`.claude/agents/`) | Runs the two design skills; never writes features |
| `test-agent` | agent | Runs the authoring skills and the validation; never runs against the environment |
| `qa-layer-test-design` | skill (`.claude/skills/`) | Test points, layer, evidence, product scope |
| `qa-bdd-feature-authoring` | skill | Feature file, case IDs, tags, step reuse (`scripts/gen-snippet-index.sh`) |
| `qa-api-test-authoring` | skill | Case data, API snippets, API glue; `references/test-data-decisions.md` decides where every data value goes (also used by the UI skill) |
| `qa-e2e-test-authoring` | skill | Flows / pages / components, element repository, UI data, UI glue |
| `qa-bdd-test-execution` | skill | `check`, `dry-run`, `real-run`, `record`; per-case classification |
| `maintaining-e2e-test-data`, `onboarding-e2e-product` | skills | Test data tool; new products (unchanged) |

**The rules live in one place**: `CLAUDE.md` and `docs/standards/*.md`. Skills hold the procedure, the inputs and the
output contract, and tell the agent which standard to read; they do not copy rules. A rule changes in its standard,
not in a skill.

## What differs from the company tooling, and why

| Company `ai-tooling` | oreo-e2e version | Why |
|---|---|---|
| YAML data `data/{api,ui}/{featureTag}.yml`, one key per `@TC-` tag, request and response inline with json-unit placeholders | Layered JSON `data/{api,ui}/<event>.json` (`defaults` → `byProduct` → row), responses recorded to `data/api/expected/`, masked by `dynamicFields` rules | That is how this project's hook and response check work; the YAML rules would produce files nothing reads |
| `@TC-...` scenario tags; tags `@smoke`, `@positive`, `@<featureTag>` | Case ID in the scenario name; `@ADO#smoke`, `@ADO#positive`, `@ADO#<featureName>`, `@capability#<name>` | Project convention, enforced by `TestDataTool check` |
| No notion of products | Product Outlines over `<product_type>`, rows from `ProductDatFiles` by `sync-examples` | One scenario serves every product that supports the capability |
| Four layers (API, UI component, UI integration, E2E) | `@api` and `@playwright`; component-level items listed for the FE repository | This repository has no component runner |
| Validation: `mvn clean test --tags ... --dry-run` | `TestDataTool check` first, then `--dry-run --strict` through `-Dtest=OreoTest` and `--name` | The offline check catches data mistakes without the environment; without `--strict`, Cucumber 4 lets undefined steps pass a dry run; the runner's fixed tags can empty a run, which is reported, not passed |
| Real run straight after validation | Explicit approval first; the first run's recordings are reviewed against the requirement | API runs book real trades; a recording is evidence of what the system did, not of what it should do |
| Repair loop fixes failures | Failures classified: `automation` (fixed), `recording`, `behavior` (stopped, never "fixed" by changing the expectation), `environment` | Prevents tests from being bent to match a defect |
| Snippet index: script greps `@Given/@When/@Then` into the skill folder, "if stale" | Regenerated every run into `target/ai/`; `@And` included; API, UI flows and Java glue; portable `find` instead of `globstar` | The old script stopped at the first snippet file without steps (`set -e` + `grep`), missed `@And` steps, and needed bash 4 |
| Input: ADO story ID or workflow-state JSON file | Same two modes, same `ado-agent` operations (`FETCH_STORY`, `LOAD`, `FIND_BDD_FILE`, `ADD_COMMENT`, `UPDATE_TAGS`, `SAVE`) and fields (`lastCompletedStage`, `bddFeatureFiles`, `tcTags`, `e2eStatus`, `e2eRepairAttempts`, `e2eFiles`); also pasted story text or a story file, with that state in `.ai-sdlc/<storyId>/` (git-ignored) | The ADO work item is the shared record that a story already has feature files; `ado-agent` lives in `ai-tooling`, so where it is not installed the commands say so and take the text or JSON input instead. `tcTags` holds case IDs here |
| debug-agent, `/codeuserstory` re-entry | Failures classified and routed by `/codetestcases` itself | Those agents are not in this repository |
| Mandatory live verification (`/v3/api-docs`, Playwright MCP) | Done on the company machine when reachable; otherwise the source used or an open item, never a guess | This copy runs nowhere; the company machine runs everything |

## Running it here and on the company machine

- **Here (public copy)**: both commands work up to authoring. Maven is not run; each command hands over the exact
  commands for the company machine.
- **Company machine** (`src/test/resources/qa.properties` exists, Maven works): everything runs, real runs only after
  the approval prompt.

## Porting back to `ai-tooling`

The names match the company's, so a piece can replace its counterpart. What a port must keep project-specific: the
paths, the case-ID and tag conventions, the JSON data and recording rules, and the runner facts. Those come from this
repository's `CLAUDE.md` and standards, which is why the skills point to them instead of repeating them.
