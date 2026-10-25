# Test Layering Standard (Phase 1: Test Analysis)

Turns a confirmed story into **test points** (TP): what must be proven, how it is observed, and on which execution
layer. It is test analysis, not automation: no Gherkin, file names or code. Downstream,
[feature generation](bdd-feature-generation-standards.md) turns the TPs into scenarios and
[implementation](bdd-feature-implementation-standards.md) makes them run.

**Output**: the TP table (§5), the AC coverage matrix and the gap list (§6).

## 1. Inputs and Authority

| Question | Decided by | Never from |
|---|---|---|
| Which behaviors exist | Confirmed Story Contract ACs and business fields; approved assumptions | Inventing behavior; existing coverage ("the other events have it") |
| How an outcome is observed | Story Contract `observableEvidence`; else the AC's expected outcome | Dropping evidence because it is hard to automate |
| Endpoints, screens, data the design implies | Approved `solutionDesign` (`technicalNotes` only as weak context) | `technicalNotes` that contradict `solutionDesign` |
| Product and domain rules | The story and the BA | FX product knowledge filled in as fact (§6) |

## 2. Analysis Method

Work through each AC in this order; each step can add test points.

1. **Split compound ACs.** One TP proves one behavior. An AC with two outcomes, two actors or two layers becomes
   several TPs.
2. **Find the rule and its partitions.** For every input the rule depends on: valid and invalid classes (equivalence
   partitioning), the edges between them (boundary values: 0, minimum, maximum, just outside), and missing / empty /
   wrong type.
3. **Walk the state model.** For lifecycle behavior: every allowed transition, and the most likely forbidden ones (an
   event on a Dead or Pending Approval trade, a second approval, an action after cancellation).
4. **Cover the roles.** Maker-checker: who may act, who must be refused (checker submitting, maker approving, self-
   approval), and what an unauthorized user gets.
5. **Decide the product scope.** Does the behavior hold for every product that supports the capability, for one
   representative product (validation and permission rules rarely vary by product), or for one product only?
6. **Rank by risk.** Impact (money, regulatory, data integrity, lost trade) × likelihood (new code, complex rule,
   past defects). High risk gets the strongest evidence and smoke; low risk may share a TP or be left out with a
   reason.

Negative coverage is not optional: for each positive TP, ask which invalid input, forbidden state and unauthorized role
the requirement implies.

## 3. Choosing the Layer

**Rule: test each behavior on the lowest layer that can produce its evidence.** API tests are faster, more stable
and cheaper to maintain; the UI is for what only the browser can show.

| The validation target is... | Layer |
|---|---|
| A business rule, validation, calculation, permission, maker-checker enforcement, state transition, persistence, audit | `@api` |
| A request/response contract (fields, types, error codes and messages) | `@api` |
| What the user sees or can do: displayed state, available/unavailable actions, form behavior, navigation, notifications | `@playwright` |
| An end-to-end user journey whose risk is in the wiring between screen and backend | `@playwright`, few and smoke-level |

- **Choose by evidence, not by name.** An endpoint or a page existing for a capability does not decide the layer.
- **How the setup is done does not decide the layer.** A UI scenario may provision its trade through the API, and a
  notification test may trigger its event through the API; both are `@playwright` because the evidence is in the
  browser.
- **Duplication needs distinct value.** The same rule on both layers is justified only when each proves something the
  other cannot: backend self-approval prevention (API) versus the approve action being unavailable to the maker (UI)
  are two targets, not a duplicate. Otherwise keep the API TP and drop the UI one.
- **Do not promote backend checks to UI** to "see them work"; do not push user-visible behavior down to API because it
  is easier to automate.

## 4. Evidence

The **observable evidence** says how the outcome is seen. Choose the weakest evidence that still proves the target,
and never claim a stronger one than the test will check:

