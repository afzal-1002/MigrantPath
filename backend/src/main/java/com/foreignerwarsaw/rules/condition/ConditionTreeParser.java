package com.foreignerwarsaw.rules.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns raw {@code condition_tree} JSON into a validated {@link ConditionNode} tree - the single
 * place anything in this codebase casts/navigates the raw JSON shape (brief §66: "do not
 * deserialize arbitrary object maps and cast everywhere"). Purely structural: rejects a malformed
 * shape, an unknown operator name, or excessive nesting, but never checks whether a referenced
 * fact/threshold/country group actually exists - that needs database access {@code
 * ConditionTreeValidator} has and this pure parser deliberately doesn't (brief §112's "structured
 * validation issues" still apply at the higher layer).
 */
public final class ConditionTreeParser {

  /**
   * Absurd nesting is rejected outright (brief §9) - 10 levels is generous for any hand-authored
   * legal rule; a JSON tree deeper than that is far more likely a mistake or an attempt at abuse
   * than a genuine condition.
   */
  private static final int MAX_DEPTH = 10;

  private static final Set<String> LEAF_KEYS =
      Set.of("fact", "operator", "value", "threshold", "code", "explanationKey");

  private ConditionTreeParser() {}

  public static ConditionNode parse(JsonNode root) {
    return parse(root, 0);
  }

  private static ConditionNode parse(JsonNode node, int depth) {
    if (depth > MAX_DEPTH) {
      throw new ConditionTreeParseException(
          "Condition tree exceeds the maximum nesting depth of " + MAX_DEPTH);
    }
    if (node == null || !node.isObject()) {
      throw new ConditionTreeParseException("Each condition node must be a JSON object");
    }

    boolean hasAll = node.has("all");
    boolean hasAny = node.has("any");
    boolean hasNot = node.has("not");
    boolean hasFact = node.has("fact");
    int shapeCount = (hasAll ? 1 : 0) + (hasAny ? 1 : 0) + (hasNot ? 1 : 0) + (hasFact ? 1 : 0);
    if (shapeCount != 1) {
      throw new ConditionTreeParseException(
          "Each condition node must be exactly one of: all, any, not, or a leaf (fact/operator) - found "
              + shapeCount
              + " in "
              + node);
    }

    if (hasAll) {
      return new AllNode(parseChildren(requireArray(node.get("all"), "all"), depth));
    }
    if (hasAny) {
      return new AnyNode(parseChildren(requireArray(node.get("any"), "any"), depth));
    }
    if (hasNot) {
      return new NotNode(parse(node.get("not"), depth + 1));
    }
    return parseLeaf(node);
  }

  private static List<ConditionNode> parseChildren(JsonNode array, int depth) {
    if (array.isEmpty()) {
      throw new ConditionTreeParseException("all/any must have at least one child condition");
    }
    List<ConditionNode> children = new ArrayList<>();
    for (JsonNode child : array) {
      children.add(parse(child, depth + 1));
    }
    return children;
  }

  private static JsonNode requireArray(JsonNode node, String key) {
    if (node == null || !node.isArray()) {
      throw new ConditionTreeParseException("\"" + key + "\" must be a JSON array");
    }
    return node;
  }

  private static LeafCondition parseLeaf(JsonNode node) {
    for (var fieldIterator = node.fieldNames(); fieldIterator.hasNext(); ) {
      String field = fieldIterator.next();
      if (!LEAF_KEYS.contains(field)) {
        throw new ConditionTreeParseException("Unknown leaf condition field: " + field);
      }
    }

    JsonNode factNode = node.get("fact");
    if (factNode == null || !factNode.isTextual() || factNode.asText().isBlank()) {
      throw new ConditionTreeParseException("A leaf condition requires a non-blank \"fact\"");
    }

    JsonNode operatorNode = node.get("operator");
    if (operatorNode == null || !operatorNode.isTextual()) {
      throw new ConditionTreeParseException("A leaf condition requires an \"operator\"");
    }
    ComparisonOperator operator;
    try {
      operator = ComparisonOperator.valueOf(operatorNode.asText());
    } catch (IllegalArgumentException e) {
      throw new ConditionTreeParseException("Unknown operator: " + operatorNode.asText());
    }

    JsonNode value = node.get("value");
    JsonNode thresholdNode = node.get("threshold");
    String threshold = thresholdNode == null ? null : thresholdNode.asText();
    if (value != null && threshold != null) {
      throw new ConditionTreeParseException(
          "A leaf condition cannot have both \"value\" and \"threshold\" - they are mutually exclusive");
    }
    boolean needsComparand =
        operator != ComparisonOperator.EXISTS && operator != ComparisonOperator.NOT_EXISTS;
    if (needsComparand && value == null && threshold == null) {
      throw new ConditionTreeParseException(
          operator + " requires either \"value\" or \"threshold\"");
    }

    JsonNode codeNode = node.get("code");
    JsonNode explanationKeyNode = node.get("explanationKey");
    return new LeafCondition(
        codeNode == null ? null : codeNode.asText(),
        factNode.asText(),
        operator,
        value,
        threshold,
        explanationKeyNode == null ? null : explanationKeyNode.asText());
  }
}
