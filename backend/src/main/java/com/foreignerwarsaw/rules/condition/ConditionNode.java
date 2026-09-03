package com.foreignerwarsaw.rules.condition;

/**
 * A parsed, validated node of a {@code RuleVersion.conditionTree} (docs/database/DATABASE.md §5,
 * brief §8/§9) - deliberately a closed, data-only shape (never executable code: no JavaScript,
 * SpEL, SQL fragment, or Groovy is ever accepted, brief §8/§111). Built once by {@link
 * ConditionTreeParser} from the raw JSONB, then walked by {@code
 * com.foreignerwarsaw.rules.evaluation.RuleEvaluator} - nothing downstream re-parses raw JSON or
 * casts an arbitrary {@code Map}/{@code JsonNode} by hand (brief §66).
 */
public sealed interface ConditionNode permits AllNode, AnyNode, NotNode, LeafCondition {}
