package com.foreignerwarsaw.rules.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.common.evaluation.ConditionEvaluator;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.reference.country.CountryClassificationService;
import com.foreignerwarsaw.rules.condition.AllNode;
import com.foreignerwarsaw.rules.condition.AnyNode;
import com.foreignerwarsaw.rules.condition.ConditionNode;
import com.foreignerwarsaw.rules.condition.ConditionTreeParser;
import com.foreignerwarsaw.rules.condition.LeafCondition;
import com.foreignerwarsaw.rules.condition.NotNode;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The core condition-tree walker (brief §30-§36): evaluates one {@link RuleVersion}'s parsed {@link
 * ConditionNode} tree against one {@link AssessmentFacts} snapshot, on one {@code evaluationDate},
 * producing a fully-traced {@link RuleEvaluationResult}. Deterministic and side-effect-free (brief
 * §1/§2) - never calls an LLM, never writes to the database, and the same inputs always produce the
 * same output.
 *
 * <p>Reuses Phase 5's {@link ConditionEvaluator} for the actual typed comparison once both sides of
 * a leaf are known and present - this class owns only the layer {@code ConditionEvaluator} never
 * needed: MISSING-fact short-circuiting, threshold/country-group resolution, ERROR wrapping of
 * configuration problems, and the ALL/ANY/NOT combination semantics below (brief §30-§31):
 *
 * <ul>
 *   <li>{@code ALL} = {@code ERROR} if any child is {@code ERROR}, else {@code FAIL} if any child
 *       is {@code FAIL}, else {@code MISSING} if any child is {@code MISSING}, else {@code PASS}.
 *   <li>{@code ANY} = {@code PASS} if any child is {@code PASS}, else {@code ERROR} if any child is
 *       {@code ERROR}, else {@code MISSING} if any child is {@code MISSING}, else {@code FAIL}.
 *   <li>{@code NOT} inverts {@code PASS}/{@code FAIL}; {@code MISSING}/{@code ERROR} pass through
 *       unchanged - negating "I don't know yet" can never manufacture a known answer (brief §31).
 * </ul>
 */
@Service
public class RuleEvaluator {

  /**
   * Bumped whenever this class's evaluation semantics change (brief §54) - lets a stored historical
   * {@link RuleEvaluationBundle} be told apart from one a content-only republish would produce,
   * distinct from {@link RuleVersion#getConditionSchemaVersion()} which tracks the condition tree's
   * own JSON shape.
   */
  public static final String ENGINE_VERSION = "1";

  private final FactResolver factResolver;
  private final ThresholdService thresholdService;
  private final CountryClassificationService countryClassificationService;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final ObjectMapper objectMapper;

