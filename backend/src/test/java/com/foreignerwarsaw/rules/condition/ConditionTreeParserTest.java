package com.foreignerwarsaw.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import org.junit.jupiter.api.Test;

/**
 * Purely structural parsing (brief §8/§9/§66) - never touches a database, never checks whether a
 * referenced fact/threshold/country group actually exists ({@link ConditionTreeValidatorTest}
 * covers that layer).
 */
class ConditionTreeParserTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private JsonNode json(String json) throws Exception {
    return objectMapper.readTree(json);
  }

  @Test
  void parsesASingleLeafCondition() throws Exception {
    ConditionNode node =
        ConditionTreeParser.parse(
            json(
                "{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN\",\"threshold\":\"BLUE_CARD_SALARY_THRESHOLD\"}"));

    assertThat(node).isInstanceOf(LeafCondition.class);
    LeafCondition leaf = (LeafCondition) node;
    assertThat(leaf.fact()).isEqualTo("SALARY_MONTHLY_GROSS");
    assertThat(leaf.operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
    assertThat(leaf.threshold()).isEqualTo("BLUE_CARD_SALARY_THRESHOLD");
    assertThat(leaf.value()).isNull();
  }

  @Test
  void parsesNestedAllAnyNot() throws Exception {
    ConditionNode node =
        ConditionTreeParser.parse(
            json(
                """
                {"all":[
                  {"fact":"AGE_YEARS","operator":"GREATER_THAN_OR_EQUAL","value":18},
                  {"any":[
                    {"fact":"EMPLOYMENT_TYPE","operator":"EQUALS","value":"CONTRACT"},
                    {"not":{"fact":"HAS_CRIMINAL_RECORD","operator":"EQUALS","value":true}}
                  ]}
                ]}
                """));

    assertThat(node).isInstanceOf(AllNode.class);
    AllNode all = (AllNode) node;
    assertThat(all.children()).hasSize(2);
    assertThat(all.children().get(1)).isInstanceOf(AnyNode.class);
    AnyNode any = (AnyNode) all.children().get(1);
    assertThat(any.children().get(1)).isInstanceOf(NotNode.class);
  }

  @Test
  void rejectsANodeThatIsNeitherAllAnyNotNorALeaf() throws Exception {
    assertThatThrownBy(() -> ConditionTreeParser.parse(json("{}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsANodeCombiningTwoShapes() throws Exception {
    assertThatThrownBy(
            () ->
                ConditionTreeParser.parse(
                    json(
                        "{\"all\":[{\"fact\":\"X\",\"operator\":\"EXISTS\"}],\"fact\":\"Y\",\"operator\":\"EXISTS\"}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsAnEmptyAllArray() throws Exception {
    assertThatThrownBy(() -> ConditionTreeParser.parse(json("{\"all\":[]}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsAnUnknownOperator() throws Exception {
    assertThatThrownBy(
            () ->
                ConditionTreeParser.parse(
                    json("{\"fact\":\"X\",\"operator\":\"FLIBBERTIGIBBET\"}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsBothValueAndThresholdOnTheSameLeaf() throws Exception {
    assertThatThrownBy(
            () ->
                ConditionTreeParser.parse(
                    json(
                        "{\"fact\":\"X\",\"operator\":\"GREATER_THAN\",\"value\":1,\"threshold\":\"T\"}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsANonExistsOperatorWithNeitherValueNorThreshold() throws Exception {
    assertThatThrownBy(
            () -> ConditionTreeParser.parse(json("{\"fact\":\"X\",\"operator\":\"GREATER_THAN\"}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void existsRequiresNoComparand() throws Exception {
    ConditionNode node =
        ConditionTreeParser.parse(json("{\"fact\":\"X\",\"operator\":\"EXISTS\"}"));
    assertThat(node).isInstanceOf(LeafCondition.class);
  }

  @Test
  void rejectsAnUnknownLeafField() throws Exception {
    assertThatThrownBy(
            () ->
                ConditionTreeParser.parse(
                    json("{\"fact\":\"X\",\"operator\":\"EXISTS\",\"unknownField\":true}")))
        .isInstanceOf(ConditionTreeParseException.class);
  }

  @Test
  void rejectsExcessiveNesting() throws Exception {
    StringBuilder deep = new StringBuilder();
    for (int i = 0; i < 12; i++) {
      deep.append("{\"not\":");
    }
    deep.append("{\"fact\":\"X\",\"operator\":\"EXISTS\"}");
    deep.append("}".repeat(12));

    assertThatThrownBy(() -> ConditionTreeParser.parse(json(deep.toString())))
        .isInstanceOf(ConditionTreeParseException.class);
  }
}
