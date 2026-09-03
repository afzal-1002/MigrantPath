# Recommendation classification policy

Status: Phase 7 implemented (`RecommendationClassifier`,
`com.foreignerwarsaw.recommendation.engine`). This is the exact, exhaustive mapping from Phase
6's per-rule `RuleEvaluationResult`s to one per-procedure `RecommendationType` - no ambiguity is
left to the reader (mirrors [OPERATOR_SEMANTICS.md](../rules/OPERATOR_SEMANTICS.md)'s discipline).

## Rule role = Phase 6's `RuleType` (no parallel field)

Rather than introduce a second "rule role" concept, the classifier reuses Phase 6's `RuleType`
directly:

| `RuleType` | Role here |
|---|---|
| `ELIGIBILITY`, `APPLICABILITY`, `REQUIREMENT` | **Required** - must hold for the procedure to stay a candidate. |
| `EXCLUSION` | **Exclusionary** - a `SATISFIED` exclusion is a definitive "does not apply." |
| `INFORMATION_REQUIRED` | **Informational only** - never gates the classification on its own; its conditions still produce reasons (see [REASON_CODES.md](REASON_CODES.md)), just never change the outcome. |

## The classification table

Given every `RuleEvaluationResult` Phase 6 returned for one candidate procedure:

| Any result is `ERROR`? | Any exclusion `SATISFIED`? | Any required `NOT_SATISFIED`? | Any required or exclusion `INDETERMINATE`? | Result |
|---|---|---|---|---|
| yes | — | — | — | `UNAVAILABLE_FOR_ANALYSIS` |
| no | yes | — | — | `NOT_APPLICABLE` |
| no | no | yes | — | `NOT_APPLICABLE` |
| no | no | no | yes | `MORE_INFORMATION_REQUIRED` |
| no | no | no | no | `PRIMARY_MATCH` (candidate - see [RANKING_POLICY.md](RANKING_POLICY.md) for the final `PRIMARY_MATCH` vs `POSSIBLE_ALTERNATIVE` decision) |

Rows are checked top to bottom, first match wins - `ERROR` always outranks everything else
(never silently converted to a confident `NOT_APPLICABLE`), and a known "no" on a required rule
always outranks an unrelated unanswered question elsewhere.

If a candidate procedure has no `ELIGIBILITY`/`APPLICABILITY`/`REQUIREMENT` rule at all (only an
`EXCLUSION` and/or `INFORMATION_REQUIRED` rule targets it), the "any required NOT_SATISFIED"/
"any required INDETERMINATE" checks are vacuously false - the procedure becomes a match candidate
by default, subject to the exclusion check still applying normally.

## Two further gates before classification even runs

- **No `Procedure` row for the rule's `targetCode`** - the candidate is dropped entirely (no
  `Recommendation` row is created for it at all; there is nothing to attach one to).
- **No active `PUBLISHED` `ProcedureVersion` at the evaluation date** - `UNAVAILABLE_FOR_ANALYSIS`
  regardless of what the rules say, before the classifier is even consulted. Showing "this route
  might apply" with no actual published content to display would be incomplete legal guidance.

## What this phase deliberately does not do

- **Alternative legal bases** (a procedure reachable through Rule A *or* Rule B *or* Rule C, brief
  §19) are not modeled - each `RuleVersion` is evaluated as a standalone tree (ADR-009's own
  scope boundary). A procedure with multiple independent eligibility rules would currently need
  every one of them satisfied, not any one of them - revisit only once a real procedure needs
  this shape.
- **`POSSIBLE_ALTERNATIVE`** is never produced by the classifier itself - see
  [RANKING_POLICY.md](RANKING_POLICY.md) for where and how it can arise.
