---
name: maintaining-e2e-test-data
description: Use when the test data, feature files, recorded API responses or product registry of oreo-e2e change, before committing them - "check the test data", "what does case X send", "compact the expected files", "write the Examples", "a product or capability was added" - or when a data mistake got past mvn test.
---

# Maintaining the test data (oreo-e2e)

## Overview

The offline tools of the test data are one Java single-file program, `scripts/TestDataTool.java`, run by hand. A plain `mvn test` (the pipeline) runs only the Cucumber suite `OreoTest`: it does not check the data. Run `check` before every commit that touches `src/test/resources/data/`, `src/test/resources/features/`, `ProductDatFiles`, `Capability`, or a step or snippet that names setup data.

Paths are relative to the oreo-e2e root. Run Maven and the tool only where the user says oreo-e2e builds (the company machine); elsewhere hand over the commands.

## Run

Both lines, every time. The first compiles the project, copies the data to `target/test-classes` (where the tool reads the case data) and writes the paths of the Jackson jars to `target/tool.classpath`. PowerShell, from oreo-e2e:

```powershell
mvn -q test-compile dependency:build-classpath "-DincludeGroupIds=com.fasterxml.jackson.core" "-Dmdep.outputFile=target/tool.classpath"
java -cp "target/classes;target/test-classes;$(Get-Content target/tool.classpath)" .claude/skills/maintaining-e2e-test-data/scripts/TestDataTool.java check
```

macOS / Linux: `:` instead of `;`, `$(cat target/tool.classpath)`. Below, `TestDataTool <command>` means the second line with that command.

| Command | What it does |
|---|---|
| `check` | All offline checks (next section). Exit code 1 on a problem |
| `sync-examples` | Writes the `Examples` of every product Outline from `ProductDatFiles`: one line per Outline (`unchanged`, `add X`, `remove Y`, `reordered`, `reformatted`); nothing written when a feature file has a problem |
| `compact <event>` or `compact all` | Turns the recorded responses of the variants into patches on their base; nothing written on a problem |
| `case <case ID>` | Prints the merged data of one case: layers, dat file, request, `dynamicFields`, `expectedBase`, expected file and response |

The imports of `TestDataTool.java` name the package of `ProductDatFiles` (`com.example.module.oreo.testdata` here): in the company copy, the project's own.

## What `check` checks

One `[ok]` or `[FAIL]` line per check, its problems and `[warn]` lines below it:

1. every data file loads (event files, `common.json`, setup files); every `byProduct` product has its dat file;
2. every case of every feature resolves; its event file and channel match the feature;
3. every row and event file has its feature;
4. every API case has its expected file, every expected file its case;
5. the `dynamicFields` rules agree with the expected files (warning: a placeholder without a rule);
6. the expected files resolve and are compacted (warning: a file `compact` would still change);
7. every setup name the code, snippets or features use has its `data/setup/` file, except `trade-cancellation` and `trade-amendment` (not covered yet: the user's decision, `KNOWN_MISSING`);
8. the product Outlines' `Examples` match the registry;
9. every product with a capability has its default dat file.

## When

| Change | Run |
|---|---|
| Event or setup data, a row, `byProduct`, `use`, `datFile` | `check`; `case <ID>` to see what a case sends |
| A product or capability in `ProductDatFiles`, a `@capability#` tag | `sync-examples`, review `git diff`, then `check`. A new product: the skill `onboarding-e2e-product` |
| API responses recorded (first run, or `OREO_EXPECTED=update`) | `compact <event>`, review `git diff`, then `check` |
| Feature files | `check` |

## Common mistakes

| Mistake | Instead |
|---|---|
| Only the `java` line after a change | Both lines. The tool stops with `target/test-classes/data is not the same as src/test/resources/data` |
| That message after deleting a data file | `mvn clean` once, then both lines |
| `NoClassDefFoundError: com/fasterxml/jackson/...` | Jackson comes from another jar here: drop the `-DincludeGroupIds` argument (the whole test classpath) |
| A `-D` argument unquoted in PowerShell | Quote each one whole |
| Run from another folder | From oreo-e2e: the tool finds the project through `target/test-classes/data` |
| `Examples` rows typed by hand | `sync-examples` |
| Expected files written or patched by hand | Record them, then `compact` |
| A check loosened, or a name added to `KNOWN_MISSING`, to get `[ok]` | Fix the data; `KNOWN_MISSING` is the user's decision |
