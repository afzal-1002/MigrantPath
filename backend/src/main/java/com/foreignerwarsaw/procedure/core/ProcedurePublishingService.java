package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.admin.validation.ValidationIssue;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publish-time validation (brief §27/§28) and the transactional publish step (brief §58: validate
 * -> close the previous active version if needed -> mark PUBLISHED -> persist actor/time, all one
 * transaction or none of it). This is the only place a {@link ProcedureVersion} is allowed to reach
 * {@link PublicationStatus#PUBLISHED} - {@link ProcedureVersion#markPublished} itself only checks
 * the mechanical state-machine transition, not readiness.
 */
@Service
public class ProcedurePublishingService {

  private final ProcedureVersionRepository procedureVersionRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final Clock clock;

  public ProcedurePublishingService(
      ProcedureVersionRepository procedureVersionRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      Clock clock) {
    this.procedureVersionRepository = procedureVersionRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.clock = clock;
  }

  /**
   * Publish-readiness checks (brief §27/§28) - at minimum: the version is APPROVED, has a title, a
   * summary, an effective_from, and at least one PRIMARY {@link
   * com.foreignerwarsaw.procedure.source.SourceRole} {@link
   * com.foreignerwarsaw.procedure.source.OfficialSource} whose {@code verificationStatus} is {@link
   * VerificationStatus#VERIFIED} - not merely present, not {@code OUTDATED}/{@code ARCHIVED}.
   * Deliberately does not require every paragraph to carry its own source record (brief §28's "do
   * not make the model so rigid") - one sufficiently authoritative source for the version as a
   * whole is the bar.
   *
   * <p>Takes {@code versionId}, not an already-loaded entity, and re-fetches here - see {@link
   * ProcedureVersionService#submitForReview}'s Javadoc for why (the same detached-entity bug this
   * method was also fixed for).
   */
  @Transactional
  public ProcedureVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    ProcedureVersion version =
        procedureVersionRepository
            .findByIdFetchingProcedure(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCEDURE_VERSION_NOT_FOUND",
                        "No version found for id " + versionId));
    validatePublishReadiness(version, effectiveFrom);

    UUID procedureId = version.getProcedure().getId();
    List<ProcedureVersion> currentlyPublished =
        procedureVersionRepository.findPublishedVersions(procedureId);
    for (ProcedureVersion existing : currentlyPublished) {
      if (existing.getId().equals(version.getId())) {
        continue;
      }
      if (existing.getEffectiveFrom() != null
          && !existing.getEffectiveFrom().isBefore(effectiveFrom)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "OVERLAPPING_PUBLISHED_VERSION",
            "New version must start after the currently published version's start date");
      }
      // Close the previous active version's range exactly where the new one begins
      // (brief §58/§80) - the exclusive effectiveTo convention makes this boundary
      // non-overlapping with the new version's effectiveFrom by construction.
      //
      // saveAndFlush, not save: the exclusion constraint is checked per-statement, not
      // only at commit, and Hibernate's automatic flush ordering is not guaranteed to
      // write this UPDATE before the new version's own PUBLISHED update below - without
      // forcing it out now, the two can flush in the wrong order and the constraint
      // rejects the new version's insert against the *old*, still-open-ended range
      // (found via ProcedureVersioningIntegrationTest's future-dated-version scenario:
      // publishing v2 failed even though v1 was correctly closed in Java, because the
      // close hadn't actually reached the database yet).
      if (existing.getEffectiveTo() == null || existing.getEffectiveTo().isAfter(effectiveFrom)) {
        existing.closeEffectiveTo(effectiveFrom);
        procedureVersionRepository.saveAndFlush(existing);
      }
    }

    version.markPublished(actor, clock.instant(), effectiveFrom);
    return version;
  }

  @Transactional
  public ProcedureVersion archive(UUID versionId) {
    ProcedureVersion version =
        procedureVersionRepository
            .findByIdFetchingProcedure(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCEDURE_VERSION_NOT_FOUND",
                        "No version found for id " + versionId));
    version.archive();
    return version;
  }

  private void validatePublishReadiness(ProcedureVersion version, LocalDate effectiveFrom) {
    List<ValidationIssue> issues = readiness(version, effectiveFrom);
    if (!issues.isEmpty()) {
      ValidationIssue first = issues.get(0);
      HttpStatus status =
          first.code().equals("MISSING_EFFECTIVE_FROM")
                  || first.code().equals("MISSING_TITLE")
                  || first.code().equals("MISSING_SUMMARY")
              ? HttpStatus.BAD_REQUEST
              : HttpStatus.CONFLICT;
      throw new ApiException(status, first.code(), first.message());
    }
  }

  /**
   * Phase 9 addition (brief §42/§91): the same publish-readiness checks {@link #publish} enforces,
   * collected rather than thrown-at-first-failure, so an admin editor's Validation panel can show
   * every outstanding problem at once instead of forcing one publish attempt per fix. {@code
   * effectiveFrom} is optional here (unlike at real publish time) - a draft being previewed before
   * an effective date has been chosen simply skips the date-specific check rather than failing it.
   */
  @Transactional(readOnly = true)
  public List<ValidationIssue> readiness(UUID versionId, LocalDate effectiveFrom) {
    ProcedureVersion version =
        procedureVersionRepository
            .findByIdFetchingProcedure(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROCEDURE_VERSION_NOT_FOUND",
                        "No version found for id " + versionId));
    return readiness(version, effectiveFrom);
  }

  private List<ValidationIssue> readiness(ProcedureVersion version, LocalDate effectiveFrom) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (version.getStatus() != PublicationStatus.APPROVED) {
      issues.add(
          new ValidationIssue("VERSION_NOT_APPROVED", "Only an APPROVED version can be published"));
    }
    if (effectiveFrom == null) {
      issues.add(
          new ValidationIssue("MISSING_EFFECTIVE_FROM", "effectiveFrom is required to publish"));
    }
    if (version.getTitle() == null || version.getTitle().isBlank()) {
      issues.add(new ValidationIssue("MISSING_TITLE", "A title is required to publish"));
    }
    if (version.getSummary() == null || version.getSummary().isBlank()) {
      issues.add(new ValidationIssue("MISSING_SUMMARY", "A summary is required to publish"));
    }
    List<ProcedureVersionSource> sources =
        procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId());
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
              "A version cannot be published without at least one VERIFIED primary official"
                  + " source"));
    }
    return issues;
  }
}
