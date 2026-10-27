---
name: onboarding-e2e-product
description: Use when a new OREO product type has to be covered by the Java + Cucumber (Genie) cases of oreo-e2e - "add product X to the E2E project", "we are launching X", "here is the dat file of X", "which E2E cases does X need", "what do I need to give you for X" - or when a product turns out not to support a scenario there.
---

# Onboarding a product (oreo-e2e)

## Overview

A product is a row, not code. Steps, snippets, the test data hook and the response check already take the product as a parameter, and `expectedBase` (`<product_type>`) already covers every product. Onboarding is: one `ProductDatFiles` constant listing the capabilities the product supports, its dat file, `byProduct` entries only for values that differ, a tool run that writes the product into the `Examples` of the Outlines tagged with those capabilities, and API responses recorded on the real system.

**One input, then a review.** QA gives the dat file; everything else starts from defaults (every capability, the current data) and is shown to QA as one review of the change. QA removes what the product does not support and gives the values that differ; nothing is recorded, run on the real system or committed before QA approves the review.

**Nothing is invented** beyond those stated defaults: no data value, dat content, masking rule or command-line flag. A default QA has not confirmed is marked as such in the review.

Paths below are relative to the oreo-e2e root.

## 1. Ask for the dat file

One question, in the user's language: the product's default dat file, named after the product ID, `<P>.dat`.

- `<P>` is the product type exactly as the system knows it: sent as `basic.productId`, typed into the New Trade Product
  ID field. New names only (`FX_PSCRIPT`, never `FX_CO`). A file not named that way: ask for the product ID, then name
  the file `<P>.dat`.
- It is a real trade template the business uses. A file of a few bytes or test text is not one: ask again.
- In the public copy (its `.dat` files are one-line placeholders) the change does not use the file: make it with the
  placeholder (section 2) and keep the real file as an open item; the company copy needs it before any run.

Nothing else is asked first. With the dat file, make the change (section 2), then send the review (section 3).

## 2. Change

| File | Change |
|---|---|
| `src/main/java/com/example/module/oreo/testdata/ProductDatFiles.java` | One constant `<P>(SUPPORT_CAPABILITY)`, named exactly as the product ID: every capability (create, step in full / partial, every lifecycle event, fixings, cortex formula, product upload) until QA's review takes some off: `except(SUPPORT_CAPABILITY, <the ones it lacks>)`, or the remaining ones one by one (`<P>(CREATE, EARLY_TERMINATION)`). Added after the last product, never first (`values()[0]` is the data checks' example product; the order of the constants is the order of the `Examples` rows). Its comment: the product's name as QA gives it, else none |
| `src/test/resources/data/dat/<P>/<P>.dat` (the default; variants QA names go into the same folder) | If the committed `.dat` files are one-line placeholders (`# Placeholder: copy <P>.dat from the E2E project.`), this is the public copy: write the same line for `<P>`, never the real file, and add `<P>` to the placeholder list in `README.md` ("This copy"). Otherwise copy QA's file byte for byte |
| `byProduct.<P>` of an event file | Nothing at first: the product runs on the defaults. Only values QA gives in the review that differ from them. `dynamicFields` only after a recording shows them (section 5) |

Then the `Examples`, never by hand: `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"` (section 4) writes the row `| <P> |` into every Outline tagged with one of the product's capabilities, in registry order, and prints one line per Outline (`add <P>`). It writes nothing when a feature file has a problem. After QA's review, change the constant and run it again: it removes the rows of the capabilities taken off.

Leave alone:
- steps, snippets, hooks, `expected/` files, `expectedBase`, `Capability.java` and the `@capability#` tags;
- scenarios without `<product_type>` (negative and permission cases are written for one product);

Coverage QA asks for that no Outline has (a capability no Outline carries, an event without a feature in that channel, a negative case for the product) is a new scenario, not onboarding: list it in the review.

## 3. Review with QA

One message, in the user's language, after section 2 and `sync-examples`; nothing is recorded or committed until QA
answers. It holds:

1. **Capabilities** (default: all). A table: capability, the Outlines it now runs for `<P>` (case ID with `<P>`,
   title), from `git diff` of the feature files or `grep -rn -A1 "@capability#" src/test/resources/features`, with a
   column QA fills in: **supported / not supported, row by row**. A general "looks fine" is not an answer: ask again
   for the rows still empty. A capability without its row answered stays a default: it is not recorded, run or
   committed. A capability covers all its Outlines, API and UI: a no for only some of them is a question for the user,
   not a registry line. The answer comes from the requirement or the BA.
