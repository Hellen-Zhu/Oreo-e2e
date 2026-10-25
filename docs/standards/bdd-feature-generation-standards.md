# BDD Feature Generation Standard

How approved test points become executable Gherkin: which scenarios to write, where they go, what they are called and
how they read. Upstream, [test layering](test-layering-standards.md) decides the test points and their layer;
downstream, [implementation](bdd-feature-implementation-standards.md) makes the steps run.

**Output**: one `.feature` file per layer that has approved test points, plus a short *Generation Context* (below).

## 1. Inputs and Authority

| Question | Decided by | Never from |
|---|---|---|
| Which behaviors get a scenario, on which layer, with which evidence | The confirmed Phase 1 test points (TP) | Re-reading the story; "it would be nice to also test" |
| API paths, UI wording, extra assertions, test data intent | Approved `solutionDesign` (`technicalNotes` only when it is missing) | Guessing; `technicalNotes` that contradict `solutionDesign` |
| What a response must contain | The requirement | A recorded response (a recording is evidence of what the system *did*, not of what it *should* do) |

- No approved TP, no scenario. A gap (unclear rule, missing domain, unsupported product) is reported, not filled in.
- A conflict between `technicalNotes` and `solutionDesign` is resolved in favour of `solutionDesign` and named in the
  Generation Context.

## 2. Test Design Rules

These decide *how many* scenarios there are and *what each one proves*.

1. **One scenario, one behavior.** A scenario proves one business rule: action → outcome → resulting state. Never mix
   positive and negative behavior in one Scenario or Outline.
2. **Group test points that share a flow.** Two or more TPs with the same `Given`, the same `When` and the same polarity
   become one scenario with several `Then`, unless they need different data, the result would exceed 8 steps, or their
   evidence types cannot be observed together. Every TP lands in exactly one scenario.
3. **Products are rows, not scenarios.** A behavior every supporting product shares is one Scenario Outline over
   `<product_type>` with one `@capability#<name>` tag; the products come from the registry (`ProductDatFiles`), never
   typed by hand. Product differences in data go into the case data's `byProduct`, not into steps or extra columns.
4. **Negative and permission cases use one representative product.** A validation or permission rule does not change
   per product; repeating it per product multiplies run time without adding coverage.
5. **Data variation that changes no step is data.** A field only some products have is case data (`byProduct`), not an
   `Examples` column. An Outline numbered by `<case>` (`| case | dataset |`) is for datasets that are each a distinct
   business case.
6. **Every scenario stands alone.** Its `Given` provisions the state it needs; no scenario depends on another having run
   or on data a previous run left behind.
7. **Assert only what is verified.** A `Then` may not claim more than the implementation checks: a successful response
   is not proof of persistence, an audit record or a downstream event unless that evidence is actually collected.
8. **Smoke is the critical path.** `@ADO#smoke`: the minimal set that proves the capability works end to end (usually
   the main positive case). Everything else is `@ADO#regression`.
9. **Stable end state, never transient UI.** Assert the business state the user relies on, not spinners, loading
   indicators or toasts that may vanish before they are read.

## 3. Identity and Placement

### 3.1 Feature name

`featureName` = `{object}_{action}` in snake_case; it is the file name, the business tag (`@ADO#{featureName}`) and,
upper-cased with `_` → `-`, the case-ID prefix (`trade_creation` → `TRADE-CREATION`).

- **Object**: the stable domain entity, singular (`trade`, `portfolio`, `reason`). Every trade lifecycle event uses `trade`.
- **Action**: the canonical business noun (`creation`, `cancellation`, `allocation`, `early_termination`,
  `partial_novation_remaining`, `portfolio_reassignment`). Keep meaningful qualifiers (`partial`, `early`, `remaining`);
  drop product names and generic verbs (`manage`, `handle`, `process`, `validate`, `execute`).
- **Established stems win.** Reuse the existing name when one exists, even if it breaks the rule above:
  `trade_approval_rejection`, `trade_step_out_full`, `trade_step_out_partial`, `reason_management`,
  `product_ids_management`, `refdata_sync_management`.