| Strength | Evidence | Proves |
|---|---|---|
| 1 | Status code + response body | The request was accepted / rejected with this contract |
| 2 | A read-back of the business object (status, event status, fields) | The state changed as required |
| 3 | Persistence / audit record / downstream event | The change was recorded or propagated |
| UI | Displayed state, enabled/disabled actions, messages in the browser | What the user experiences |

A "successful response" is level 1; it is not evidence of persistence, audit or a downstream event (levels 2-3) unless
the TP says that evidence is collected. Transient UI (spinners, toasts that disappear) is not evidence; stable end state
is.

## 5. Test Point Fields

| Field | Content |
|---|---|
| `TP-###` | Sequential, neutral, stable within the report |
| `Scenario Name` | English, specific: actor, action, outcome (`Maker cannot approve a trade they submitted`) |
| `Layer` | Exactly one: `@api` or `@playwright` |
| `Tags` | Exactly one polarity (`@ADO#positive` / `@ADO#negative`) and one suite (`@ADO#smoke` / `@ADO#regression`) |
| `Product Scope` | `all supporting products` / `one representative product` / `product-independent` / `<product> only` |
| `AC Mapping` | One or more AC IDs |
| `Validation Target` | What must be proven, as a rule (`a checker cannot approve their own submission`) |
| `Observable Evidence` | How it is seen, with its strength (§4) |
| `Risk` | High / Medium / Low, with the reason in one phrase |
| `Reasoning` | Why this layer and this evidence (one or two sentences) |

- **Smoke** = the few high-risk positive TPs that prove the capability works end to end; everything else is
  regression. A negative TP is smoke only when its failure would be critical (for example, self-approval).
- No feature names, file paths, TC IDs, step wording, snippets, classes or endpoints: those are decided downstream.
  Business, capability and case-ID tags are added at feature generation, never here.

Example:

| TP | Scenario Name | Layer | Tags | Product Scope | AC | Validation Target | Observable Evidence | Risk | Reasoning |
|---|---|---|---|---|---|---|---|---|---|
| TP-001 | Maker terminates a live trade early | `@api` | `@ADO#positive @ADO#smoke` | all supporting products | AC-1 | A live trade can be early-terminated | Response accepted; trade read back as Terminated (2) | High | Core lifecycle rule, backend-owned |
| TP-002 | Early termination of a dead trade is rejected | `@api` | `@ADO#negative @ADO#regression` | one representative product | AC-2 | A dead trade cannot be terminated | Rejection with reason (1) | Medium | State rule; does not vary by product |
| TP-003 | Terminated trade shows as closed in the blotter | `@playwright` | `@ADO#positive @ADO#regression` | one representative product | AC-3 | The user sees the terminated state | Blotter status Terminated (UI) | Medium | Only the browser shows it |

## 6. Gaps

When safe test design needs information the story or design does not give, list it as a gap with the question to ask
and the TPs it blocks; never fill it with assumed product behavior. Typical areas for FX structured products:

- target redemption, knock-out and early termination amounts
- fixing, settlement, maturity, tenor, holiday calendar and value date rules
- currency pair, direction, strike, notional, leverage and product variant constraints
- payoff, precision, rounding and rate source
- maker-checker roles and self-approval
- lifecycle states and the transitions allowed from each
- persistence, audit, downstream events, confirmations and reporting

## 7. Review Checklist

- [ ] Every AC is in at least one TP; every `observableEvidence` item is covered or its omission explained.
- [ ] Compound ACs are split; each TP proves one behavior.
- [ ] Partitions, boundaries, forbidden transitions and unauthorized roles were considered for each rule.
- [ ] Each TP is on the lowest layer that can produce its evidence; API/UI duplicates have distinct value.
- [ ] API-based setup or event triggering did not decide a layer.
- [ ] Evidence strength matches the target and is not overstated.
- [ ] Product scope is stated; negative and permission TPs are not repeated per product without a reason.
- [ ] Exactly one layer, one polarity and one suite tag per TP; smoke is small and high-risk.
- [ ] Gaps are listed with their question and the TPs they block, not guessed.
- [ ] No Gherkin, paths, IDs, steps or implementation details.
