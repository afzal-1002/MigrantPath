package com.foreignerwarsaw.usercase.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.common.web.ApiException;
import org.junit.jupiter.api.Test;

/** Whole-case status workflow (brief §22/§130, docs/cases/CASE_STATUS_WORKFLOW.md). */
class UserCaseStatusTransitionsTest {

  @Test
  void theBaselineHappyPathIsAllowedStepByStep() {
    assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.DRAFT, UserCaseStatus.PREPARING))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.PREPARING, UserCaseStatus.READY_TO_SUBMIT))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.READY_TO_SUBMIT, UserCaseStatus.SUBMITTED))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(UserCaseStatus.SUBMITTED, UserCaseStatus.WAITING))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.WAITING, UserCaseStatus.DECISION_RECEIVED))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.DECISION_RECEIVED, UserCaseStatus.APPROVED))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(UserCaseStatus.APPROVED, UserCaseStatus.COMPLETED))
        .isTrue();
  }

  @Test
  void waitingAndAdditionalDocumentsRequiredCycleBackAndForth() {
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.WAITING, UserCaseStatus.ADDITIONAL_DOCUMENTS_REQUIRED))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.ADDITIONAL_DOCUMENTS_REQUIRED, UserCaseStatus.WAITING))
        .isTrue();
  }

  @Test
  void rejectedCanAppealOrConcludeAsCompleted() {
    assertThat(
            UserCaseStatusTransitions.isAllowed(
                UserCaseStatus.DECISION_RECEIVED, UserCaseStatus.REJECTED))
        .isTrue();
    assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.REJECTED, UserCaseStatus.APPEAL))
        .isTrue();
    assertThat(
            UserCaseStatusTransitions.isAllowed(UserCaseStatus.REJECTED, UserCaseStatus.COMPLETED))
        .isTrue();
  }

  @Test
  void cancelledIsReachableFromEveryNonTerminalStatus() {
    for (UserCaseStatus status : UserCaseStatus.values()) {
      if (status == UserCaseStatus.COMPLETED || status == UserCaseStatus.CANCELLED) {
        continue;
      }
      assertThat(UserCaseStatusTransitions.isAllowed(status, UserCaseStatus.CANCELLED))
          .describedAs("%s -> CANCELLED", status)
          .isTrue();
    }
  }

  @Test
  void cancelledAndCompletedAreTerminal_noOutgoingTransitions() {
    for (UserCaseStatus target : UserCaseStatus.values()) {
      assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.CANCELLED, target)).isFalse();
      assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.COMPLETED, target)).isFalse();
    }
  }

  @Test
  void arbitrarySkipAheadJumpsAreRejected() {
    assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.DRAFT, UserCaseStatus.APPROVED))
        .isFalse();
    assertThat(UserCaseStatusTransitions.isAllowed(UserCaseStatus.DRAFT, UserCaseStatus.COMPLETED))
        .isFalse();
  }

  @Test
  void requireAllowedThrowsApiExceptionWithTheStableCode() {
    assertThatThrownBy(
            () ->
                UserCaseStatusTransitions.requireAllowed(
                    UserCaseStatus.DRAFT, UserCaseStatus.APPROVED))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getCode())
                    .isEqualTo("CASE_STATUS_TRANSITION_INVALID"));
  }
}
