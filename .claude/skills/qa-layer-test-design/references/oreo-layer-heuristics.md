# OREO layer heuristics

Domain knowledge for choosing layers and evidence in oreo-e2e. It sharpens `docs/standards/test-layering-standards.md`
with OREO's roles, states, surfaces and products; it never overrides it. Facts about a story still come from the story.

## What each layer can observe here

| Layer | Observes | Cannot observe |
|---|---|---|
| `@api` | Status and the full response (compared with a recording), a read-back of the trade (`the trade basic status is ...`), audit records and database rows when glue for them exists | What the user sees; whether an action is offered on a screen |
| `@playwright` | The blotter (status, event status columns), trade detail page, dialogs, the New Trade form, notifications, which actions are offered to the logged-in role | Response contracts; exact backend values not shown on screen |

API-based setup (`a live '<P>' trade is created via api`, `a Live '<P>' trade exists in the blotter`) never decides
the layer: it is how the state is reached, not what is proven.

## Roles and the maker-checker matrix

Roles: `maker`, `checker`, `admin`, `user`. The authorization matrix (who may submit, approve, reject, perform an
event; self-approval; a second approval) is enforced by the backend: prove it at `@api`, one TP per rule, on one
representative product. Add a `@playwright` TP only for what the user experiences: the action is not offered to the
maker, the checker's approval appears in the blotter. One real-role UI journey is usually enough.

## Trade states

`Draft`, `Draft Pending Approval`, `Live`, `Dead`, and the end states lifecycle events lead to (`Terminated`,
`Novated`, `Cancelled`, ...; take the exact names from the story or an existing feature, never guess). For each event:
the allowed transition from `Live` (positive), and the likely forbidden ones (on a `Dead` or `Draft Pending Approval`
trade, twice, after cancellation) as negative `@api` TPs.

## Surfaces

The blotter (trade portal) and the trade detail page often offer the same action through the same API. One API TP
covers the rule for both; a UI TP per surface only when the surfaces are implemented separately and the story changes
both.

## Products and capabilities

- A behavior every supporting product shares: `all supporting products` with its `capability`, one TP (phase 2 makes it
  a product Outline; the registry `ProductDatFiles` supplies the rows).
- Validation, permission and forbidden-transition rules: `one representative product`, not per product.
- A behavior only some products have, or that differs per product: say which products and why; a difference in data
  only is not a separate TP (it becomes `byProduct` data).
- Product knowledge (payoff, fixing, knock-out, barrier, settlement) is never assumed: it is a gap (standard §6).

## Choosing evidence

| Requirement says | Evidence | Strength |
|---|---|---|
| "is accepted / rejected" | response status + recorded response | 1 |
| "the trade becomes / remains ..." | read-back of the trade | 2 |
| "is recorded / audited / sent downstream" | audit record, database, event; only if the environment exposes it | 3 |
| "the user sees / can / cannot ..." | blotter, detail page, dialog, offered actions | UI |

A rejection TP names its reason when the requirement does ("rejected as the trade is not Live"), and what stays
unchanged when that matters.

## Mocks and controlled dependencies

The risk engine and other external dependencies may be controlled (setup files such as `risk-calc-success-mock`, the
`oreo.risk.engine.mode` property). That is fine for dependencies that are not the target. Never use a mocked response
to prove authorization, persistence, state or audit.

## Re-routing examples

| Behavior | Layer | Why |
|---|---|---|
| Required field missing in the request is rejected | `@api` | Request contract |
| Required field shows an inline error on the New Trade form | below this repo (FE) | Local form behavior; or one `@playwright` TP only if the story is about the journey |
| Maker cannot approve their own trade | `@api` (+ `@playwright` if the story says the action is hidden) | Enforcement vs. what the user sees are two targets |
| Live trade early-terminated for every product | `@api`, all supporting products | Backend rule shared by products |
| Terminated trade shown as closed in the blotter | `@playwright`, one representative product | Only the browser shows it |
| Early termination on a Dead trade is rejected | `@api`, one representative product | State rule, product-independent |
| Notification appears when a trade is approved | `@playwright` | The evidence is in the browser, even if the approval is triggered via API |
