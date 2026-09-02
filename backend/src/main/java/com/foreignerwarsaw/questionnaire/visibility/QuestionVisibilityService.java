package com.foreignerwarsaw.questionnaire.visibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.evaluation.ConditionEvaluator;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.VisibilityCombinator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Deterministic "should this question be shown right now" engine (brief §67) - no AI, no
 * immigration-eligibility conclusions, purely a function of the current answer set and the {@link
 * QuestionDependency} graph. Reused by every read path that needs "what's currently visible"
 * (assessment detail, answer validation, progress, completion) so there is exactly one visibility
 * computation in the system, never a slightly-different one per call site.
 *
 * <p>Dependencies may chain (a question can depend on another gated question), so visibility is
 * resolved depth-first with memoization rather than a single flat pass - a question whose own
 * prerequisite is not currently visible is evaluated against a "no answer" value (brief: a hidden
 * question's stale answer must never influence anything downstream), which {@link
 * ConditionEvaluator} already treats correctly (EXISTS-style operators false, NOT_EXISTS true).
 * Publish-time validation ({@code DependencyGraphValidator}) guarantees the graph is acyclic; the
 * defensive check here exists only to fail loudly, not silently loop, if that guarantee is ever
 * violated.
 */
@Service
public class QuestionVisibilityService {

  private final ObjectMapper objectMapper;

  public QuestionVisibilityService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param questions every QuestionnaireQuestion in the version
   * @param dependencies every QuestionDependency in the version
   * @param answerByQuestionId the assessment's current raw answer value per stable Question id (see
   *     {@code AssessmentAnswer#logicalValue}) - only currently-held answers, regardless of their
   *     persisted {@code is_applicable} flag (this method is what computes that flag).
   * @return the set of QuestionnaireQuestion ids currently visible
   */
  public Set<UUID> computeVisibleQuestionnaireQuestionIds(
      List<QuestionnaireQuestion> questions,
      List<QuestionDependency> dependencies,
      Map<UUID, Object> answerByQuestionId) {
    Map<UUID, QuestionnaireQuestion> questionsById =
        questions.stream().collect(Collectors.toMap(QuestionnaireQuestion::getId, q -> q));
    Map<UUID, List<QuestionDependency>> dependenciesByGatedId =
        dependencies.stream()
            .collect(Collectors.groupingBy(d -> d.getQuestionnaireQuestion().getId()));

    Map<UUID, Boolean> memo = new HashMap<>();
    for (QuestionnaireQuestion question : questions) {
      resolveVisible(
          question.getId(),
          questionsById,
          dependenciesByGatedId,
          answerByQuestionId,
          memo,
          new HashSet<>());
    }
    return memo.entrySet().stream()
        .filter(Map.Entry::getValue)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private boolean resolveVisible(
      UUID questionnaireQuestionId,
      Map<UUID, QuestionnaireQuestion> questionsById,
      Map<UUID, List<QuestionDependency>> dependenciesByGatedId,
      Map<UUID, Object> answerByQuestionId,
      Map<UUID, Boolean> memo,
      Set<UUID> inProgress) {
    Boolean cached = memo.get(questionnaireQuestionId);
    if (cached != null) {
      return cached;
    }
    if (!inProgress.add(questionnaireQuestionId)) {
      throw new IllegalStateException(
          "Cyclic question dependency detected at " + questionnaireQuestionId);
    }

    List<QuestionDependency> ownDependencies =
        dependenciesByGatedId.getOrDefault(questionnaireQuestionId, List.of());
    boolean visible;
    if (ownDependencies.isEmpty()) {
      visible = true;
    } else {
      VisibilityCombinator combinator =
          questionsById.get(questionnaireQuestionId).getVisibilityCombinator();
      visible =
          combinator == VisibilityCombinator.ANY
              ? ownDependencies.stream()
                  .anyMatch(
                      d ->
                          conditionHolds(
                              d,
                              questionsById,
                              dependenciesByGatedId,
                              answerByQuestionId,
                              memo,
                              inProgress))
              : ownDependencies.stream()
                  .allMatch(
                      d ->
                          conditionHolds(
                              d,
                              questionsById,
                              dependenciesByGatedId,
                              answerByQuestionId,
                              memo,
                              inProgress));
    }

    inProgress.remove(questionnaireQuestionId);
    memo.put(questionnaireQuestionId, visible);
    return visible;
  }

  private boolean conditionHolds(
      QuestionDependency dependency,
      Map<UUID, QuestionnaireQuestion> questionsById,
      Map<UUID, List<QuestionDependency>> dependenciesByGatedId,
      Map<UUID, Object> answerByQuestionId,
      Map<UUID, Boolean> memo,
      Set<UUID> inProgress) {
    UUID sourceId = dependency.getDependsOnQuestionnaireQuestion().getId();
    boolean sourceVisible =
        resolveVisible(
            sourceId, questionsById, dependenciesByGatedId, answerByQuestionId, memo, inProgress);
    Object actualValue =
        sourceVisible
            ? answerByQuestionId.get(
                dependency.getDependsOnQuestionnaireQuestion().getQuestion().getId())
            : null;
    JsonNode expectedValue = readExpectedValue(dependency.getExpectedValue());
    return ConditionEvaluator.evaluate(dependency.getOperator(), actualValue, expectedValue);
  }

  private JsonNode readExpectedValue(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid QuestionDependency.expectedValue JSON: " + json, e);
    }
  }
}
