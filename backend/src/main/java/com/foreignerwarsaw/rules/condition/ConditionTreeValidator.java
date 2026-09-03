package com.foreignerwarsaw.rules.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.procedure.threshold.ThresholdRepository;
import com.foreignerwarsaw.reference.country.CountryGroupRepository;
import com.foreignerwarsaw.rules.evaluation.FactDefinition;
import com.foreignerwarsaw.rules.evaluation.FactRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semantic validation of a condition tree (brief §23) - everything {@link ConditionTreeParser}
 * can't check because it needs database access: every referenced {@code fact} is known to the
 * {@link FactRegistry}, every operator is valid for that fact's type, every {@code threshold} code
 * names a real {@code Threshold}, and every {@code IS_MEMBER_OF_COUNTRY_GROUP}/{@code
 * IS_NOT_MEMBER_OF_COUNTRY_GROUP} leaf names a real {@code CountryGroup} code. Called by {@code
 * RulePublishingService} before a version leaves {@code DRAFT} - never by {@link
 * ConditionTreeParser}, which stays purely structural and reusable without a database.
 */
@Service
public class ConditionTreeValidator {

  private final FactRegistry factRegistry;
  private final ThresholdRepository thresholdRepository;
  private final CountryGroupRepository countryGroupRepository;
  private final ObjectMapper objectMapper;

  public ConditionTreeValidator(
      FactRegistry factRegistry,
      ThresholdRepository thresholdRepository,
      CountryGroupRepository countryGroupRepository,
      ObjectMapper objectMapper) {
    this.factRegistry = factRegistry;
    this.thresholdRepository = thresholdRepository;
    this.countryGroupRepository = countryGroupRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * @throws ConditionTreeValidationException listing every structural or semantic problem found,
   *     never just the first
   */
  @Transactional(readOnly = true)
  public void validate(String conditionTreeJson) {
    ConditionNode tree;
    try {
      JsonNode root = objectMapper.readTree(conditionTreeJson);
      tree = ConditionTreeParser.parse(root);
    } catch (Exception e) {
      throw new ConditionTreeValidationException(
          List.of("Malformed condition tree: " + e.getMessage()));
    }

    List<String> problems = new ArrayList<>();
    validateNode(tree, "root", problems);
    if (!problems.isEmpty()) {
      throw new ConditionTreeValidationException(problems);
    }
  }

  private void validateNode(ConditionNode node, String path, List<String> problems) {
    if (node instanceof AllNode all) {
      List<ConditionNode> children = all.children();
      for (int i = 0; i < children.size(); i++) {
        validateNode(children.get(i), path + ".all[" + i + "]", problems);
      }
    } else if (node instanceof AnyNode any) {
      List<ConditionNode> children = any.children();
      for (int i = 0; i < children.size(); i++) {
        validateNode(children.get(i), path + ".any[" + i + "]", problems);
      }
    } else if (node instanceof NotNode not) {
      validateNode(not.child(), path + ".not", problems);
    } else if (node instanceof LeafCondition leaf) {
      validateLeaf(leaf, path, problems);
    }
  }

  private void validateLeaf(LeafCondition leaf, String path, List<String> problems) {
    Optional<FactDefinition> factDefinition = factRegistry.find(leaf.fact());
    if (factDefinition.isEmpty()) {
      problems.add(path + ": unknown fact \"" + leaf.fact() + "\"");
      return;
    }
    FactDefinition fact = factDefinition.get();
    if (!fact.allowedOperators().contains(leaf.operator())) {
      problems.add(
          path
              + ": operator "
              + leaf.operator()
              + " is not valid for fact \""
              + leaf.fact()
              + "\" ("
              + fact.valueType()
              + ")");
    }

    if (leaf.threshold() != null
        && thresholdRepository.findByCodeIgnoreCase(leaf.threshold()).isEmpty()) {
      problems.add(path + ": unknown threshold code \"" + leaf.threshold() + "\"");
    }

    boolean isCountryGroupOperator =
        leaf.operator() == ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP
            || leaf.operator() == ComparisonOperator.IS_NOT_MEMBER_OF_COUNTRY_GROUP;
    if (isCountryGroupOperator) {
      if (leaf.value() == null || !leaf.value().isTextual()) {
        problems.add(
            path
                + ": "
                + leaf.operator()
                + " requires a textual \"value\" naming the country group code");
      } else if (countryGroupRepository.findByCode(leaf.value().asText()).isEmpty()) {
        problems.add(path + ": unknown country group code \"" + leaf.value().asText() + "\"");
      }
    }
  }
}
