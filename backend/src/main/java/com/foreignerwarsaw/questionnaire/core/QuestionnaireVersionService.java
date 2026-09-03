package com.foreignerwarsaw.questionnaire.core;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependencyRepository;
import com.foreignerwarsaw.questionnaire.option.QuestionOption;
import com.foreignerwarsaw.questionnaire.option.QuestionOptionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import com.foreignerwarsaw.questionnaire.visibility.DependencyGraphValidator;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publish-lifecycle + new-version-cloning for {@link QuestionnaireVersion} (brief §44/§45),
 * mirroring {@code ProcedurePublishingService}/{@code ProcedureVersionService}'s split of
 * responsibility: the entity itself enforces its own state machine and immutability-once-published;
 * this service owns publish-readiness validation (cycle detection) and the "close the previous
 * active version" side effect.
 *
 * <p>No REST-exposed admin editor exists for this in Phase 5 (brief §40: full admin UI is Phase 9)
 * - this service exists so a new version can be drafted/published via a fixture/migration/future
 * admin service without ever mutating a version already in use, and so the immutability guarantee
 * (brief §44) is exercised by tests, not just asserted by the seed data staying untouched.
 */
@Service
public class QuestionnaireVersionService {

  private final QuestionnaireVersionRepository questionnaireVersionRepository;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionOptionRepository questionOptionRepository;
  private final QuestionDependencyRepository questionDependencyRepository;
  private final Clock clock;

  public QuestionnaireVersionService(
      QuestionnaireVersionRepository questionnaireVersionRepository,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionOptionRepository questionOptionRepository,
      QuestionDependencyRepository questionDependencyRepository,
      Clock clock) {
    this.questionnaireVersionRepository = questionnaireVersionRepository;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionOptionRepository = questionOptionRepository;
    this.questionDependencyRepository = questionDependencyRepository;
    this.clock = clock;
  }

  /**
   * Clones {@code source}'s full question structure (QuestionnaireQuestion + QuestionOption +
   * QuestionDependency rows) into a new DRAFT version, ready for independent editing - the same
   * "full snapshot, not a diff" convention as Phase 4 content versions (docs/database/DATABASE.md
   * §0). An in-progress Assessment bound to {@code source}'s id is entirely unaffected by anything
   * done to the returned draft.
   */
  @Transactional
  public QuestionnaireVersion createDraftFrom(
      QuestionnaireVersion source, String title, String description, User actor) {
    int nextVersionNumber =
        questionnaireVersionRepository.findMaxVersionNumber(source.getQuestionnaire().getId()) + 1;
    QuestionnaireVersion draft =
        QuestionnaireVersion.draft(
            source.getQuestionnaire(), nextVersionNumber, title, description, actor);
    questionnaireVersionRepository.saveAndFlush(draft);

    List<QuestionnaireQuestion> sourceQuestions =
        questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
            source.getId());
    Map<UUID, QuestionnaireQuestion> clonedByOriginalId = new HashMap<>();
    for (QuestionnaireQuestion original : sourceQuestions) {
      QuestionnaireQuestion clone =
          QuestionnaireQuestion.create(
              draft,
              original.getQuestion(),
              original.getSectionCode(),
              original.getLabel(),
              original.getHelpText(),
              original.isRequired(),
              original.getSortOrder(),
              original.getOptionSource(),
              original.isAllowUnsure(),
              original.getVisibilityCombinator());
      questionnaireQuestionRepository.saveAndFlush(clone);
      clonedByOriginalId.put(original.getId(), clone);

      for (QuestionOption option :
          questionOptionRepository.findByQuestionnaireQuestion_IdOrderBySortOrder(
              original.getId())) {
        questionOptionRepository.saveAndFlush(
            QuestionOption.create(
                clone,
                option.getCode(),
                option.getLabel(),
                option.getDescription(),
                option.getSortOrder()));
      }
    }

    // Second pass: dependencies, once every clone exists (a dependency's source may sort after
    // its gated question).
    for (QuestionnaireQuestion original : sourceQuestions) {
      for (QuestionDependency dependency :
          questionDependencyRepository.findByQuestionnaireQuestion_Id(original.getId())) {
        questionDependencyRepository.saveAndFlush(
            QuestionDependency.create(
                clonedByOriginalId.get(original.getId()),
                clonedByOriginalId.get(dependency.getDependsOnQuestionnaireQuestion().getId()),
                dependency.getOperator(),
                dependency.getExpectedValue()));
      }
    }

