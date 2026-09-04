package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.observability.CaseMetrics;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.usercase.core.SnapshotRevisionReason;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventType;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevision;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic case creation (brief §78): validate -&gt; build the revision-1 snapshot -&gt; create the
 * case -&gt; log {@code CASE_CREATED} - one transaction or none of it. Idempotent per {@link
 * com.foreignerwarsaw.recommendation.core.Recommendation} (brief §53/§77): a second request for the
 * same recommendation returns the existing case rather than erroring or duplicating rows (the
 * {@code user_cases_recommendation_uq} unique index is the last-resort backstop this check makes
 * unreachable in practice).
 */
@Service
public class UserCaseCreationService {

  private final UserCaseRepository userCaseRepository;
  private final UserCaseSnapshotService snapshotService;
  private final UserCaseEventRepository eventRepository;
  private final CaseMetrics caseMetrics;
  private final Clock clock;

  public UserCaseCreationService(
      UserCaseRepository userCaseRepository,
      UserCaseSnapshotService snapshotService,
      UserCaseEventRepository eventRepository,
      CaseMetrics caseMetrics,
      Clock clock) {
    this.userCaseRepository = userCaseRepository;
    this.snapshotService = snapshotService;
    this.eventRepository = eventRepository;
    this.caseMetrics = caseMetrics;
    this.clock = clock;
  }

  /**
   * Checked by the controller before ever calling {@link CaseCreationValidator#validate}: a
   * recommendation that already has a case must return that case idempotently (brief §77), even if
   * the recommendation's own pinned {@code ProcedureVersion} has since gone stale - staleness only
   * blocks *new* case creation, it must never block returning to a case that already exists.
   */
  @Transactional(readOnly = true)
  public Optional<UserCase> findExistingCase(UUID recommendationId) {
    return userCaseRepository.findByRecommendation_Id(recommendationId);
  }

  @Transactional
  public UserCase createFromRecommendation(
      CaseCreationValidator.ValidatedRecommendation validated, User user) {
    Optional<UserCase> existing =
        userCaseRepository.findByRecommendation_Id(validated.recommendation().getId());
    if (existing.isPresent()) {
      return existing.get();
    }

    Instant now = clock.instant();
    UserCase userCase =
        userCaseRepository.save(
            UserCase.create(
                user,
                validated.recommendation(),
                validated.recommendation().getRecommendationRun().getAssessment(),
                validated.recommendation().getProcedure(),
                now));

    UserCaseSnapshotRevision revision =
        snapshotService.buildRevision(
            userCase,
            1,
            validated.procedureVersion(),
            validated.evaluationDate(),
            SnapshotRevisionReason.INITIAL,
            user,
            null,
            now);
    userCase.attachRevision(revision);

    eventRepository.save(
        new UserCaseEvent(
            userCase,
            UserCaseEventType.CASE_CREATED,
            now,
            user,
            "procedureVersion=" + validated.procedureVersion().getVersionNumber()));

    caseMetrics.recordCaseCreated();
    return userCase;
  }
}