  public RuleEvaluator(
      FactResolver factResolver,
      ThresholdService thresholdService,
      CountryClassificationService countryClassificationService,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      ObjectMapper objectMapper) {
    this.factResolver = factResolver;
    this.thresholdService = thresholdService;
    this.countryClassificationService = countryClassificationService;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * A parse failure or an unexpected exception anywhere in the tree never propagates out of this
   * method (brief §64: a broken rule must never crash an assessment) - it is captured as a single
   * root-level {@code ERROR} trace instead, and the whole rule evaluates to {@link
   * RuleEvaluationStatus#ERROR}.
   */
  @Transactional(readOnly = true)
  public RuleEvaluationResult evaluate(
      RuleVersion ruleVersion, AssessmentFacts facts, java.time.LocalDate evaluationDate) {
    Rule rule = ruleVersion.getRule();
    List<ConditionTrace> passed = new ArrayList<>();
    List<ConditionTrace> failed = new ArrayList<>();
    List<ConditionTrace> missing = new ArrayList<>();
    List<ConditionTrace> errors = new ArrayList<>();
    Set<String> missingFacts = new LinkedHashSet<>();
    List<ThresholdUsage> thresholdsUsed = new ArrayList<>();

    ConditionResult rootResult =
        evaluateTree(
            ruleVersion.getConditionTree(),
            facts,
            evaluationDate,
            ruleVersion.getExplanationKey(),
            passed,
            failed,
            missing,
            errors,
            missingFacts,
            thresholdsUsed);

    List<UUID> sourceIds =
        ruleVersionSourceRepository.findByRuleVersion_Id(ruleVersion.getId()).stream()
            .map(source -> source.getOfficialSource().getId())
            .toList();

    return new RuleEvaluationResult(
        rule.getId(),
        rule.getCode(),
        rule.getRuleType(),
        ruleVersion.getId(),
        ruleVersion.getVersionNumber(),
        rule.getTargetType(),
        rule.getTargetCode(),
        evaluationDate,
        toStatus(rootResult),
        List.copyOf(passed),
        List.copyOf(failed),
        List.copyOf(missing),
        List.copyOf(errors),
        Set.copyOf(missingFacts),
        List.copyOf(thresholdsUsed),
        sourceIds,
        ruleVersion.getExplanationKey());
  }

  /**
   * A dry-run evaluation of a raw, not-yet-persisted condition tree (brief §113) - rule authoring
   * tooling and tests call this to see PASS/FAIL/MISSING/ERROR tracing before ever saving a {@link
   * RuleVersion}. The returned envelope carries no rule identity ({@code ruleId}/{@code
   * ruleVersionId} are {@code null}, {@code ruleCode} is {@code "PREVIEW"}) and no source
   * provenance - never mistake this for a real, publishable evaluation result.
   */
  @Transactional(readOnly = true)
  public RuleEvaluationResult previewEvaluate(
      String conditionTreeJson,
      String explanationKey,
      AssessmentFacts facts,
      java.time.LocalDate evaluationDate) {
    List<ConditionTrace> passed = new ArrayList<>();
    List<ConditionTrace> failed = new ArrayList<>();
    List<ConditionTrace> missing = new ArrayList<>();
    List<ConditionTrace> errors = new ArrayList<>();
    Set<String> missingFacts = new LinkedHashSet<>();
    List<ThresholdUsage> thresholdsUsed = new ArrayList<>();

    ConditionResult rootResult =
        evaluateTree(
            conditionTreeJson,
            facts,
            evaluationDate,
            explanationKey,
            passed,
            failed,
            missing,
            errors,
            missingFacts,
            thresholdsUsed);

    return new RuleEvaluationResult(
        null,
        "PREVIEW",
        null,
        null,
        0,
        null,
        null,
        evaluationDate,
        toStatus(rootResult),
        List.copyOf(passed),
        List.copyOf(failed),
        List.copyOf(missing),
        List.copyOf(errors),
        Set.copyOf(missingFacts),
        List.copyOf(thresholdsUsed),
        List.of(),
        explanationKey);
  }

  /**
   * Parses and walks one condition tree, routing any parse or evaluation failure into a single
   * root-level {@code ERROR} trace rather than throwing (brief §64) - shared by {@link #evaluate}
   * and {@link #previewEvaluate} so the two never diverge in how a broken tree is handled.
   */
  private ConditionResult evaluateTree(
      String conditionTreeJson,
      AssessmentFacts facts,
      java.time.LocalDate evaluationDate,
      String explanationKeyForRootError,
      List<ConditionTrace> passed,
      List<ConditionTrace> failed,
      List<ConditionTrace> missing,
      List<ConditionTrace> errors,
      Set<String> missingFacts,
      List<ThresholdUsage> thresholdsUsed) {
    try {
      JsonNode root = objectMapper.readTree(conditionTreeJson);
      ConditionNode tree = ConditionTreeParser.parse(root);
      return evaluateNode(
          tree,
          facts,
          evaluationDate,
          "root",
          passed,
          failed,
          missing,
          errors,
          missingFacts,
          thresholdsUsed);
    } catch (Exception e) {
      errors.add(
          new ConditionTrace(
              null,
              "root",
              null,
              null,
              ConditionResult.ERROR,
              explanationKeyForRootError,
              "Failed to parse or evaluate condition tree: " + e.getMessage()));
      return ConditionResult.ERROR;
    }
  }

  private ConditionResult evaluateNode(
      ConditionNode node,
      AssessmentFacts facts,
      java.time.LocalDate evaluationDate,
      String path,
      List<ConditionTrace> passed,
      List<ConditionTrace> failed,
      List<ConditionTrace> missing,
      List<ConditionTrace> errors,
      Set<String> missingFacts,
      List<ThresholdUsage> thresholdsUsed) {
    if (node instanceof AllNode all) {
      List<ConditionResult> results = new ArrayList<>();
      List<ConditionNode> children = all.children();
      for (int i = 0; i < children.size(); i++) {
        results.add(
            evaluateNode(
                children.get(i),
                facts,
                evaluationDate,
                path + ".all[" + i + "]",
                passed,
                failed,
                missing,
                errors,
                missingFacts,
                thresholdsUsed));
      }
      return combineAll(results);
    }
    if (node instanceof AnyNode any) {
      List<ConditionResult> results = new ArrayList<>();
      List<ConditionNode> children = any.children();
      for (int i = 0; i < children.size(); i++) {
        results.add(
            evaluateNode(
                children.get(i),
                facts,
                evaluationDate,
                path + ".any[" + i + "]",
                passed,
                failed,
                missing,
                errors,
                missingFacts,
                thresholdsUsed));
      }
      return combineAny(results);
    }
    if (node instanceof NotNode not) {
      ConditionResult child =
          evaluateNode(
              not.child(),
              facts,
              evaluationDate,
              path + ".not",
              passed,
              failed,
              missing,
              errors,
              missingFacts,
              thresholdsUsed);
      return invert(child);
    }
    if (node instanceof LeafCondition leaf) {
      return evaluateLeaf(
          leaf,
          facts,
          evaluationDate,
          path,
          passed,
          failed,
          missing,
          errors,
          missingFacts,
          thresholdsUsed);
    }
    throw new IllegalStateException("Unknown condition node type: " + node.getClass());
  }

  private ConditionResult evaluateLeaf(
      LeafCondition leaf,
      AssessmentFacts facts,
      java.time.LocalDate evaluationDate,
      String path,
      List<ConditionTrace> passed,
      List<ConditionTrace> failed,
      List<ConditionTrace> missing,
      List<ConditionTrace> errors,
      Set<String> missingFacts,
      List<ThresholdUsage> thresholdsUsed) {
    String conditionCode = leaf.code() != null ? leaf.code() : path;

    Object actualValue;
    try {
      actualValue = factResolver.resolve(leaf.fact(), facts, evaluationDate);
    } catch (Exception e) {
      errors.add(
          new ConditionTrace(
              conditionCode,
              path,
              leaf.fact(),
              leaf.operator(),
              ConditionResult.ERROR,
              leaf.explanationKey(),
              "Failed to resolve fact " + leaf.fact() + ": " + e.getMessage()));
      return ConditionResult.ERROR;
    }

    boolean existsFamily =
        leaf.operator() == ComparisonOperator.EXISTS
            || leaf.operator() == ComparisonOperator.NOT_EXISTS;
    if (!existsFamily && isAbsent(actualValue)) {
      missingFacts.add(leaf.fact());
      missing.add(
          new ConditionTrace(
              conditionCode,
              path,
              leaf.fact(),
              leaf.operator(),
              ConditionResult.MISSING,
              leaf.explanationKey(),
              leaf.fact() + " has not been answered yet"));
      return ConditionResult.MISSING;
    }

    try {
      boolean satisfied;
      if (leaf.operator() == ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP
          || leaf.operator() == ComparisonOperator.IS_NOT_MEMBER_OF_COUNTRY_GROUP) {
        satisfied = evaluateCountryGroupMembership(leaf, actualValue, evaluationDate);
      } else if (leaf.threshold() != null) {
        ThresholdVersion thresholdVersion =
            thresholdService
                .findActiveVersion(leaf.threshold(), evaluationDate)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "No published threshold version active for \""
                                + leaf.threshold()
                                + "\" on "
                                + evaluationDate));
        if (thresholdVersion.getValue() == null) {
          throw new IllegalStateException(
              "Threshold \"" + leaf.threshold() + "\" has no numeric value to compare against");
        }
        JsonNode comparand = DecimalNode.valueOf(thresholdVersion.getValue());
        satisfied = ConditionEvaluator.evaluate(leaf.operator(), actualValue, comparand);
        thresholdsUsed.add(
            new ThresholdUsage(
                leaf.threshold(),
                thresholdVersion.getId(),
                thresholdVersion.getValue(),
                thresholdVersion.getEffectiveFrom()));
      } else {
        satisfied = ConditionEvaluator.evaluate(leaf.operator(), actualValue, leaf.value());
      }

      ConditionResult result = satisfied ? ConditionResult.PASS : ConditionResult.FAIL;
      ConditionTrace trace =
          new ConditionTrace(
              conditionCode,
              path,
              leaf.fact(),
              leaf.operator(),
              result,
              leaf.explanationKey(),
              describe(leaf, actualValue, result));
      (result == ConditionResult.PASS ? passed : failed).add(trace);
      return result;
    } catch (Exception e) {
      errors.add(
          new ConditionTrace(
              conditionCode,
              path,
              leaf.fact(),
              leaf.operator(),
              ConditionResult.ERROR,
              leaf.explanationKey(),
              "Evaluation error: " + e.getMessage()));
      return ConditionResult.ERROR;
    }
  }

