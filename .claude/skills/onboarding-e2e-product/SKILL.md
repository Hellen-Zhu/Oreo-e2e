---
name: onboarding-e2e-product
description: Use when a new OREO product type (a composition) has to be covered by the Java + Cucumber (Genie) cases of oreo-e2e - "add product X to the E2E project", "we are launching X", "here is the dat file of X", "which E2E cases does X need", "what do I need to give you for X" - or when a product turns out not to support a scenario there.
---

# Onboarding a product (oreo-e2e)

## Overview

A product is a row, not code. Steps, snippets, the test data hook and the response check already take the product as a parameter, and `expectedBase` (`<product_type>`) already covers every product. A product is a composition, named as the trade's `basic.compositionId` (`FX_TRF`, `FX_CO`, `FX_FSB`, `FX_PSCRIPT_TRF`). Onboarding is: one `ProductDatFiles` constant registering the default product ID the cases send and the capabilities it supports, its dat file, `byProduct` entries only for values that differ, a tool run that writes the product into the `Examples` of the Outlines tagged with those capabilities, and API responses recorded on the real system.

**One input, then a review.** QA gives the composition and its dat file; everything else starts from defaults (every capability, the current data) and is shown to QA as one review of the change. QA removes what the product does not support and gives the values that differ; nothing is recorded, run on the real system or committed before QA approves the review.

**Nothing is invented** beyond those stated defaults: no data value, dat content, masking rule or command-line flag. A default QA has not confirmed is marked as such in the review.

Paths below are relative to the oreo-e2e root.

## 1. Ask for the composition and its dat file

One question, in the user's language, for three things:

- `<C>`, the composition ID: `basic.compositionId` of a trade of it, the Product ID column of the product catalogue
  (`FX_CO`, not the product ID `FX_PSCRIPT`). A composition already in `ProductDatFiles` is already covered; say so
  and stop. Another product ID of it is not a new product: add a case that names it with `productId` in its data
  (`docs/test-data.md`).
- The product ID the cases send by default: `basic.productId`, the New Trade Product ID field (`FX_PSCRIPT`). A
  composition with several product IDs needs one default; QA picks it. The others are case data (`productId`).
- Its default dat file, a template of that product ID, named `<C>.dat` here whatever its name was.
- It is a real trade template the business uses. A file of a few bytes or test text is not one: ask again.
- In the public copy (its `.dat` files are one-line placeholders) the change does not use the file: make it with the
  placeholder (section 2) and keep the real file as an open item; the company copy needs it before any run.

Nothing else is asked first. With the dat file, make the change (section 2), then send the review (section 3).

## 2. Change

| File | Change |
|---|---|
| `src/main/java/com/example/module/oreo/testdata/ProductDatFiles.java` | One constant `<C>("<default product ID>", SUPPORT_CAPABILITY, PRODUCT_FEATURES)`, named exactly as the composition ID: both groups, every capability (the trade lifecycle: create, step in full / partial, every lifecycle event, fixings; the product features: cortex formula, product upload) until QA's review takes some off, per group: `except(<group>, <the ones it lacks>)`, `only(<the ones it has>)` (`<C>("...", SUPPORT_CAPABILITY, only(PRODUCT_UPLOAD))`), or the group left out when it has none of it. Added after the last product, never first (`values()[0]` is the data checks' example product; the order of the constants is the order of the `Examples` rows). Its comment: the product's name as QA gives it, else none |
| `src/test/resources/data/dat/<C>/<C>.dat` (the default; variants QA names go into the same folder) | If the committed `.dat` files are one-line placeholders (`# Placeholder: copy <C>.dat from the E2E project.`), this is the public copy: write the same line for `<C>`, never the real file, and add `<C>` to the placeholder list in `README.md` ("This copy"). Otherwise copy QA's file byte for byte |
| `byProduct.<C>` of an event file | Nothing at first: the product runs on the defaults. Only values QA gives in the review that differ from them. `dynamicFields` only after a recording shows them (section 5) |

Then the `Examples`, never by hand: `mvn -Ptest-data process-test-classes "-Dexec.args=sync-examples"` (section 4) writes the row `| <C> |` into every Outline tagged with one of the product's capabilities, in registry order, and prints one line per Outline (`add <C>`). It writes nothing when a feature file has a problem. After QA's review, change the constant and run it again: it removes the rows of the capabilities taken off.

Leave alone:
- steps, snippets, hooks, `expected/` files, `expectedBase`, `Capability.java` and the `@capability#` tags;
- scenarios without `<product_type>` (negative and permission cases are written for one product);

Coverage QA asks for that no Outline has (a capability no Outline carries, an event without a feature in that channel, a negative case for the product) is a new scenario, not onboarding: list it in the review.

## 3. Review with QA

One message, in the user's language, after section 2 and `sync-examples`; nothing is recorded or committed until QA
answers. It holds:

