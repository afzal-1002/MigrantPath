# Rule condition-tree schema

Status: Phase 6 implemented. See [ADR-009](../architecture/ADR/ADR-009-deterministic-condition-tree-engine.md)
for why this shape was chosen, [DATABASE.md §5](../database/DATABASE.md#5-rule-engine-entities-implemented-phase-6--see-adr-009)
for the storage model, and [OPERATOR_SEMANTICS.md](OPERATOR_SEMANTICS.md) for exact
evaluation semantics.

`RuleVersion.condition_tree` is a JSONB document. It is parsed structurally by
`ConditionTreeParser` (`com.foreignerwarsaw.rules.condition`) into a `ConditionNode`
tree, then validated semantically by `ConditionTreeValidator` before a version may be
published. **A condition tree is data — never executable code.** No JavaScript, SpEL,
SQL fragment, or Groovy is ever accepted.

## Node shapes

Every node is a JSON object that is **exactly one** of the four shapes below — combining
two shapes on the same object, or using none of them, is a parse error.

### `all` — every child must hold

```json
{ "all": [ <node>, <node>, ... ] }
```

At least one child is required. See OPERATOR_SEMANTICS.md for the exact PASS/FAIL/
MISSING/ERROR combination rule.

### `any` — at least one child must hold

```json
{ "any": [ <node>, <node>, ... ] }
```

Same non-empty-array requirement as `all`.

### `not` — inverts one child

```json
{ "not": <node> }
```

### Leaf — one comparison against a fact

```json
{
  "code": "BLUE_CARD_SALARY",
  "fact": "MONTHLY_GROSS_SALARY",
  "operator": "GREATER_THAN_OR_EQUAL",
  "threshold": "BLUE_CARD_MIN_SALARY",
  "explanationKey": "rule.blueCard.salary"
}
```

| Field | Required | Notes |
|---|---|---|
| `fact` | yes | A code from the [Fact Registry](FACT_REGISTRY.md) — validated at publish time, never a free-typed string at runtime. |
| `operator` | yes | One of `ComparisonOperator` (see OPERATOR_SEMANTICS.md) — validated against the fact's allowed-operator set. |
| `value` | for every operator except `EXISTS`/`NOT_EXISTS`, mutually exclusive with `threshold` | A JSON literal (scalar, or array for `IN`/`NOT_IN`/`BETWEEN`). For `IS_MEMBER_OF_COUNTRY_GROUP`/`IS_NOT_MEMBER_OF_COUNTRY_GROUP`, a country-group code as a JSON string (e.g. `"EU_MEMBER"`). |
| `threshold` | mutually exclusive with `value` | A `Threshold.code`, resolved against a real `ThresholdVersion` at evaluation time — never a literal amount baked into the tree. |
| `code` | no | A stable identifier for this leaf, used in trace output instead of a positional path (brief §35) — omit only for a condition that will never need individually citing. |
| `explanationKey` | no | A language-neutral key Phase 7 later maps to translated, legally-reviewed prose — never itself user-facing text. |

## Nesting

Nodes nest arbitrarily via `all`/`any`/`not`, to a maximum depth of 10 (`ConditionTreeParser.MAX_DEPTH`)
— generous for any hand-authored legal rule; deeper is treated as a mistake, not a
legitimate need.

## Full example

```json
{
  "all": [
    {
      "code": "BLUE_CARD_NON_EU_CITIZEN",
      "fact": "CITIZENSHIP_COUNTRY",
      "operator": "IS_NOT_MEMBER_OF_COUNTRY_GROUP",
      "value": "EU_MEMBER"
    },
    {
      "any": [
        { "code": "BLUE_CARD_WORK_GOAL", "fact": "GOALS", "operator": "CONTAINS", "value": "WORK" },
        { "code": "BLUE_CARD_HQ_WORK_GOAL", "fact": "GOALS", "operator": "CONTAINS", "value": "HIGHLY_QUALIFIED_WORK" }
      ]
    },
    { "code": "BLUE_CARD_JOB_OFFER", "fact": "HAS_JOB_OFFER", "operator": "EQUALS", "value": true },
    {
      "code": "BLUE_CARD_SALARY",
      "fact": "MONTHLY_GROSS_SALARY",
      "operator": "GREATER_THAN_OR_EQUAL",
      "threshold": "BLUE_CARD_MIN_SALARY",
      "explanationKey": "rule.blueCard.salary"
    },
    {
      "not": { "code": "BLUE_CARD_NOT_UNSURE_STATUS", "fact": "CURRENT_LEGAL_STATUS", "operator": "EQUALS", "value": "UNSURE" }
    }
  ]
}
```

## Schema versioning

`RuleVersion.condition_schema_version` (currently `1`) records the JSON shape's own
version — independent of `RuleEvaluator.ENGINE_VERSION` (evaluation *semantics*, see
OPERATOR_SEMANTICS.md), and independent of the `RuleVersion.version_number` (legal
*content*). Bump it only if the tree's JSON shape itself changes incompatibly.