  /**
   * {@code leaf.value()} carries the target group code (e.g. {@code "EU_MEMBER"}) as a JSON string
   * - {@code ConditionEvaluator} never sees this operator (brief §41/§68); it is intercepted here
   * and delegated to the live {@link CountryClassificationService} instead.
   */
  private boolean evaluateCountryGroupMembership(
      LeafCondition leaf, Object actualValue, java.time.LocalDate evaluationDate) {
    if (!(actualValue instanceof String countryCode)) {
      throw new IllegalStateException(leaf.fact() + " did not resolve to a country code string");
    }
    if (leaf.value() == null || !leaf.value().isTextual()) {
      throw new IllegalStateException(
          leaf.operator() + " requires a textual \"value\" naming the country group code");
    }
    String groupCode = leaf.value().asText();
    boolean isMember =
        countryClassificationService.isMember(countryCode, groupCode, evaluationDate);
    return leaf.operator() == ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP ? isMember : !isMember;
  }

  private boolean isAbsent(Object value) {
    if (value == null) {
      return true;
    }
    return value instanceof Collection<?> collection && collection.isEmpty();
  }

  private String describe(LeafCondition leaf, Object actualValue, ConditionResult result) {
    String comparand =
        leaf.threshold() != null ? "threshold:" + leaf.threshold() : String.valueOf(leaf.value());
    return leaf.fact()
        + "="
        + actualValue
        + " "
        + leaf.operator()
        + " "
        + comparand
        + " -> "
        + result;
  }

