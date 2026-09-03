package com.foreignerwarsaw.rules.admin.dto;

import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRuleVersionDetailResponse(
    UUID id,
    String ruleCode,
    int versionNumber,
    String status,
    String conditionTree,
    String explanationKey,
    String changeSummary,
    java.time.LocalDate effectiveFrom,
    java.time.LocalDate effectiveTo,
    long lockVersion,
    Actor createdBy,
    Actor submittedBy,
    Actor approvedBy,
    Actor publishedBy,
    Instant submittedAt,
    Instant approvedAt,
    Instant publishedAt,
    List<Source> sources) {

  public record Actor(UUID id, String email) {
    static Actor from(com.foreignerwarsaw.user.User user) {
      return user == null ? null : new Actor(user.getId(), user.getEmail());
    }
  }

  public record Source(
      UUID officialSourceId,
      String title,
      String sourceUrl,
      String role,
      String verificationStatus) {
    static Source from(RuleVersionSource s) {
      return new Source(
          s.getOfficialSource().getId(),
          s.getOfficialSource().getTitle(),
          s.getOfficialSource().getSourceUrl(),
          s.getRole().name(),
          s.getOfficialSource().getVerificationStatus().name());
    }
  }

  public static AdminRuleVersionDetailResponse from(
      RuleVersion v, List<RuleVersionSource> sources) {
    return new AdminRuleVersionDetailResponse(
        v.getId(),
        v.getRule().getCode(),
        v.getVersionNumber(),
        v.getStatus().name(),
        v.getConditionTree(),
        v.getExplanationKey(),
        v.getChangeSummary(),
        v.getEffectiveFrom(),
        v.getEffectiveTo(),
        v.getLockVersion(),
        Actor.from(v.getCreatedBy()),
        Actor.from(v.getSubmittedBy()),
        Actor.from(v.getApprovedBy()),
        Actor.from(v.getPublishedBy()),
        v.getSubmittedAt(),
        v.getApprovedAt(),
        v.getPublishedAt(),
        sources.stream().map(Source::from).toList());
  }
}