- Never derive the name from a story ID, module, Java class, endpoint or TP number. If the business identity is still
  unclear, report it.

A broad suite tag (`@ADO#trade_lifecycle`) may sit next to the feature tag; it selects, it does not name.

### 3.2 Path

```text
src/test/resources/features/{api|ui}/{domain}/{featureName}.feature
```

| Domain | `api/` | `ui/` |
|---|---|---|
| `trading` (creation, approval/rejection, lifecycle events, audit, query) | ✔ | ✔ |
| `blotter` | ✔ | ✔ |
| `reference_data` (currencies, marketers, portfolios, product IDs, reasons) | ✔ | |
| `refdata` (legacy: `refdata_sync_management` only; new reference data goes to `reference_data`) | ✔ | |
| `risk_management`, `product_management` | ✔ | |
| `compose`, `notification`, `user_management` | | ✔ |

- API and UI are always separate files; the same `featureName` may exist under both. No `_api` / `_ui` / `_e2e` suffix,
  no IDs in file names.
- Check the actual tree before choosing; a new domain needs a reason (no existing domain fits) and is reported.
- **Existing file → append mode**: add Scenario blocks only (no Feature header, tags or `Background:`), and continue
  case numbering from the highest existing number of the same prefix.

## 4. Feature Header

```gherkin
@api @ADO#trade_early_termination @ADO#trade_lifecycle
Feature: Trade Early Termination API
  As a maker
  I want to terminate a live trade before its maturity
  So that the trade stops accruing exposure once the client exits the position
```

- Layer tag: `@api` or `@playwright`. Title: `{Business Capability} API` or `{Business Capability} E2E`, as in every
  existing feature.
- `@expected#none` on the Feature line when its API cases verify other evidence (database, audit) instead of the
  response (implementation standard §5.4).
- API features have no `Background:`. A UI feature may move an identical leading `Given` (typically the login) shared
  by 2+ scenarios into `Background:`; assertions and data preparation stay in the scenarios.
- Suite and polarity tags are never feature-level (they would select the whole file).

## 5. Scenarios

### 5.1 Case ID and title

```gherkin
@ADO#positive @ADO#smoke @capability#earlyTermination
Scenario Outline: [TC-TRADE-EARLY-TERMINATION-<product_type>-API-001] Maker terminates a live <product_type> trade early
```

- ID: `TC-{FEATURE-MODULE}[-{PRODUCT}]-{API|UI}-{NNN}`, inside `[...]` at the start of the scenario name. The case ID is
  what binds the scenario to its test data; it is never a tag (`@TC-...`).
- Product Outline: `<product_type>` in the ID. Product-independent case: no product segment
  (`TC-TRADE-CREATION-API-004`). Numbered dataset Outline: `-API-<case>` at the end.
- `NNN` is three digits, unique per full prefix; check existing features **and** the case rows in `data/api` and
  `data/ui` before assigning one.
- Title: what the actor does and what results, in business words, understandable without the code. One behavior, so no
  "and"; a negative title names the rejected condition (`...is rejected when the counterparty is missing`).

### 5.2 Tags

| Level | Tags |
|---|---|
| Feature | layer (`@api` / `@playwright`), `@ADO#{featureName}`, optional suite tag (`@ADO#trade_lifecycle`), optional `@expected#none` |
| Scenario | exactly one of `@ADO#positive` / `@ADO#negative`, exactly one of `@ADO#smoke` / `@ADO#regression` |
| Product Outline | additionally exactly one `@capability#<name>` (case-sensitive, a `Capability` constant) |

No story, AC or TP IDs as tags. `@wip` excludes a scenario from runs; it is not removed to force an incomplete case in.

## 6. Step Language

Feature files are business specifications: a reviewer who does not read Java must be able to approve them.

- **Allowed**: canonical actors (`maker`, `checker`, `admin`, `user`), business objects, business actions, business
  outcomes and states.
- **Forbidden**: endpoints, HTTP verbs, payloads, file names, selectors, element keys, page/component names, UI mechanics
  (`click`, `type`, `fill`, `select`), snippet or Java names, status codes, response matching.