  private ConditionResult combineAll(List<ConditionResult> results) {
    if (results.contains(ConditionResult.ERROR)) {
      return ConditionResult.ERROR;
    }
    if (results.contains(ConditionResult.FAIL)) {
      return ConditionResult.FAIL;
    }
    if (results.contains(ConditionResult.MISSING)) {
      return ConditionResult.MISSING;
    }
    return ConditionResult.PASS;
  }

  private ConditionResult combineAny(List<ConditionResult> results) {
    if (results.contains(ConditionResult.PASS)) {
      return ConditionResult.PASS;
    }
    if (results.contains(ConditionResult.ERROR)) {
      return ConditionResult.ERROR;
    }
    if (results.contains(ConditionResult.MISSING)) {
      return ConditionResult.MISSING;
    }
    return ConditionResult.FAIL;
  }

  private ConditionResult invert(ConditionResult result) {
    return switch (result) {
      case PASS -> ConditionResult.FAIL;
      case FAIL -> ConditionResult.PASS;
      case MISSING -> ConditionResult.MISSING;
      case ERROR -> ConditionResult.ERROR;
    };
  }

  private RuleEvaluationStatus toStatus(ConditionResult result) {
    return switch (result) {
      case PASS -> RuleEvaluationStatus.SATISFIED;
      case FAIL -> RuleEvaluationStatus.NOT_SATISFIED;
      case MISSING -> RuleEvaluationStatus.INDETERMINATE;
      case ERROR -> RuleEvaluationStatus.ERROR;
    };
  }
}
