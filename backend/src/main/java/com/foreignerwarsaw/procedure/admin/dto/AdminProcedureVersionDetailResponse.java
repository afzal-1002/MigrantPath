package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionSource;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
import com.foreignerwarsaw.procedure.step.StepVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The full DRAFT/IN_REVIEW/APPROVED/PUBLISHED/ARCHIVED version editor payload (brief §19) - one
 * response carrying every tab's content (Overview/Steps/Documents/Fees/Sources) rather than four
 * separate round trips, since the admin editor loads them together.
 */
public record AdminProcedureVersionDetailResponse(
    UUID id,
    String procedureCode,
    int versionNumber,
    String title,
    String summary,
    String description,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String changeSummary,
    long lockVersion,
    Actor createdBy,
    Actor submittedBy,
    Actor approvedBy,
    Actor publishedBy,
    Instant submittedAt,
    Instant approvedAt,
    Instant publishedAt,
    List<Step> steps,
    List<Document> documents,
    List<Fee> fees,
    List<Source> sources) {

  public record Actor(UUID id, String email) {
    static Actor from(com.foreignerwarsaw.user.User user) {
      return user == null ? null : new Actor(user.getId(), user.getEmail());
    }
  }

  public record Step(
      UUID id,
      String stableCode,
      String title,
      String description,
      String detailedInstructions,
      String stepType,
      int sortOrder,
      boolean mandatory) {
    static Step from(StepVersion s) {
      return new Step(
          s.getId(),
          s.getProcedureStep().getStableCode(),
          s.getTitle(),
          s.getDescription(),
          s.getDetailedInstructions(),
          s.getStepType().name(),
          s.getSortOrder(),
          s.isMandatory());
    }
  }

  public record Document(
      UUID id,
      String stableCode,
      String name,
      String description,
      String requirementType,
      boolean requiredByDefault,
      Integer numberOfCopies,
      Boolean originalRequired,
      Boolean copyRequired,
      Boolean translationRequired,
      Boolean swornTranslationRequired,
      Boolean apostilleRequired,
      Boolean legalisationRequired,
      String validityPeriodDescription,
      String notes,
      int sortOrder) {
    static Document from(DocumentRequirementVersion d) {
      return new Document(
          d.getId(),
          d.getDocumentRequirement().getStableCode(),
          d.getName(),
          d.getDescription(),
          d.getRequirementType().name(),
          d.isRequiredByDefault(),
          d.getNumberOfCopies(),
          d.getOriginalRequired(),
          d.getCopyRequired(),
          d.getTranslationRequired(),
          d.getSwornTranslationRequired(),
          d.getApostilleRequired(),
          d.getLegalisationRequired(),
          d.getValidityPeriodDescription(),
          d.getNotes(),
          d.getSortOrder());
    }
  }

  public record Fee(
      UUID id,
      String stableCode,
      String feeType,
      BigDecimal amount,
      String currency,
      String description,
      String paymentInstructions,
      Boolean refundable) {
    static Fee from(FeeVersion f) {
      return new Fee(
          f.getId(),
          f.getFee().getStableCode(),
          f.getFee().getFeeType().name(),
          f.getAmount(),
          f.getCurrency(),
          f.getDescription(),
          f.getPaymentInstructions(),
          f.getRefundable());
    }
  }

  public record Source(
      UUID officialSourceId,
      String title,
      String sourceUrl,
      String role,
      String verificationStatus) {
    static Source from(ProcedureVersionSource s) {
      return new Source(
          s.getOfficialSource().getId(),
          s.getOfficialSource().getTitle(),
          s.getOfficialSource().getSourceUrl(),
          s.getRole().name(),
          s.getOfficialSource().getVerificationStatus().name());
    }
  }

  public static AdminProcedureVersionDetailResponse from(
      ProcedureVersion v,
      List<StepVersion> steps,
      List<DocumentRequirementVersion> documents,
      List<FeeVersion> fees,
      List<ProcedureVersionSource> sources) {
    return new AdminProcedureVersionDetailResponse(
        v.getId(),
        v.getProcedure().getCode(),
        v.getVersionNumber(),
        v.getTitle(),
        v.getSummary(),
        v.getDescription(),
        v.getStatus().name(),
        v.getEffectiveFrom(),
        v.getEffectiveTo(),
        v.getChangeSummary(),
        v.getLockVersion(),
        Actor.from(v.getCreatedBy()),
        Actor.from(v.getSubmittedBy()),
        Actor.from(v.getApprovedBy()),
        Actor.from(v.getPublishedBy()),
        v.getSubmittedAt(),
        v.getApprovedAt(),
        v.getPublishedAt(),
        steps.stream().map(Step::from).toList(),
        documents.stream().map(Document::from).toList(),
        fees.stream().map(Fee::from).toList(),
        sources.stream().map(Source::from).toList());
  }
}
