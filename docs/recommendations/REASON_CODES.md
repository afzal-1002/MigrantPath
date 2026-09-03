# Recommendation reason codes

Status: Phase 7 implemented (`RecommendationReasonMapper`,
`com.foreignerwarsaw.recommendation.engine`). Every `RecommendationReason` a user can see is one
of the seven `reasonType`s below - never raw English prose from the backend, never a raw Phase 6
condition path (brief §12/§76).

## Reason types

| `reasonType` | Meaning | `conditionCode`/`factCode` | When produced |
|---|---|---|---|
| `MATCHED_CONDITION` | A required or informational rule's leaf condition held. | `conditionCode` set | Every `PASS` leaf of a non-`EXCLUSION` rule. |
| `FAILED_CONDITION` | A required or informational rule's leaf condition did not hold. | `conditionCode` set | Every `FAIL` leaf of a non-`EXCLUSION` rule. |
| `MISSING_INFORMATION` | A leaf condition could not be evaluated - the fact hasn't been answered. | `factCode` set | Every `MISSING` leaf of any rule (required, exclusion, or informational). |
| `EXCLUSION` | This specific condition is *why* an exclusion rule applies. | `conditionCode` set | Every `PASS` leaf of a `SATISFIED` `EXCLUSION` rule. |
| `ALTERNATIVE_PATH` | Reserved - not produced in Phase 7 (see below). | — | — |
| `PROCEDURE_PRIORITY` | Reserved - not produced in Phase 7 (see below). | — | — |
| `ANALYSIS_ERROR` | The underlying rule returned `ERROR` - a configuration problem, not a fact about the user. | neither set | Once per rule whose `RuleEvaluationResult.status()` is `ERROR`; no per-condition detail is generated for it. |

## What is deliberately not surfaced

- A `NOT_SATISFIED` exclusion rule's leaves produce **no** reasons at all - "this exclusion
  doesn't apply" is not interesting to a user and would just be noise.
- `ALTERNATIVE_PATH` and `PROCEDURE_PRIORITY` are declared in the schema/enum (matching the
  brief's exact §10 list) but never emitted by `RecommendationReasonMapper` in Phase 7 - they
  would describe a ranking-driven relationship between two procedures, and Phase 7's ranker
  (see [RANKING_POLICY.md](RANKING_POLICY.md)) does not yet produce that kind of relationship to
  describe. A future phase that adds real `recommendationPriority`-driven demotion could populate
  these to explain *why* a candidate was ranked as an alternative.

## `messageKey` and translation

`messageKey` is a stable key (from the underlying `LeafCondition.explanationKey`, falling back to
the rule's own `RuleVersion.explanationKey`, e.g. `"rule.blueCard.salary"`) - **never** itself
user-facing text (brief §54/§83). No i18n framework exists yet anywhere in this codebase (Phase 5
was English-only); the current frontend (`RecommendationResults`) maps `reasonType` to a small
local English fallback string rather than looking up `messageKey` in a translation table -
documented as a known simplification in PHASE_7_REPORT.md, not a design decision worth reversing
before a real i18n pipeline exists to plug into.

## Ordering

Reasons are generated per rule, rules processed in a stable `(ruleType, ruleCode)` order (never
input-list order), and within one rule in the order Phase 6 itself already returns conditions
(passed, then failed, then missing) - `displayOrder` on the persisted row reflects this exactly,
so a client never needs to re-sort.
