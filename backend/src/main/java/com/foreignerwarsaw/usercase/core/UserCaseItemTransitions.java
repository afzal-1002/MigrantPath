package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Checklist-item transition rules (brief §60/§61/§130) - separate from {@link
 * UserCaseStatusTransitions} (the whole-case status machine). Deliberately permissive among the
 * user-directly-settable statuses (a user may freely move a step/document back and forth as they
 * work through a case) but rejects a transition into a status reserved for a future engine this
 * phase doesn't build ({@code BLOCKED}/{@code NOT_APPLICABLE} for steps - brief §10/§72; {@code
 * NOT_APPLICABLE} for documents - brief §12, only ever set by the snapshot builder itself).
 */
public final class UserCaseItemTransitions {

  private static final Set<UserCaseStepStatus> USER_SETTABLE_STEP_STATUSES =
      Set.of(
          UserCaseStepStatus.NOT_STARTED,
          UserCaseStepStatus.IN_PROGRESS,
          UserCaseStepStatus.COMPLETED,
          UserCaseStepStatus.SKIPPED);

  private static final Set<UserCaseDocumentStatus> USER_SETTABLE_DOCUMENT_STATUSES =
      Set.of(
          UserCaseDocumentStatus.NOT_STARTED,
          UserCaseDocumentStatus.MISSING,
          UserCaseDocumentStatus.IN_PROGRESS,
          UserCaseDocumentStatus.READY);

  private UserCaseItemTransitions() {}

  public static void requireAllowedStep(UserCaseStepStatus from, UserCaseStepStatus to) {
    if (from == UserCaseStepStatus.NOT_APPLICABLE) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_NOT_APPLICABLE",
          "This step is not applicable to this case");
    }
    if (!USER_SETTABLE_STEP_STATUSES.contains(to)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_TRANSITION_INVALID",
          "%s is not a status a user can set directly".formatted(to));
    }
  }

  public static void requireAllowedDocument(
      UserCaseDocumentStatus from, UserCaseDocumentStatus to) {
    if (from == UserCaseDocumentStatus.NOT_APPLICABLE) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_NOT_APPLICABLE",
          "This document is not applicable to this case");
    }
    if (!USER_SETTABLE_DOCUMENT_STATUSES.contains(to)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_TRANSITION_INVALID",
          "%s is not a status a user can set directly".formatted(to));
    }
  }
}
