package com.foreignerwarsaw.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class PublicationStateMachineTest {

  @Test
  void forwardChain_draftThroughArchived_isAllowedOneStepAtATime() {
    assertThat(
            PublicationStateMachine.isAllowed(PublicationStatus.DRAFT, PublicationStatus.IN_REVIEW))
        .isTrue();
    assertThat(
            PublicationStateMachine.isAllowed(
                PublicationStatus.IN_REVIEW, PublicationStatus.APPROVED))
        .isTrue();
    assertThat(
            PublicationStateMachine.isAllowed(
                PublicationStatus.APPROVED, PublicationStatus.PUBLISHED))
        .isTrue();
    assertThat(
            PublicationStateMachine.isAllowed(
                PublicationStatus.PUBLISHED, PublicationStatus.ARCHIVED))
        .isTrue();
  }

  @Test
  void justifiedReverseTransitions_areAllowed() {
    assertThat(
            PublicationStateMachine.isAllowed(PublicationStatus.IN_REVIEW, PublicationStatus.DRAFT))
        .isTrue();
    assertThat(
            PublicationStateMachine.isAllowed(PublicationStatus.APPROVED, PublicationStatus.DRAFT))
        .isTrue();
  }

  @Test
  void draftCannotSkipStraightToPublished() {
    assertThat(
            PublicationStateMachine.isAllowed(PublicationStatus.DRAFT, PublicationStatus.PUBLISHED))
        .isFalse();
  }

  @Test
  void archivedIsTerminal_noTransitionOutOfItIsAllowed() {
    for (PublicationStatus target : PublicationStatus.values()) {
      assertThat(PublicationStateMachine.isAllowed(PublicationStatus.ARCHIVED, target)).isFalse();
    }
  }

  @Test
  void everyStatusPairNotExplicitlyAllowed_isRejected() {
    // Exhaustive sweep, not just the named cases above - proves no arbitrary jump
    // (e.g. DRAFT -> APPROVED, PUBLISHED -> DRAFT) was accidentally left open.
    EnumSet<PublicationStatus> allowedFromDraft = EnumSet.of(PublicationStatus.IN_REVIEW);
    EnumSet<PublicationStatus> allowedFromInReview =
        EnumSet.of(PublicationStatus.APPROVED, PublicationStatus.DRAFT);
    EnumSet<PublicationStatus> allowedFromApproved =
        EnumSet.of(PublicationStatus.PUBLISHED, PublicationStatus.DRAFT);
    EnumSet<PublicationStatus> allowedFromPublished = EnumSet.of(PublicationStatus.ARCHIVED);

    for (PublicationStatus target : PublicationStatus.values()) {
      assertThat(PublicationStateMachine.isAllowed(PublicationStatus.DRAFT, target))
          .isEqualTo(allowedFromDraft.contains(target));
      assertThat(PublicationStateMachine.isAllowed(PublicationStatus.IN_REVIEW, target))
          .isEqualTo(allowedFromInReview.contains(target));
      assertThat(PublicationStateMachine.isAllowed(PublicationStatus.APPROVED, target))
          .isEqualTo(allowedFromApproved.contains(target));
      assertThat(PublicationStateMachine.isAllowed(PublicationStatus.PUBLISHED, target))
          .isEqualTo(allowedFromPublished.contains(target));
    }
  }

  @Test
  void requireAllowed_throwsApiExceptionWithConflictStatusOnAnInvalidTransition() {
    assertThatThrownBy(
            () ->
                PublicationStateMachine.requireAllowed(
                    PublicationStatus.DRAFT, PublicationStatus.PUBLISHED))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
              assertThat(apiException.getStatus())
                  .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
            });
  }

  @Test
  void requireAllowed_doesNotThrowOnAValidTransition() {
    org.assertj.core.api.Assertions.assertThatCode(
            () ->
                PublicationStateMachine.requireAllowed(
                    PublicationStatus.DRAFT, PublicationStatus.IN_REVIEW))
        .doesNotThrowAnyException();
  }
}
