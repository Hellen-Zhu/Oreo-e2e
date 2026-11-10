# Parallel execution in Genie 4

How Genie runs scenarios in parallel, summarized from the company's Genie 4 reference pages ("Parallelism model in
Genie 4", "Executing Tests in Parallel"), and what it means for `OreoTest`. When this page and the runner disagree,
trust `src/test/java/com/example/module/oreo/OreoTest.java`.

## 1. The model: processes, not threads

Cucumber-JVM is not thread-safe by design: a runtime holds one scenario at a time, and a new scenario gets new glue
instances. Genie therefore parallelizes by **forking processes**, as the cucumber-parallel Maven plugin does.

```
master Genie (forking runtime)                         child Genie (forked runtime), one per unit of work
  load every feature ─┐                                  receives the parent run's details and its unit
  apply the fork mode ┴─> queue of units of work  ───>   of work as command-line arguments, locates those
  work-stealing pool: at most forkCount children         scenarios, runs them serially as in normal mode,
  a finished child is replaced by the next unit          then exits
```

1. The master loads the features, applies the tag filter and splits the scenarios into **units of work** by the fork
   mode, queued in serial order.
2. It starts a child JVM per unit, up to `forkCount` at once. When any child finishes, the next unit starts at once
   (it does not wait for the batch). Units start in queue order but may finish in any order.
3. When the queue is empty and every child has finished, the run ends and the report opens.

A unit of work is a subset of the run's scenarios that one child executes serially.

There are three runtimes: **normal** (no forking, as Genie 3), **forking** (the master) and **forked** (a child).
Modules declare where they load: `@MasterOnly` (for example the uploader, run-log and update modules) or
`@MasterAndChild`, optionally limited to some fork modes. Enrichment and JDBC provider modules are loaded in the master
when the fork mode needs unrolled scenarios. Each instance has its own run identifier and its own report.

## 2. Options

| `@GenieOptions` | Command line | Default | Values |
|---|---|---|---|
| `forkMode` | `--forkMode` | `NONE` | `NONE`, `FEATURE`, `SCENARIO_DEFINITION`, `UNROLLED_SCENARIOS` |
| `forkCount` | `--forkCount` | `4` | maximum concurrent children |
| `forkWorkingDirPolicy` | `--forkWorkingDirPolicy` | `SHARED` | `SHARED`, `DISCRETE` |

Fork modes, from coarsest to finest unit of work:

| Mode | Unit of work | Suits |
|---|---|---|
| `FEATURE` | one feature file | many features of similar size; the general choice |
| `SCENARIO_DEFINITION` | one Scenario or Scenario Outline, not unrolled | many scenarios, light modules, cheap startup |
| `UNROLLED_SCENARIOS` | each scenario and each Examples row, after enrichment | few, long scenarios with many enriched rows; the startup overhead is very noticeable |

Working directory policies:

- `SHARED`: every child runs in the project root. File writes of concurrent children may collide.
- `DISCRETE`: every child runs in its own empty temporary directory, deleted at the end. The project root is
  prepended to the child's classpath, so features and data must be read through **classpath** references, not
  relative file paths.

On Java 9 and later (Genie 4.7.1+), the JVM needs `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED`: in the
surefire `argLine` for Maven, and in the VM options of an IDE run configuration.

## 3. When parallel is faster

With `G` the Genie startup time (modules, enrichment), `n` the number of units of work, `U` the average time of a unit
and `f` the fork count (`n >= f`):

| Serial | Parallel |
|---|---|
| `Ts = G + n·U` | `Tp = G + (n / f)·(G + U)` |

Parallel is faster only when **`U > G / (f − 1)`**: every unit pays the startup again. The page's example: `G = 60 s`,
`f = 4` gives a break-even `U` of 20 s; units shorter than that run slower in parallel. A higher `f` lowers the
break-even on paper, but the speed-up is not linear: the children share one host's CPU and memory, and the page notes
that 4 instances can bring a firm-issued laptop to a halt, with IntelliJ too unresponsive to stop the run.

## 4. Known issues

- **Host load**: every child is a full JVM on the same machine (plus, for UI cases, its own browser). Too high a fork
  count makes laptops unresponsive and build agents run out of memory or CPU, which makes tests unstable.
- **Shared browser grids** fill up when many projects fork at once.
- **IntelliJ's JUnit tree** can miss statuses of parallel executions (Genie serializes them through a layer of its
  own); all tests still run.
- **Child logs under `mvn test`**: on Java 9+, the stdout of a process started by `ProcessBuilder` is captured at DEBUG
  level, so child logging only shows with `mvn test -X`.
- **Line-number selection** (`file.feature:42`) is unreliable with enrichment: enriched rows do not exist when Cucumber
  reads the file, so their order follows the external data, not the file.
- Before parallelizing, remove explicit waits (`Thread.sleep`) and make scenarios independent and self-contained:
  parallel order is non-deterministic, and state cannot be shared across units of work.

## 5. What this means for OreoTest

`OreoTest` runs `forkMode = FEATURE`, `forkCount = 3`, `forkWorkingDirPolicy = DISCRETE`.

- **Unit of work = one feature file.** A feature holds many scenarios, often an Outline with one row per composition,
  so `U` is minutes against a startup of seconds: well above `G / 2`. `SCENARIO_DEFINITION` is the commented-out
  alternative and would also pay off for Outlines; `UNROLLED_SCENARIOS` would start one JVM per product row and is not
  worth it here.
- **Independence is mandatory** (CLAUDE.md §1): three features run at once in separate JVMs, so no scenario may rely on
  another one or on shared mutable state. This is why every case provisions its own trade.
- **DISCRETE means no relative paths.** Code that writes into the project resolves it through
  `ProjectPaths.projectRoot()` (recordings under `data/api/expected`), and hooks read resources through the classpath
  (`ScenarioLifecycleHooks`). New code must do the same.
- **What reaches the children**: the parent passes its run details to each child as arguments. A switch meant for the
  children is safest as an environment variable: `OREO_EXPECTED=update` reaches them (`docs/test-data.md`).
- **Selecting cases**: by `--name` with the case ID or by tags (`-Dcucumber.options`), never by line number.
- **Memory**: surefire's `argLine` gives `-Xmx2048m`. With three children each running a JVM and, for `@playwright`
  features, a browser, plus the IDE, a 32 GB laptop with a low-power CPU runs out of CPU and native memory. Lower
  `forkCount` to 2 on a laptop, or run the full suite on a build agent.
- **Logs and reports**: run `mvn test -X` to see the children's logs. Each child writes its own results, which a
  merged report has to combine (`docs/test-dashboard-plan.md`).

## Open questions

- Do the children inherit surefire's `argLine` (`-Xmx`, `--add-opens`), or does Genie start them with its own JVM
  options? The pages do not say; it decides the real memory ceiling of a run.
- How long does our Genie startup (`G`) take? It can be read from the time between a child's start and its first
  scenario in a `-X` log.
