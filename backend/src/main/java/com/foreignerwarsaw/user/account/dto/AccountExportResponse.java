package com.foreignerwarsaw.user.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Canonical Phase 12 personal-data export (brief §16-§21). An explicit, hand-mapped DTO tree - no
 * JPA entity is ever serialized directly (brief §17), so a field only appears here because it was
 * deliberately decided to be exportable personal data, never by accident of what an entity happens
 * to expose. Deliberately excludes {@code passwordHash}, session identifiers, and
 * verification/reset token (hashes or otherwise) - see {@code AccountExportService} and
 * `ProductionSecurityTest`-style export tests for the explicit proof these never appear.
 */
public record AccountExportResponse(
    int exportSchemaVersion,
    Instant generatedAt,
    UUID accountId,
    Account account,
    List<Consent> consents,
    List<Assessment> assessments,
    List<RecommendationRun> recommendationRuns,
    List<Case> cases) {

  public static final int SCHEMA_VERSION = 1;

  public record Account(
      UUID id,
      String email,
      String firstName,
      String preferredLanguage,
      boolean emailVerified,
      List<String> roles,
      Instant createdAt) {}

  public record Consent(String consentType, String policyVersion, Instant acceptedAt) {}

  public record Assessment(
      UUID id,
      String questionnaireCode,
      int questionnaireVersionNumber,
      String status,
      Instant startedAt,
      Instant completedAt,
      List<Answer> answers) {}

  public record Answer(String questionCode, Object value, boolean unsure) {}

  public record RecommendationRun(
      UUID id,
      UUID assessmentId,
      LocalDate evaluationDate,
      String status,
      Instant createdAt,
      List<Recommendation> recommendations) {}

  public record Recommendation(
      UUID id, String procedureCode, String recommendationType, int rank, Instant createdAt) {}

  public record Case(
      UUID id,
      String procedureCode,
      String status,
      Integer currentRevisionNumber,
      Instant createdAt,
      Instant updatedAt,
      List<Step> steps,
      List<Document> documents,
      List<Fee> fees,
      List<Event> events) {}

  public record Step(String stableCode, String title, String status, boolean mandatory) {}

  public record Document(
      String stableCode, String name, String status, boolean mandatory, String userNote) {}

  public record Fee(
      String stableCode, String feeType, BigDecimal amount, String currency, String status) {}

  public record Event(String eventType, Instant occurredAt) {}
}
