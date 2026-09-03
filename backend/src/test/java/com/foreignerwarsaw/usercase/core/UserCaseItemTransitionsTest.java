package com.foreignerwarsaw.usercase.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.common.web.ApiException;
import org.junit.jupiter.api.Test;

/**
 * Checklist-item transitions (brief §60/§61/§130) - distinct from the whole-case status machine.
 */
class UserCaseItemTransitionsTest {

  @Test
  void everyUserSettableStepStatusIsFreelyReachableFromAnother() {
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.NOT_STARTED, UserCaseStepStatus.IN_PROGRESS))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.IN_PROGRESS, UserCaseStepStatus.COMPLETED))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.COMPLETED, UserCaseStepStatus.NOT_STARTED))
        .doesNotThrowAnyException();
  }

  @Test
  void aStepCannotBeSetToBlockedOrNotApplicableDirectly_reservedForAFutureEngine() {
    assertThatThrownBy(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.NOT_STARTED, UserCaseStepStatus.BLOCKED))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                org.assertj.core.api.Assertions.assertThat(((ApiException) e).getCode())
                    .isEqualTo("CASE_ITEM_TRANSITION_INVALID"));
    assertThatThrownBy(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.NOT_STARTED, UserCaseStepStatus.NOT_APPLICABLE))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void aStepAlreadyNotApplicableCannotBeChangedByAUser() {
    assertThatThrownBy(
            () ->
                UserCaseItemTransitions.requireAllowedStep(
                    UserCaseStepStatus.NOT_APPLICABLE, UserCaseStepStatus.NOT_STARTED))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                org.assertj.core.api.Assertions.assertThat(((ApiException) e).getCode())
                    .isEqualTo("CASE_ITEM_NOT_APPLICABLE"));
  }

  @Test
  void everyUserSettableDocumentStatusIsFreelyReachableFromAnother() {
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedDocument(
                    UserCaseDocumentStatus.NOT_STARTED, UserCaseDocumentStatus.READY))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedDocument(
                    UserCaseDocumentStatus.READY, UserCaseDocumentStatus.IN_PROGRESS))
        .doesNotThrowAnyException();
  }

  @Test
  void aDocumentInNeedsUpdateCanBeMovedBackToReadyOnceFixed() {
    // NEEDS_UPDATE is a valid *source* state (an upgrade sets it), just never a user-settable
    // *target* (brief §36 - only the upgrade merge produces it).
    assertThatCode(
            () ->
                UserCaseItemTransitions.requireAllowedDocument(
                    UserCaseDocumentStatus.NEEDS_UPDATE, UserCaseDocumentStatus.READY))
        .doesNotThrowAnyException();
  }

  @Test
  void aUserCannotSetADocumentToNeedsUpdateOrNotApplicableDirectly() {
    assertThatThrownBy(
            () ->
                UserCaseItemTransitions.requireAllowedDocument(
                    UserCaseDocumentStatus.NOT_STARTED, UserCaseDocumentStatus.NEEDS_UPDATE))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                UserCaseItemTransitions.requireAllowedDocument(
                    UserCaseDocumentStatus.NOT_STARTED, UserCaseDocumentStatus.NOT_APPLICABLE))
        .isInstanceOf(ApiException.class);
  }
}
