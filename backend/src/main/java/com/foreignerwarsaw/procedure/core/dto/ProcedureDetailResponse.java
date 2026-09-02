package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public detail response (brief §38) - never exposes DRAFT versions, internal reviewer fields
 * (createdBy/approvedBy/etc.), or database implementation details (lockVersion, raw entity IDs).
 *
 * <p>{@code contentReviewedAt} (brief §40) is defined as the *earliest* {@code lastVerifiedAt}
 * across this version's PRIMARY sources - deliberately conservative: if any primary source hasn't
 * been (re-)verified as recently as another, that's the bottleneck for how current the whole page
 * can honestly be considered. {@code null} if any PRIMARY source has never been verified at all
 * (publish-time validation already requires at least one to be VERIFIED, but a second PRIMARY
 * source could still be unverified).
 */
public record ProcedureDetailResponse(
    String code,
    String name,
    String summary,
    String description,
    String category,
    String jurisdictionScope,
    int versionNumber,
    LocalDate effectiveFrom,
    List<StepResponse> steps,
    List<DocumentRequirementResponse> documents,
    List<FeeResponse> fees,
    List<ProcedureAuthorityRefResponse> authorities,
    List<ProcedureOfficeRefResponse> offices,
    List<SourceResponse> sources,
    Instant contentReviewedAt) {

  public static ProcedureDetailResponse of(
      Procedure procedure,
      ProcedureVersion version,
      List<StepResponse> steps,
      List<DocumentRequirementResponse> documents,
      List<FeeResponse> fees,
      List<ProcedureAuthorityRefResponse> authorities,
      List<ProcedureOfficeRefResponse> offices,
      List<SourceResponse> sources) {
    Instant contentReviewedAt =
        sources.stream()
            .filter(s -> "PRIMARY".equals(s.role()))
            .map(SourceResponse::lastVerifiedAt)
            .reduce((a, b) -> a == null || b == null ? null : (a.isBefore(b) ? a : b))
            .orElse(null);
    return new ProcedureDetailResponse(
        procedure.getCode(),
        procedure.getCanonicalName(),
        version.getSummary(),
        version.getDescription(),
        procedure.getCategory().getCode(),
        procedure.getJurisdictionScope().name(),
        version.getVersionNumber(),
        version.getEffectiveFrom(),
        steps,
        documents,
        fees,
        authorities,
        offices,
        sources,
        contentReviewedAt);
  }
}