- `Given` = business precondition; `When` = the business action under test; `Then` = the business outcome.
- **The Then test**: every `Then` answers *what business goal has been achieved?*

  | UI-level (rewrite) | Business outcome |
  |---|---|
  | the Save button is enabled | can submit the trade |
  | the error toast appears | the submission is rejected with a reason |
  | the Allocation tab is visible | can view trade allocations |

- **Reuse before inventing.** Use an existing public step when it expresses the same intent; a new synonym splits the
  vocabulary and the glue. Changing the business intent to fit an existing step is not allowed either.

### 6.1 API steps

- Preconditions reuse the provisioning steps: `a live '<product_type>' trade is created via api`,
  `a '<product_type>' trade is submitted to pending approval via api`, `a draft ...`, `a dead ...`.
- Shared outcomes: `the '<object>' should be '<action>' successfully`, `failed to '<action>' '<object>'`,
  `failed to '<action>' '<object>' with status '<status>'`. They include the status code and the strict comparison with
  the case's recorded response, so neither appears as a step of its own.
- Evidence beyond the response (state, persistence, audit, rejection reason) is its own business `Then`, written only
  when the TP asks for it: `the trade basic status is '<status>'`, `exactly one new audit record exists for action 'SUBMIT'
  on the created trade`.

### 6.2 UI steps

- Login: `maker is logged in to the trade portal` (also `checker`, `admin`, `user`). **Role names appear only in login
  steps**; other UI steps are role-agnostic and act as the last logged-in user. A second login switches the actor.
- Preconditions: `a Live '<product_type>' trade exists in the blotter` (also `Draft Pending Approval`, `Dead`,
  `Draft`). They provision through the API and neither log in nor navigate.
- One step = one user intent or one business-visible assertion: `creates a new '<product_type>' trade`,
  `the trade has status 'Live' and event status 'New' in the blotter`.
- Outcome wording: `completed successfully` (lifecycle event), `approved successfully` / `rejected successfully`
  (checker decision), `submitted successfully` (into pending approval only), `request is rejected with an error`
  (negative execution).

## 7. Traceability Comments

Every generated scenario carries its trace as Gherkin comments directly above its tags; a created file also carries
one feature-level block below the description. They are for reviewers, never read by the runtime.

```gherkin
  # tp: TP-003 | ac: AC-2
  # validationTarget: a live trade is closed by early termination
  # observableEvidence: trade status Terminated in the blotter
  # testDataIntent: a live trade with a positive termination amount
  @ADO#positive @ADO#regression @capability#earlyTermination
  Scenario Outline: ...
```

- Feature level: `businessDomain`, `validationScope`, `source` (story and design references). Scenario level: `tp`,
  `ac`, `validationTarget`, `observableEvidence`, `testDataIntent`.
- Values come from the approved TPs; never invent TP or AC IDs, and never name artifacts (classes, snippets, endpoints,
  files, tables) in them.

## 8. Generation Context (returned with the files)

For each layer: file path and mode (`create` / `append` / `not generated`); how `featureName`, domain and ID prefix
were derived; the TP → scenario grouping with its reason; the AC coverage matrix; any reported gap or conflict.

## 9. Review Checklist

- [ ] Every approved TP is in exactly one scenario; no scenario without a TP; every AC of the TPs is covered.
- [ ] Each scenario proves one behavior; no positive/negative mix; grouping follows §2 rule 2.
- [ ] Every `Then` passes the business-outcome test and claims no more than is verified.
- [ ] Product coverage is a `@capability#` Outline; negative/permission cases use one product.
- [ ] Each scenario provisions its own state; no order dependency.
- [ ] Name, path and ID prefix follow §3; IDs are unique against features and case data; no `@TC-` tags.
- [ ] Exactly one polarity and one suite tag per scenario; no suite/polarity tags at feature level.
- [ ] No implementation detail in steps; role names only in UI login steps; existing steps reused.
- [ ] Append mode added scenarios only; numbering continues the prefix.
- [ ] Trace comments present and sourced from approved TPs.
