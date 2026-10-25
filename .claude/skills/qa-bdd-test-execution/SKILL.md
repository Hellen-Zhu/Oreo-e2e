---
name: qa-bdd-test-execution
description: Use when oreo-e2e cases have to be checked or run - offline data check, Cucumber dry run, a real run against the environment, re-recording API responses - and the result reported per case. Called by test-agent and /codetestcases; also "run TC-...", "dry-run the new scenarios", "why did the run select nothing".
---

# QA BDD test execution (oreo-e2e)

Builds the narrowest command for the cases given, runs it where the project runs, and reports per case. The facts it
relies on are in `CLAUDE.md` §1 and `docs/standards/bdd-feature-implementation-standards.md` §6; read `OreoTest.java`
before the first run of a session.

## Where it runs

The project builds and reaches its environment only on the company machine. Decide once per session:

```shell
test -f src/test/resources/qa.properties && mvn -v
```

Both succeed: `where: company`, run the commands. Otherwise `where: offline`: run nothing, return the commands and
say they are for the company machine.

## Modes

| Mode | Command | Proves | Books trades |
|---|---|---|---|
| `check` | `mvn -Ptest-data process-test-classes` | data files, case rows, expected files, setup names, product `Examples` (skill `maintaining-e2e-test-data`) | No |
| `dry-run` | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=<selection> --dry-run --strict"` | every step of the selection has a definition; compile and startup | No |
| `real-run` | `mvn -B test -Dtest=OreoTest "-Dcucumber.options=<selection>"` | the cases pass against `qa.properties`' environment | **Yes** |
| `record` | `real-run` with `OREO_EXPECTED=update` set for the command (PowerShell: `$env:OREO_EXPECTED = "update"`, then `Remove-Item Env:OREO_EXPECTED`) | rewrites the selected cases' recordings without comparing | **Yes** |

- `--strict` matters: without it Cucumber 4 reports an undefined step as skipped and the dry run passes.
- Resolve the mode from the caller's explicit `mode:` field; otherwise validation / sanity wording means `check` then
  `dry-run`, "run" means `real-run`. Never choose `record` unless the caller asks for it by name.
- `real-run` and `record` need the user's go-ahead in this conversation for this selection (the command, `/codetestcases`
  step 3, asks for it). Without it, stop after `dry-run` and show the command.

## Selection

Narrowest first: case IDs, then a feature, never the whole suite.

- Case IDs: `--name ^\[(TC-A|TC-B)\].*$`
- A product Outline case (`TC-X-<product_type>-API-001`): its expanded names, `--name ^\[TC-X-[A-Z_]+-API-001\].*$`,
  or one product, `TC-X-FX_TRF-API-001`
- A feature: `--tags @ADO#<featureName>`
- A suite within a feature: repeated `--tags` (all must match), `--tags @ADO#<featureName> --tags @ADO#smoke`

- Combine several cases into one `--name` alternation: one Maven invocation, not one per case.
- Quote each `-D` argument whole (PowerShell too). Never `-Dcucumber.filter.*`: unsupported here, possibly ignored,
  running everything.
- `OreoTest` fixes `not @wip` and `@ADO#trade_creation`, and this project combines a command-line filter with them. A
  selection outside trade creation can therefore come back empty. **`Tests run: 0` is not a pass**: report "nothing
  selected", show the runner's tags, and ask the user whether to widen the runner's tags locally for this run (never
  committed).
- Announce each command, with its mode, before running it. Run long commands in the background with a 10-minute
  budget, one at a time (the runner already forks 3 features in parallel).

## Reading the result

1. Maven's output: `Tests run / Failures / Errors / Skipped`, `BUILD SUCCESS | FAILURE`, and the first actionable
   line (`Undefined step`, `Compilation failure`, `Failed to execute goal`, an exception).
2. On failure only: `target/results/failed-scenarios-*.log` (written by `ScenarioLifecycleHooks`, newest file of the
   run): failed scenario names with their case IDs and the first `error` line of each.
3. A failed build with no log is still a failure; its cause is Maven's first actionable line.

Classify every failed case, so that the caller routes it:

| Class | Signals |
|---|---|
| `automation` | undefined step, element not found / ambiguous, data row or setup missing, `check` problem, compile error |
| `recording` | `no expected file`; a new case's first comparison; a value that changes between runs |
| `behavior` | status or business value differs from what the requirement says |
| `environment` | login failure, connection refused, 5xx across cases, timeouts in unrelated cases |

## Report

```text
[PASS|FAIL|EMPTY] <mode> (where: company|offline)
Command : <exact command>
Summary : run X, failures X, errors X, skipped X, Maven BUILD SUCCESS|FAILURE
Log     : target/results/failed-scenarios-<timestamp>.log | none
Cases   :
  - TC-...  passed | failed: <class> - <first error line>
Next    : <what the caller should do: fix automation / record / ask about behavior / environment>
```

For `where: offline`, the report is the commands in order with what each should show; nothing is claimed as run.