    return draft;
  }

  /**
   * Publish-readiness validation (brief §68's cycle rejection) + the mechanical transition +
   * closing whichever previously-PUBLISHED version of this questionnaire is still open, exactly
   * mirroring {@code ProcedurePublishingService#publish}.
   */
  @Transactional
  public QuestionnaireVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    QuestionnaireVersion version =
        questionnaireVersionRepository
            .findByIdFetchingQuestionnaire(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "QUESTIONNAIRE_VERSION_NOT_FOUND", "Not found"));

    DependencyGraphValidator.requireAcyclic(
        questionDependencyRepository.findByQuestionnaireVersion_Id(versionId));

    for (QuestionnaireVersion published :
        questionnaireVersionRepository.findPublishedVersions(version.getQuestionnaire().getId())) {
      if (!published.getId().equals(version.getId()) && published.getEffectiveTo() == null) {
        // saveAndFlush, not save: the exclusion constraint is checked per-statement, not only at
        // commit, and Hibernate's automatic flush ordering is not guaranteed to write this UPDATE
        // before the new version's own PUBLISHED update below - without forcing it out now, the
        // two can flush in the wrong order and the constraint rejects the new version's insert
        // against the *old*, still-open-ended range. Same bug/fix as {@code
        // ProcedurePublishingService#publish} - see that Javadoc for the full story.
        published.closeEffectiveTo(effectiveFrom);
        questionnaireVersionRepository.saveAndFlush(published);
      }
    }

    version.markPublished(actor, Instant.now(clock), effectiveFrom);
    return version;
  }

  public boolean isDraftStage(QuestionnaireVersion version) {
    return version.getStatus() == PublicationStatus.DRAFT
        || version.getStatus() == PublicationStatus.IN_REVIEW
        || version.getStatus() == PublicationStatus.APPROVED;
  }

  /**
   * Phase 9 additions (brief §49/§50) - {@code QuestionnaireVersionService} previously exposed only
   * {@code createDraftFrom}/{@code publish} (brief §40's "no REST-exposed admin editor exists in
   * Phase 5"); the review-workflow transitions the entity itself already supported were never
   * wrapped in a service method until now.
   */
  @Transactional
  public QuestionnaireVersion submitForReview(UUID versionId, User actor) {
    QuestionnaireVersion version = getManagedById(versionId);
    version.submitForReview(actor, Instant.now(clock));
    return version;
  }

  @Transactional
  public QuestionnaireVersion sendBackToDraft(UUID versionId) {
    QuestionnaireVersion version = getManagedById(versionId);
    version.sendBackToDraft();
    return version;
  }

  @Transactional
  public QuestionnaireVersion approve(UUID versionId, User actor) {
    QuestionnaireVersion version = getManagedById(versionId);
    version.approve(actor, Instant.now(clock));
    return version;
  }

  @Transactional
  public QuestionnaireVersion archive(UUID versionId) {
    QuestionnaireVersion version = getManagedById(versionId);
    version.archive();
    return version;
  }

  @Transactional
  public QuestionnaireVersion updateDraftContent(UUID versionId, String title, String description) {
    QuestionnaireVersion version = getManagedById(versionId);
    version.updateDraftContent(title, description);
    return version;
  }

  @Transactional(readOnly = true)
  public QuestionnaireVersion getById(UUID versionId) {
    return getManagedById(versionId);
  }

  @Transactional(readOnly = true)
  public List<QuestionnaireVersion> listVersions(UUID questionnaireId) {
    return questionnaireVersionRepository.findByQuestionnaire_IdOrderByVersionNumberDesc(
        questionnaireId);
  }

  private QuestionnaireVersion getManagedById(UUID versionId) {
    return questionnaireVersionRepository
        .findByIdFetchingAll(versionId)
        .orElseThrow(
            () ->
                new com.foreignerwarsaw.common.web.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "QUESTIONNAIRE_VERSION_NOT_FOUND",
                    "No version found for id " + versionId));
  }
}
