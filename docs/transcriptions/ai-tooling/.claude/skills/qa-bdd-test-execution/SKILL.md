<!-- Transcribed from IMG_6485–IMG_6489; overlapping passages merged. Product title anonymized; technical filenames preserved. No frontmatter was visible. Archival source text, not an active skill. Commands below are transcribed examples, not executed commands. -->

# Example E2E Test Runner

1. Resolve `{E2E_DIR}` from root `CLAUDE.md` → `# Repos` table. Default: `./e2e-test`.
2. Resolve run mode: `dry-run` or `real-run`.

The skill supports two distinct execution intents and **must return different output depending on the resolved mode**:

- `dry-run` = validation-only sanity gate. Maven/Cucumber validates discovery, glue registration, undefined steps, and compile/startup issues. It does **not** execute scenarios end-to-end.
- `real-run` = actual test execution. Scenarios run normally and the skill must report pass/fail at scenario level.

## Step 1 — Parse inputs and derive run targets

Read everything passed in the invocation and extract the following signals. Any combination is valid — derive what you can, apply sensible defaults for the rest.

**Signals to look for:**

| Signal | How to recognise it | Effect |
|---|---|---|
| `featureTag` string | e.g. `reason_management`, `trade_lifecycle` | Run `--tags @{featureTag}` |
| `@TC-*` tag(s) | e.g. `@TC-REASON-MANAGEMENT-API-010` | Run `--tags @TC-...` |
| `file_status: reused` on an entry that also has `tc_ids` | explicit field from authoring output | Prefer `tc_ids` over `featureTag` for that entry |
| `file_status: created` | explicit field from authoring output | Use `featureTag` for that entry |
| `@api` or `@playwright` | tag keyword | Type-level filter (add to tag expression) |
| `sit` | keyword | Target SIT environment |
| `post-generation validation`, `validation mode`, `sanity gate` | orchestration wording | Force `dry-run` |
| `Standard execution`, `standard execution`, `run tests`, `final run`, `actual run` | orchestration wording | Force `real-run` |
| explicit `mode: dry-run` / `mode: real-run` | structured caller field | Highest-priority mode signal |
| *(nothing / empty)* | no tags or filters given | Run the full suite against DEV |

**Priority when both `featureTag` and `tc_ids` are present for the same entry:**

- `file_status: reused` → use `tc_ids` (only the new cases)
- `file_status: created` → use `featureTag` (entire new file)
- No `file_status` provided → use whichever is more specific: `tc_ids` if present, else `featureTag`

**When multiple featureTags or multiple tc_ids are present: combine into a single invocation using OR logic.** Do not run separate sequential commands.

### Run mode resolution priority

Resolve mode in this order:

1. explicit structured field such as `mode: dry-run` / `mode: real-run`
2. orchestration intent phrases:
   - `post-generation validation`, `validation mode`, `dry run`, `sanity gate` → `dry-run`
   - `Standard execution`, `standard execution`, `run tests`, `real run`, `actual run` → `real-run`
3. if still ambiguous:
   - requests focused on validation / binding / undefined steps / generated automation sanity → `dry-run`
   - otherwise default to `real-run`

State the resolved mode explicitly before announcing the final command.

---

## Step 2 — Build the command(s)

Base: `mvn clean test` (run from `{E2E_DIR}/`)

**Environment:**

| Env | Command suffix |
|---|---|
| DEV (default) | *(none)* |
| SIT | `-Dgenie.properties=./src/test/resources/sit.properties` |

**Tag filter:**

```shell
-Dcucumber.options="--tags @trade_cancellation"
```

For multiple TC-IDs or multiple featureTag, combine with OR:

```shell
-Dcucumber.options="--tags \"@trade_cancellation or @trade_allocation\""
```

Announce each final command to the user **before** running it, including the resolved mode.

if the resolved mode is `dry-run`, append `--dry-run` to the tag filter.

```shell
mvn clean test -Dcucumber.options="--tags @trade_cancellation --dry-run" -B
```

if the resolved mode is `real-run`, do not append `--dry-run`.

```shell
mvn clean test -Dcucumber.options="--tags @trade_cancellation" -Dgenie.forkMode="None"
```

## Step 3 — Execute

Run each command sequentially with a 10-minute timeout per invocation.

Capture the full terminal output for each Maven invocation. Treat that terminal output as a first-class evidence source for reporting, especially when Maven exits with `BUILD FAILURE` before or alongside detailed scenario artifacts.

---

## Step 4 — Report results

### Evidence sources to combine

Build the final report summary in this order:

1. terminal Maven output from the just-finished command → primary source for pass/fail and test-count summary
2. `target/results/failed-scenarios-*.log` → failure drill-down source, only needed when Maven output indicates failures/errors/build failure

If Maven output shows all cases passed, you do not need to inspect `target/results/`.

### What to extract from terminal Maven output

From the terminal output, look for lines such as:

```text
[ERROR] Tests run: 6, Failures: 0, Errors: 6, Skipped: 0
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:...:test ...
```

Use terminal output to extract, when present:

- `Tests run`
- `Failures`
- `Errors`
- `Skipped`
- overall Maven outcome: `BUILD SUCCESS` or `BUILD FAILURE`
- first actionable failure signal, such as:
  - `Failed to execute goal ...`
  - `There are test failures`
  - `Unknown option: --tags ...`
  - `Undefined step`
  - `Compilation failure`

Terminal Maven output is the default summary source. Do not depend on `OreoTest.txt` for the report.

### Where to find execution results

Only when Maven output indicates failure (`BUILD FAILURE`, `Failures > 0`, or `Errors > 0`), inspect the scenario failure directory:

```text
{E2E_DIR}/target/results/
```

If that directory contains a file matching:

```text
failed-scenarios-*.log
```

Use all the `failed-scenarios-*.log` files to extract:

- the concrete failed scenario names / case names
- their `@TC-*` tags when present in the log entry
- the first visible `error:` line or root signal for each failed scenario
- a short failure summary grouped as "which cases failed"

If `target/results/` does not exist, or no `failed-scenarios-*.log` file exists there for the run, treat that as **no scenario failures logged**.

If the Maven terminal output says the run failed but `target/results/` has no failure log, still report the run as failed and use the terminal error signal as the main failure clue.

### Output format

```text
[PASS|FAIL] [dry-run|real-run]  (Env: [dev|sit] Tags: [tag expression])

Command: mvn clean test ...

Summary:
- Tests run: X
- Failures: X
- Errors: X
- Skipped: X
- Maven outcome: BUILD SUCCESS | BUILD FAILURE
- Failed log: {E2E_DIR}/target/results/failed-scenarios-YYYY-MM-DD_HH-mm-ss.log
- Failed cases:
  - <scenario / TC summary>
  - <scenario / TC summary>
```

If no `failed-scenarios-*.log` file exists, use this shorter ending instead:

```text
Summary:
- all passed
```

---

<!-- Transcription note: The final "all passed" fallback is reproduced from IMG_6489. It conflicts with the preceding rule requiring a failed Maven run to remain failed even when no scenario failure log exists. The source inconsistency is retained, not resolved by the transcriber. -->
