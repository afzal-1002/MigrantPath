package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The one authoritative set of legal {@link UserCaseStatus} transitions (brief §22/§130, full table
 * in docs/cases/CASE_STATUS_WORKFLOW.md) - mirrors {@code PublicationStateMachine}'s pattern
 * exactly (one shared table, never per-call-site branching). {@code CANCELLED} is reachable from
 * every non-terminal status (a user may abandon a case at any point before it concludes) but is
 * itself terminal. {@code DRAFT -> APPROVED} and similar "skip the entire workflow" jumps are
 * deliberately not allowed - brief §59's "do not allow arbitrary" transitions.
 */
public final class UserCaseStatusTransitions {

  private static final Map<UserCaseStatus, Set<UserCaseStatus>> ALLOWED =
      Map.ofEntries(
          Map.entry(
              UserCaseStatus.DRAFT, Set.of(UserCaseStatus.PREPARING, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.PREPARING,
              Set.of(UserCaseStatus.READY_TO_SUBMIT, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.READY_TO_SUBMIT,
              Set.of(UserCaseStatus.PREPARING, UserCaseStatus.SUBMITTED, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.SUBMITTED, Set.of(UserCaseStatus.WAITING, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.WAITING,
              Set.of(
                  UserCaseStatus.ADDITIONAL_DOCUMENTS_REQUIRED,
                  UserCaseStatus.DECISION_RECEIVED,
                  UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.ADDITIONAL_DOCUMENTS_REQUIRED,
              Set.of(UserCaseStatus.WAITING, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.DECISION_RECEIVED,
              Set.of(UserCaseStatus.APPROVED, UserCaseStatus.REJECTED, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.APPROVED, Set.of(UserCaseStatus.COMPLETED, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.REJECTED,
              Set.of(UserCaseStatus.APPEAL, UserCaseStatus.COMPLETED, UserCaseStatus.CANCELLED)),
          Map.entry(
              UserCaseStatus.APPEAL,
              Set.of(
                  UserCaseStatus.DECISION_RECEIVED,
                  UserCaseStatus.COMPLETED,
                  UserCaseStatus.CANCELLED)),
          Map.entry(UserCaseStatus.COMPLETED, Set.of()),
          Map.entry(UserCaseStatus.CANCELLED, Set.of()));

  private UserCaseStatusTransitions() {}

  public static boolean isAllowed(UserCaseStatus from, UserCaseStatus to) {
    return ALLOWED.getOrDefault(from, Set.of()).contains(to);
  }

  public static void requireAllowed(UserCaseStatus from, UserCaseStatus to) {
    if (!isAllowed(from, to)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_STATUS_TRANSITION_INVALID",
          "Cannot transition a case from %s to %s".formatted(from, to));
    }
  }
}