2. **Data the product runs on** (default: the current values). Per event the product joins: the `defaults` of
   `src/test/resources/data/api|ui/<event>.json` and the `shared` blocks they `use` (the event file's own `shared`, or
   `api/common.json`, `ui/common.json`); `mvn -Ptest-data process-test-classes "-Dexec.args=case TC-<EVENT>-<P>-API-001"` prints the merged request. QA gives
   only the values that differ (e.g. `earlyTermination.amount` must fit the dat file's notional); a wrong default fails
   the case (the status check runs before any recording).
3. **Anything the current products do not have**: in the New Trade form, the trade detail page or the setup trade
   (`data/setup/trade-create*.json`, one body for every product). Yes is a code change of its own: report it, do not
   guess it.
4. **The diff**: files created and changed.

Apply QA's answers: capabilities off the constant and `sync-examples` again, the values that differ under
`byProduct.<P>`. Record and commit only when every capability row has its answer. When QA cannot be reached, stop here: the change stays uncommitted, the review is the report.

## 4. Offline check

The tool is `src/test/java/com/example/module/oreo/tools/TestDataTool.java`, documented in `.claude/skills/maintaining-e2e-test-data/SKILL.md`. Every command below is that Maven command, written in full: `mvn -Ptest-data process-test-classes "-Dexec.args=<command>"`; without `exec.args` it runs `check`. The class `TestDataTool` is run only through it. Run them only where the user says oreo-e2e builds (the company machine); elsewhere give them the commands.

`mvn -Ptest-data process-test-classes`. Expected before `sync-examples`: "the product Outlines' Examples match the registry" fails with one `add <P>` line per Outline of the product's capabilities. After it, until the recordings: only "every API case has its expected file" fails, one `no expected file` line per new API case. Any other problem (a product that is not in `ProductDatFiles`, a `byProduct` without a dat file, a `remove` line before QA took a capability off) is a mistake in section 2.

`mvn -Ptest-data process-test-classes "-Dexec.args=case TC-TRADE-CREATION-<P>-API-001"` prints the merged request the case will send.

## 5. Record the API responses (company machine, real system)

Every run books real UAT trades: run only the product's cases. Select them with `-Dcucumber.options` (`docs/standards/bdd-feature-implementation-standards.md` §6.2), e.g. `mvn -B test -Dtest=OreoTest "-Dcucumber.options=--name ^\[TC-[A-Z-]+-<P>-API-\d+\].*$"` for every API case of the product, and `-UI-` in the same pattern for its UI cases. Never `-Dcucumber.filter.tags` / `-Dcucumber.filter.name`: unsupported here, they may be ignored silently and run everything. The risk engine mode is `oreo.risk.engine.mode` in `genie.properties`.

1. Run the product's API cases: each records `data/api/expected/<event>/<caseId>.json` and fails once.
2. Run `mvn -Ptest-data process-test-classes "-Dexec.args=suggest <event>"` for the values that look generated, then run the cases again. A failure on a value that changed between the two runs (an ID, a date, a version, a generated schedule) is a dynamic field. Add a rule under `byProduct.<P>.dynamicFields` of the event file, shaped like the other products' entries (`type`, `type-tree`, `regex:<pattern>`, `var:<name>`, `ignore`: `docs/test-data.md`, "Recorded responses"). Record again with `OREO_EXPECTED=update`, then run once more: it must pass.
3. Review each recorded file against the requirement: a wrong value recorded is a wrong expectation.
4. Compact with `mvn -Ptest-data process-test-classes "-Dexec.args=compact <event>"`, then review `git diff`: the product's variants become patches on its `API-001`, and where the event declares shared parts (`sharedFragments`, e.g. the block `trade-response-parts`) the product gets its own `expected/_shared/basic/<P>.json` and `instrument/<P>.json`, taken from its first recorded case, with references in its files. Nothing to write by hand. If a rule was added after the shared files were written, run `mvn -Ptest-data process-test-classes "-Dexec.args=compact all"` instead: `compact <event>` keeps a shared file as it is, so a value masked later would stay as a key that differs in every case. Run `mvn -Ptest-data process-test-classes` again: no problem.
5. Run the product's UI cases.

## 6. Report

In the user's language:
1. **Coverage**: one line per capability: yes (its Outlines, as case IDs with `<P>`) / no, and whether QA confirmed it
   in the review or it is still the default.
2. **Files** created and changed.
3. **Open**: the real dat file (public copy), review answers still missing, values still on the defaults, recordings
   pending, anything inferred.
4. **Commands** for the company machine: `sync-examples` (section 2), then sections 4 and 5, in order.

## Common mistakes

| Mistake | Instead |
|---|---|
| Every capability recorded or committed "like the other products", or on a general "looks fine" | All capabilities are the starting point of the review; record and commit only after QA answered every capability row |
| `Examples` rows typed by hand | `sync-examples` writes them; a row the registry does not back fails `check` |
| A new `Capability` constant or `@capability#` tag for the product | A new scenario: report it |
| A row commented out to skip the product for a while | Take the capability off its constant, then `sync-examples`; a comment inside a product table stops it |
| QA's real dat file committed to the public copy | The placeholder line |
| `-Dcucumber.filter.tags=...` / `-Dcucumber.filter.name=...` (unsupported), `-Doreo.risk.engine.mode=...` | `-Dcucumber.options=--tags ...` / `--name ...`; the mode is in `genie.properties` |
| A run without a filter | It runs every feature and books UAT trades for all of them: always filter to the product's cases |
| `dynamicFields` guessed before a recording | A rule whose path is not in the response fails the recording |
| Expected files written by hand | Record them |
| The product added to negative scenarios | They are written for one product |

## Never

Commit a server address, IP, account, password, company name or real trade / task ID; write anything but English in the repo. Commit and push as set for this project, after scanning the diff for those; never force-push or merge into main without asking.
