package com.foreignerwarsaw.questionnaire.visibility;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/**
 * Rejects a cyclic {@link QuestionDependency} graph (brief §68) - run once at QuestionnaireVersion
 * publish time (never at read time; the cost of a full graph walk belongs to the rare admin action,
 * not every assessment request). A gated question depending, directly or transitively, on itself
 * would make {@link QuestionVisibilityService} unable to resolve a stable answer.
 */
public final class DependencyGraphValidator {

  private DependencyGraphValidator() {}

  public static void requireAcyclic(List<QuestionDependency> dependencies) {
    Map<UUID, List<UUID>> edges =
        dependencies.stream()
            .collect(
                Collectors.groupingBy(
                    d -> d.getQuestionnaireQuestion().getId(),
                    Collectors.mapping(
                        d -> d.getDependsOnQuestionnaireQuestion().getId(), Collectors.toList())));

    Set<UUID> visited = new HashSet<>();
    Set<UUID> inProgress = new HashSet<>();
    for (UUID node : edges.keySet()) {
      if (!visited.contains(node) && hasCycle(node, edges, visited, inProgress)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "CYCLIC_QUESTION_DEPENDENCY",
            "Question dependency graph contains a cycle involving questionnaire question " + node);
      }
    }
  }

  private static boolean hasCycle(
      UUID node, Map<UUID, List<UUID>> edges, Set<UUID> visited, Set<UUID> inProgress) {
    if (inProgress.contains(node)) {
      return true;
    }
    if (visited.contains(node)) {
      return false;
    }
    inProgress.add(node);
    for (UUID next : edges.getOrDefault(node, List.of())) {
      if (hasCycle(next, edges, visited, inProgress)) {
        return true;
      }
    }
    inProgress.remove(node);
    visited.add(node);
    return false;
  }
}