1. **Capabilities** (default: all). A table: capability, the Outlines it now runs for `<C>` (case ID with `<C>`,
   title), from `git diff` of the feature files or `grep -rn -A1 "@capability#" src/test/resources/features`; the
   product's column of `mvn -Ptest-data process-test-classes "-Dexec.args=coverage"` shows how many API and UI
   Outlines each capability runs and which have none (`NONE`, listed as gaps; `"-Dexec.args=coverage --html"` writes
   the same as a page, `target/coverage/coverage.html`, a product's cases on a click; `--by productId`,
   `--by tradeType` or `--by tradeTypeProductId` count the same by product ID, trade type, or trade type and
   product ID, when QA asks for those views); with a
   column QA fills in: **supported / not supported, row by row**, in two parts as `Capability.GROUPS` has them: the
   trade lifecycle, then the product features (cortex formula, product upload), asked on their own: products
   differ there most, so "the same as the others" for the lifecycle is no answer for them. A general "looks fine" is not an answer: ask again
   for the rows still empty. A capability without its row answered stays a default: it is not recorded, run or
   committed. A capability covers all its Outlines, API and UI: a no for only some of them is a question for the user,
   not a registry line. The answer comes from the requirement or the BA.
2. **Data the product runs on** (default: the current values). Per event the product joins: the `defaults` of
   `src/test/resources/data/api|ui/<event>.json` and the `shared` blocks they `use` (the event file's own `shared`, or
   `api/common.json`, `ui/common.json`); `mvn -Ptest-data process-test-classes "-Dexec.args=case TC-<EVENT>-<C>-API-001"` prints the merged request. QA gives
   only the values that differ; a wrong default fails the case (the status check runs before any recording). An
   amount given as a share of the notional (`"50%"`) fits every product and needs no value.
3. **Anything the current products do not have**: in the New Trade form, the trade detail page or the setup trade
   (`data/setup/trade-create*.json`, one body for every product). Yes is a code change of its own: report it, do not
   guess it.
4. **The diff**: files created and changed.

Apply QA's answers: capabilities off the constant and `sync-examples` again, the values that differ under
`byProduct.<C>`. Record and commit only when every capability row has its answer. When QA cannot be reached, stop here: the change stays uncommitted, the review is the report.

## 4. Offline check

The commands are `TestDataTool`'s (`docs/test-data.md`, "Commands"); run them only on the company machine, elsewhere
give them.

`mvn -Ptest-data process-test-classes`. Expected before `sync-examples`: "the product Outlines' Examples match the registry" fails with one `add <C>` line per Outline of the product's capabilities. After it, until the recordings: only "every API case has its expected file" fails, one `no expected file` line per new API case. Any other problem (a product that is not in `ProductDatFiles`, a `byProduct` without a dat file, a `remove` line before QA took a capability off) is a mistake in section 2. A feature-size warning is not a problem: report each feature it names with a proposed split (the user decides).

`mvn -Ptest-data process-test-classes "-Dexec.args=case TC-TRADE-CREATION-<C>-API-001"` prints the merged request the case will send.

## 5. Record the API responses (company machine, real system)

Every run books real trades: select only the product's cases (selection syntax: skill `qa-bdd-test-execution`), e.g.
`--name ^\[TC-[A-Z-]+-<C>-API-\d+\].*$`, and `-UI-` for its UI cases. The recording procedure and the rule types are
in `docs/test-data.md`, "Recorded responses"; the product's rules go under `byProduct.<C>.dynamicFields`.

1. Run the product's API cases: each records `data/api/expected/<event>/<caseId>.json` and fails once.
2. Run `mvn -Ptest-data process-test-classes "-Dexec.args=suggest <event>"` for the values that look generated, then run the cases again. A failure on a value that changed between the two runs (an ID, a date, a version, a generated schedule) is a dynamic field. Add its rule under `byProduct.<C>.dynamicFields`. Record again with `OREO_EXPECTED=update`, then run once more: it must pass.
3. Review each recorded file against the requirement: a wrong value recorded is a wrong expectation.
4. `compact <event>` (`compact all` when a rule was added after the product's shared files were written:
   `docs/test-data.md`, "Shared parts"), review `git diff`, then `check`: no problem. Read `basic.compositionId` in
   `_shared/basic/<C>.json`: it must be `<C>`; another one means the constant's product ID or dat file creates another
   composition (nothing checks this automatically).
5. Run the product's UI cases.

## 6. Report

In the user's language:
1. **Coverage**: one line per capability: yes (its Outlines, as case IDs with `<C>`) / no, and whether QA confirmed it
   in the review or it is still the default.
2. **Files** created and changed.
3. **Open**: the real dat file (public copy), review answers still missing, values still on the defaults, recordings
   pending, anything inferred.
4. **Commands** for the company machine: `sync-examples` (section 2), then sections 4 and 5, in order.

## Common mistakes

| Mistake | Instead |
|---|---|
| Every capability recorded or committed "like the other products", or on a general "looks fine" | All capabilities are the starting point of the review; record and commit only after QA answered every capability row |
| A new `Capability` constant or `@capability#` tag for the product | A new scenario: report it |
| QA's real dat file committed to the public copy | The placeholder line |
| The product added to negative scenarios | They are written for one product |
| A constant named after the product ID (`FX_PSCRIPT`), or a second one for another product ID of a registered composition | One constant per composition ID with its default product ID; another product ID is `productId` in a case's data |
| An amount per product in `byProduct` because the dat files' notionals differ | A share of the notional in the event's `defaults` (`docs/test-data.md`, "Where a value goes") |
