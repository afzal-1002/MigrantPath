# Operator and combination-node semantics

Status: Phase 6 implemented (`RuleEvaluator`, `ConditionEvaluator`,
`com.foreignerwarsaw.rules.evaluation`/`com.foreignerwarsaw.common.evaluation`). This is
the exact, exhaustive contract — no ambiguity is left to the reader (brief §124).

## The four-state leaf result

Every leaf condition resolves to exactly one of:

| Result | Meaning |
|---|---|
| `PASS` | The comparison was evaluated and held. |
| `FAIL` | The comparison was evaluated and did not hold. |
| `MISSING` | The fact this leaf needs has not been answered yet — **never** treated as `FAIL`. |
| `ERROR` | The rule's own configuration is broken (unknown threshold at runtime, a type mismatch) — **never** treated as `FAIL`, and never silently swallowed. |

### When a leaf is `MISSING`

A leaf is `MISSING` iff its operator is **not** `EXISTS`/`NOT_EXISTS` **and** the
resolved fact value is absent (`null`, or an empty collection for a multi-select fact).
`EXISTS`/`NOT_EXISTS` are the one family of operators that legitimately evaluate an
absent fact — `NOT_EXISTS` against a genuinely-absent fact is `PASS`, not `MISSING`,
because "this was never answered" is exactly what `NOT_EXISTS` is asking.

### When a leaf is `ERROR`

- The fact resolver itself threw (a derived fact's computation failed unexpectedly).
- A `threshold` reference has no active `PUBLISHED` `ThresholdVersion` for the
  evaluation date.
- A country-group leaf's fact did not resolve to a country-code string, or its `value`
  is not a country-group code string.
- The underlying typed comparison threw (e.g. a non-numeric value against a numeric
  operator) — a configuration problem `ConditionTreeValidator` should have caught at
  publish time, but the runtime evaluator never trusts that and always guards against it.
- The condition tree's raw JSON failed to parse, or failed `ConditionTreeParser`'s
  structural checks — surfaces as a single root-level `ERROR`, not a thrown exception
  (brief §64: a broken rule must never crash an assessment).

## Combination-node semantics

Given `AllNode`/`AnyNode`/`NotNode` children already resolved to a four-state result
each:

### `ALL`

| Any child is... | Result |
|---|---|
| `ERROR` | `ERROR` |
| `FAIL` (and no `ERROR`) | `FAIL` |
| `MISSING` (and no `ERROR`/`FAIL`) | `MISSING` |
| otherwise (every child `PASS`) | `PASS` |

A known "no" always outranks an unrelated unanswered question — `ALL(FAIL, MISSING) =
FAIL`, never `MISSING` (brief §30/§93).

### `ANY`

| Any child is... | Result |
|---|---|
| `PASS` | `PASS` |
| `ERROR` (and no `PASS`) | `ERROR` |
| `MISSING` (and no `PASS`/`ERROR`) | `MISSING` |
| otherwise (every child `FAIL`) | `FAIL` |

A single known "yes" is enough regardless of what else is broken or unanswered —
`ANY(PASS, ERROR) = PASS`.

### `NOT`

| Child | Result |
|---|---|
| `PASS` | `FAIL` |
| `FAIL` | `PASS` |
| `MISSING` | `MISSING` (unchanged) |
| `ERROR` | `ERROR` (unchanged) |

Negating "I don't know yet" can never manufacture a known answer, and negating a
configuration error can never manufacture a legitimate result (brief §31).

## Rule-level status

The root node's four-state result maps 1:1 to `RuleEvaluationStatus`:

```
PASS     → SATISFIED
FAIL     → NOT_SATISFIED
MISSING  → INDETERMINATE
ERROR    → ERROR
```

Deliberately not named `ELIGIBLE`/`NOT_ELIGIBLE` — this is a statement about whether
*this rule's* conditions held, never a final user-facing recommendation (Phase 7's job).

## Operator reference

`ComparisonOperator` (`com.foreignerwarsaw.common.evaluation`), evaluated by
`ConditionEvaluator` for everything except the two country-group operators, which
`RuleEvaluator` intercepts and delegates to `CountryClassificationService` before ever
reaching `ConditionEvaluator` (it throws `IllegalStateException` if one somehow does).

| Operator | Applies to | Semantics |
|---|---|---|
| `EQUALS` / `NOT_EQUALS` | any typed scalar | Typed equality — never a string comparison (`"15000" == "15000.0"` would be wrong; `BigDecimal`/`LocalDate`/`Boolean` comparison is used per the actual Java type). |
| `IN` / `NOT_IN` | single-select/string/country | `value` is a JSON array of allowed scalars; typed equality against any element. |
| `CONTAINS` / `NOT_CONTAINS` | multi-select (a `Collection`) | `value` is a single scalar checked for membership in the collection. |
| `EXISTS` / `NOT_EXISTS` | any | True iff the fact is present (non-null, and non-empty for a collection/blank string). The only operators that never produce `MISSING`. |
| `GREATER_THAN` / `GREATER_THAN_OR_EQUAL` / `LESS_THAN` / `LESS_THAN_OR_EQUAL` | numeric (`BigDecimal`/`Long`/`Integer`) | `BigDecimal.compareTo` — typed numeric comparison, never a string comparison (`"15000" > "9000"` as strings would be wrong). |
| `BETWEEN` | numeric | `value` is a two-element `[min, max]` JSON array, **both bounds inclusive**. Any other array size throws (`ERROR`). |
| `DATE_BEFORE` / `DATE_BEFORE_OR_EQUAL` / `DATE_AFTER` / `DATE_AFTER_OR_EQUAL` | `LocalDate` | `LocalDate.compareTo` against `value` parsed as an ISO date string. |
| `IS_MEMBER_OF_COUNTRY_GROUP` / `IS_NOT_MEMBER_OF_COUNTRY_GROUP` | `CITIZENSHIP_COUNTRY`-shaped facts | `value` is a country-group code string (e.g. `"EU_MEMBER"`); delegates to `CountryClassificationService.isMember(country, group, evaluationDate)` — never a hardcoded country list in a rule. |

`DURATION_*` operators from the original Phase 0 sketch were deliberately not
implemented — no current fact needs them (brief §45); add one only alongside a real,
tested duration fact.

## Boundary-case guarantees (tested)

- `BETWEEN`: the exact min and the exact max are both `PASS` (inclusive both ends);
  one unit outside either bound is `FAIL`.
- `GREATER_THAN_OR_EQUAL`/`LESS_THAN_OR_EQUAL`: the exact threshold value is `PASS`.
- `DATE_BEFORE_OR_EQUAL`/`DATE_AFTER_OR_EQUAL`: the exact same date is `PASS`.
- Numeric precision: `BigDecimal` throughout — `13355.34` vs `13355.339` compares
  correctly; `double`/`float` are never used for a legal numeric comparison.

## `UNSURE` answers

Phase 5's `AssessmentFacts.answersByQuestionCode` never contains an `"unsure"` value —
an answer marked unsure is simply absent from the map (`AssessmentFactsService`,
Phase 5), identically to a question never reached. Phase 6 therefore treats an explicit
`UNSURE` answer exactly as `MISSING` — insufficient information — for every rule, by
construction, not by a per-rule policy choice (brief §47/§97's recommended default).
