# Test data decisions (JSON)

How an agent decides where a value goes in oreo-e2e's test data, with the project's own files as examples. The format
in full is `docs/test-data.md`; this page is the decision procedure. Used by `qa-api-test-authoring` and
`qa-e2e-test-authoring`. There is no YAML test data in this project: a `data/**/*.yml` file is read by nothing.

## 1. The files

| Need | File | Read by |
|---|---|---|
| What an API case sends, its masking rules, its base | `data/api/<event>.json` (`<event>` = the feature file name) | `ScenarioLifecycleHooks` before each case |
| What a UI case types or selects | `data/ui/<event>.json` | the same hook; flows read the values from ctx |
| A block several events of one channel share | `data/api/common.json`, `data/ui/common.json` (`shared`), applied only where `use` names it | the event file that names it |
| The payload that prepares a precondition (a trade, a mock) | `data/setup/<name>.json` (`extends` another setup) | the steps `... from test data '<name>'`, `TradeProvisioningSteps` |
| What an API case's response must be | `data/api/expected/<event>/<case ID>.json` | the response check; **written by a run, never by hand** |
| A product's dat file | `data/dat/<P>/<P>.dat`, variants in the same folder | upload and provisioning steps |

API and UI files never read each other, and setup data is kept apart from API case data on purpose: changing what an
API case sends must not change how a UI case prepares its trade.

## 2. Where a value goes

Ask in order; stop at the first yes.

1. **Does it prepare a precondition rather than the action under test?** → `data/setup/`. Reuse an existing file
   (`ls src/test/resources/data/setup`) or `extends` one; a new name is lower case with `-`.
2. **Is it the same for every case of the event?** → the event file's `defaults` (`request`). If other events of the
   channel share the same block, a `shared` block in `common.json` and `"use": ["<block>"]` in `defaults`.
3. **Does it differ by product, for every case of that product?** → `byProduct.<P>`.
4. **Does it differ for one case?** → a row in `cases` with that case's `id`.
5. **For one case and one product?** → the row's `byProduct.<P>`.

A case that runs on the defaults has **no row**. One case matches at most one row. The row's `id` is the feature's
case ID as written: an Outline's `TC-TRADE-CREATION-<product_type>-API-002` is one row for every product.

Example, `data/api/trade_early_termination.json`: the event body is in `defaults`; API-001 has a row only for its
masking rules; API-002 (malformed) a row that empties `data`; API-003 (duplicate) a row with a message rule.

## 3. Merge traps

Layers apply in order, later wins: `defaults` → `byProduct[P]` → row → row's `byProduct[P]`; within a layer its `use`
blocks first, then its own `request` and `dynamicFields`.

- **Objects merge key by key.** `{"basic": {"direction": "Sell"}}` changes only `direction`.
- **Arrays replace whole.** To change one entry of `data` (`[{"key": "amount", ...}, ...]`), give the whole list. To
  give some products an extra entry, put the list with it in a `shared` block and `use` it from those products'
  `byProduct` (`docs/test-data.md`, "Case data").
- **`null` removes a key**, inherited rules included: `"dynamicFields": null` drops every rule the case inherited.
- `_comment` is allowed at any depth; any other unknown key is a load error.

## 4. Values

- **Dates**: `${date:T}`, `${date:T+N}`, `${date:T-N}` (calendar days, `yyyy-MM-dd`), never a fixed date that will
  expire. Other expressions are a load error.
- **Values a step fills**: keep the existing placeholder (`"tradeIds": ["PLACEHOLDER"]`, replaced with the created
  trade) or a `${...}` the step resolves; never a real trade or task ID.
- **Never**: `basic.productId` (the step sets it from the product), a path in `datFile` (a file name of the product's
  folder only), credentials, addresses, real counterparties.
- **Product-only form fields (UI)**: the value in those products' `byProduct`, and a flow step ending in
  `if it is set`, which types nothing when the case has no value (`targetAmount` in `ui/trade_novation_remaining.json`).
- A field only some products have is data, never an `Examples` column.

## 5. Recorded responses and `dynamicFields` (API only)

Rules are written **after** a run shows what changes, never guessed: a rule whose path is not in the response fails
the recording. `mvn -Ptest-data process-test-classes "-Dexec.args=suggest <event>"` reads the recordings and proposes the rules and their layer; judge each
(a date the request fixes is data, the ID this scenario created is `var:`).

| The value | Rule |
|---|---|
| A generated ID, a timestamp, a date the system derives | `type` |
| A generated structure whose shape matters (a schedule, a payoff table) | `type-tree` |
| A message with a variable part | `regex:<pattern>`, keeping the business words: `regex:.*status 'Dead' does not permit 'EarlyTermination'.*` |
| The ID this scenario created | `var:createdTradeId` |
| Nothing else works | `ignore` (last resort) |

Paths: keys joined by `.`, `[n]` one element, `[*]` every element (`data.results[*].tradeId`), `*` every value of an
object (`data.fixings.*`, also for keys holding `.`, `[` or `]`). Where the rule goes
follows section 2: every case → `defaults`, one product → `byProduct`, one case → the row.

**A rejected case usually needs `"dynamicFields": null`** in its row when `defaults` has rules for success fields
(`data.trade.id` in `api/trade_creation.json`): the error response has no such path, and the recording would fail.
When `defaults` has no rules (`api/trade_early_termination.json`), the negative row needs none.

**`expectedBase`**: when the cases of an event return mostly the same response, `defaults` names the base
(`"expectedBase": "TC-TRADE-CREATION-<product_type>-API-001"`) and `mvn -Ptest-data process-test-classes "-Dexec.args=compact <event>"` turns the variants into
patches. When the responses share nothing, leave it out and say why in `_comment`. `mvn -Ptest-data process-test-classes "-Dexec.args=suggest <event>"`
measures every candidate base on the masked recordings.

**`sharedFragments`**: when the response holds a part another event returns too (the trade's `basic` and
`instrument`), name the block `trade-response-parts` of `api/common.json` in `use` and give each part's path in this
event's response: `"sharedFragments": { "basic": "data.trade.basic", "instrument": "data.trade.instrument" }` (or
`data.basic` without `trade`). The block holds the parts' files and their dynamic values (`{basic}.dealDate`), which
follow the path. The compaction keeps the part once per product in `expected/_shared/`, taken from the events of
`sharedSources` (`api/common.json`, `trade_creation`) before any other. A case whose response has no such part (a
rejected request) says `"sharedFragments": null`, and has none of the parts' rules. See `docs/test-data.md`, "Shared parts".

A case whose evidence is the database or audit, not the response, carries `@expected#none` and has no expected file.

## 6. Check what you wrote

On the company machine, before any run:

```shell
mvn -Ptest-data process-test-classes "-Dexec.args=case <case ID>"
mvn -Ptest-data process-test-classes
```

`case` prints the applied layers, the request, the rules, the base and the expected file of one case: read it for every
case you added a row for. `check` must show no problem other than `no expected file` for API cases not yet run.
Offline, give these commands instead.
