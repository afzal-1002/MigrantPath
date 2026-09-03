package com.foreignerwarsaw.procedure.threshold;

import com.foreignerwarsaw.admin.validation.ValidationIssue;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
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
 * The Threshold engine's own lifecycle - originally not exposed through a dedicated HTTP API in
 * Phase 4 (no threshold value existed yet to manage), given a real admin surface in Phase 9. Every
 * concept a real numeric legal fact needs (draft/review/approve/publish/archive, effective dates,
 * an official-source requirement before publication) now mirrors Procedure/Rule exactly - see the
 * pre-Phase-10 hardening note on {@link #readiness}/{@link #publish} below (brief §D): a {@code
 * ThresholdVersion} could previously reach {@code PUBLISHED} with no source at all, the one
 * publication-safety gap Procedure/Rule never had.
 */
@Service
public class ThresholdService {

  private final ThresholdRepository thresholdRepository;
  private final ThresholdVersionRepository thresholdVersionRepository;
  private final ThresholdVersionSourceRepository thresholdVersionSourceRepository;
  private final Clock clock;

  public ThresholdService(
      ThresholdRepository thresholdRepository,
      ThresholdVersionRepository thresholdVersionRepository,
      ThresholdVersionSourceRepository thresholdVersionSourceRepository,
      Clock clock) {
    this.thresholdRepository = thresholdRepository;
    this.thresholdVersionRepository = thresholdVersionRepository;
    this.thresholdVersionSourceRepository = thresholdVersionSourceRepository;
    this.clock = clock;
  }

  /**
   * Pre-Phase-10 hardening addition (brief §D), mirroring {@code RuleVersionService#attachSource}.
   */
  @Transactional
  public void attachSource(ThresholdVersion version, OfficialSource source, SourceRole role) {
    thresholdVersionSourceRepository.save(new ThresholdVersionSource(version, source, role));
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

  /**
   * Phase 9 addition (brief §42/§91), mirroring {@code ProcedurePublishingService#readiness}.
   * Pre-Phase-10 hardening (brief §D) added the VERIFIED-source requirement - the one publication-
   * safety gap Threshold had that Procedure/Rule never did.
   *
   * <p>{@code effectiveFrom} is optional here (unlike at real publish time) exactly like {@code
   * ProcedurePublishingService#readiness}'s own Javadoc explains - a draft being previewed before
   * an effective date has been chosen simply skips that one check. A real, found-by-testing bug
   * fixed here: this used to check {@code version.getEffectiveFrom()} - the entity's own field,
   * which {@link ThresholdVersion#markPublished} is the only thing that ever sets, so it is always
   * null before publish and this check could never pass, breaking every real publish call that
   * routed through here.
   */
  @Transactional(readOnly = true)
  public List<ValidationIssue> readiness(UUID versionId, LocalDate effectiveFrom) {
    return readiness(getManagedById(versionId), effectiveFrom);
  }

  private List<ValidationIssue> readiness(ThresholdVersion version, LocalDate effectiveFrom) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (version.getStatus() != PublicationStatus.APPROVED) {
      issues.add(
          new ValidationIssue("VERSION_NOT_APPROVED", "Only an APPROVED version can be published"));
    }
    if (version.getValue() == null && version.getValueText() == null) {
      issues.add(
          new ValidationIssue(
              "THRESHOLD_VALUE_MISSING", "Cannot publish a threshold version with no value"));
    }
    if (effectiveFrom == null) {
      issues.add(
          new ValidationIssue("MISSING_EFFECTIVE_FROM", "effectiveFrom is required to publish"));
    }
    List<ThresholdVersionSource> sources =
        thresholdVersionSourceRepository.findByThresholdVersion_Id(version.getId());
    boolean hasVerifiedPrimarySource =
        sources.stream()
            .anyMatch(
                s ->
                    s.getRole() == SourceRole.PRIMARY
                        && s.getOfficialSource().getVerificationStatus()
                            == VerificationStatus.VERIFIED);
    if (!hasVerifiedPrimarySource) {
      issues.add(
          new ValidationIssue(
              "NO_VERIFIED_SOURCE",
              "A threshold version cannot be published without at least one VERIFIED primary"
                  + " official source"));
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
   * Publish-readiness (value present, VERIFIED primary source - brief §D pre-Phase-10 hardening)
   * plus the same "close the previous PUBLISHED version" transactional step
   * ProcedurePublishingService performs (brief §58).
   */
  @Transactional
  public ThresholdVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    ThresholdVersion version = getManagedById(versionId);
    List<ValidationIssue> issues = readiness(version, effectiveFrom);
    if (!issues.isEmpty()) {
      ValidationIssue first = issues.get(0);
      HttpStatus status =
          first.code().equals("MISSING_EFFECTIVE_FROM")
                  || first.code().equals("THRESHOLD_VALUE_MISSING")
              ? HttpStatus.BAD_REQUEST
              : HttpStatus.CONFLICT;
      throw new ApiException(status, first.code(), first.message());
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
