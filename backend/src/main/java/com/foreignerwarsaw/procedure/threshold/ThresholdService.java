package com.foreignerwarsaw.procedure.threshold;

import com.foreignerwarsaw.admin.validation.ValidationIssue;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Threshold engine's own lifecycle - deliberately not exposed through a dedicated internal HTTP
 * API in Phase 4 (brief §43's "if implementing all management endpoints is too much... defer UI/API
 * breadth"): no threshold value exists to manage yet (brief §21/§53 forbids seeding one), so
 * service+repository-level coverage proves the engine works without a controller no admin has any
 * real content to call it with yet. A future phase adds the controller alongside its first real
 * threshold.
 */
@Service
public class ThresholdService {

  private final ThresholdRepository thresholdRepository;
  private final ThresholdVersionRepository thresholdVersionRepository;
  private final Clock clock;

  public ThresholdService(
      ThresholdRepository thresholdRepository,
      ThresholdVersionRepository thresholdVersionRepository,
      Clock clock) {
    this.thresholdRepository = thresholdRepository;
    this.thresholdVersionRepository = thresholdVersionRepository;
    this.clock = clock;
  }

  @Transactional
  public Threshold createThreshold(
      String code, String canonicalName, ThresholdValueType valueType) {
    if (thresholdRepository.findByCodeIgnoreCase(code).isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "THRESHOLD_CODE_TAKEN", "Threshold code already exists: " + code);
    }
    return thresholdRepository.save(new Threshold(code, canonicalName, valueType));
  }

  @Transactional
  public ThresholdVersion createDraftVersion(
      Threshold threshold, BigDecimal value, String valueText, User createdBy) {
    return thresholdVersionRepository.save(
        ThresholdVersion.draft(threshold, value, valueText, createdBy));
  }

  /**
   * Takes {@code versionId}, not an already-loaded entity, and re-fetches inside this transaction -
   * see {@code ProcedureVersionService#submitForReview}'s Javadoc for the detached-entity bug this
   * signature avoids (mutating a detached entity's fields never flushes to the database, since it
   * isn't managed by the transaction doing the mutating).
   */
  @Transactional
  public ThresholdVersion submitForReview(UUID versionId, User actor) {
    ThresholdVersion version = getManagedById(versionId);
    version.submitForReview(actor, clock.instant());
    return version;
  }

  @Transactional
  public ThresholdVersion sendBackToDraft(UUID versionId) {
    ThresholdVersion version = getManagedById(versionId);
    version.sendBackToDraft();
    return version;
  }

  @Transactional
  public ThresholdVersion approve(UUID versionId, User actor) {
    ThresholdVersion version = getManagedById(versionId);
    version.approve(actor, clock.instant());
    return version;
  }

  /**
   * Phase 9 addition (brief §46/§77) - {@code ThresholdVersion} previously had no archive action
   * exposed anywhere, unlike its three siblings.
   */
  @Transactional
  public ThresholdVersion archive(UUID versionId) {
    ThresholdVersion version = getManagedById(versionId);
    version.archive();
    return version;
  }

  /** Phase 9 addition (brief §47): edit a still-DRAFT threshold version's value/dates/notes. */
  @Transactional
  public ThresholdVersion updateDraftContent(
      UUID versionId, BigDecimal value, String valueText, LocalDate effectiveFrom, String notes) {
    ThresholdVersion version = getManagedById(versionId);
    version.updateDraftContent(value, valueText, effectiveFrom, notes);
    return version;
  }

  /** Phase 9 addition (brief §42/§91), mirroring {@code ProcedurePublishingService#readiness}. */
  @Transactional(readOnly = true)
  public List<ValidationIssue> readiness(UUID versionId) {
    ThresholdVersion version = getManagedById(versionId);
    List<ValidationIssue> issues = new ArrayList<>();
    if (version.getValue() == null && version.getValueText() == null) {
      issues.add(
          new ValidationIssue(
              "THRESHOLD_VALUE_MISSING", "Cannot publish a threshold version with no value"));
    }
    if (version.getEffectiveFrom() == null) {
      issues.add(
          new ValidationIssue("MISSING_EFFECTIVE_FROM", "effectiveFrom is required to publish"));
    }
    return issues;
  }

  @Transactional(readOnly = true)
  public List<Threshold> listAll() {
    return thresholdRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Threshold getByCode(String code) {
    return thresholdRepository
        .findByCodeIgnoreCase(code)
        .orElseThrow(() -> new ThresholdNotFoundException(code));
  }

  @Transactional(readOnly = true)
  public List<ThresholdVersion> listVersions(UUID thresholdId) {
    return thresholdVersionRepository.findByThreshold_Id(thresholdId);
  }

  @Transactional(readOnly = true)
  public ThresholdVersion getVersionById(UUID versionId) {
    return getManagedById(versionId);
  }

  /**
   * Publish-readiness (a value must be set - already a DB CHECK constraint, so this is a clearer
   * error than a raw constraint violation) plus the same "close the previous PUBLISHED version"
   * transactional step ProcedurePublishingService performs (brief §58).
   */
  @Transactional
  public ThresholdVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    ThresholdVersion version = getManagedById(versionId);
    if (version.getValue() == null && version.getValueText() == null) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "THRESHOLD_VALUE_MISSING",
          "Cannot publish a threshold version with no value");
    }
    UUID thresholdId = version.getThreshold().getId();
    List<ThresholdVersion> currentlyPublished =
        thresholdVersionRepository.findPublishedVersions(thresholdId);
    for (ThresholdVersion existing : currentlyPublished) {
      if (existing.getEffectiveFrom() != null
          && !existing.getEffectiveFrom().isBefore(effectiveFrom)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "OVERLAPPING_PUBLISHED_VERSION",
            "New version must start after the currently published version's start date");
      }
      // saveAndFlush, not save - see ProcedurePublishingService#publish's Javadoc for
      // why: the exclusion constraint checks per-statement, and Hibernate's flush
      // ordering isn't guaranteed to write this close before the new version's own
      // PUBLISHED update.
      if (existing.getEffectiveTo() == null || existing.getEffectiveTo().isAfter(effectiveFrom)) {
        existing.closeEffectiveTo(effectiveFrom);
        thresholdVersionRepository.saveAndFlush(existing);
      }
    }
    version.markPublished(actor, clock.instant(), effectiveFrom);
    return version;
  }

  @Transactional(readOnly = true)
  public java.util.Optional<ThresholdVersion> findActiveVersion(
      String thresholdCode, LocalDate evaluationDate) {
    Threshold threshold =
        thresholdRepository
            .findByCodeIgnoreCase(thresholdCode)
            .orElseThrow(() -> new ThresholdNotFoundException(thresholdCode));
    return thresholdVersionRepository.findActivePublishedVersion(threshold.getId(), evaluationDate);
  }

  private ThresholdVersion getManagedById(UUID versionId) {
    return thresholdVersionRepository
        .findByIdFetchingAll(versionId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "THRESHOLD_VERSION_NOT_FOUND",
                    "No version found for id " + versionId));
  }
}
