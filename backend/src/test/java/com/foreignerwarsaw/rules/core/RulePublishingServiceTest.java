package com.foreignerwarsaw.rules.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.SourceType;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidationException;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidator;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Publish-readiness validation (brief §23/§27) and threshold-reference sync, isolated with mocked
 * repositories - mirrors {@code ProcedurePublishingServiceTest} exactly. {@code
 * RuleEngineIntegrationTest} proves the same rules again against a real database, including the
 * exclusion-constraint race.
 */
@ExtendWith(MockitoExtension.class)
class RulePublishingServiceTest {

  @Mock private RuleVersionRepository ruleVersionRepository;
  @Mock private RuleVersionSourceRepository ruleVersionSourceRepository;
  @Mock private RuleThresholdReferenceRepository ruleThresholdReferenceRepository;
  @Mock private ConditionTreeValidator conditionTreeValidator;

  private RulePublishingService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    service =
        new RulePublishingService(
            ruleVersionRepository,
            ruleVersionSourceRepository,
            ruleThresholdReferenceRepository,
            conditionTreeValidator,
            new ObjectMapper(),
            clock);
  }

  private RuleVersion approvedVersion() {
    Rule rule =
        new Rule(
            "BLUE_CARD_ELIGIBILITY",
            "Blue Card eligibility",
            RuleType.ELIGIBILITY,
            RuleTargetType.PROCEDURE,
            "BLUE_CARD");
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    RuleVersion version =
        RuleVersion.draft(
            rule, 1, "{\"fact\":\"A\",\"operator\":\"EXISTS\"}", "rules.blueCard", null);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(version, "status", PublicationStatus.APPROVED);
    return version;
  }

  private void mockNoOtherPublishedVersions(RuleVersion version) {
    when(ruleVersionRepository.findPublishedVersions(version.getRule().getId()))
        .thenReturn(List.of());
  }

  private RuleVersionSource verifiedSource(RuleVersion version, SourceRole role) {
    OfficialSource source =
        OfficialSource.draft("Ustawa", "https://isap.sejm.gov.pl", SourceType.LEGISLATION);
    ReflectionTestUtils.setField(source, "verificationStatus", VerificationStatus.VERIFIED);
    return new RuleVersionSource(version, source, role);
  }

  @Test
  void publish_rejectsAVersionThatIsNotApproved() {
    RuleVersion draft = approvedVersion();
    ReflectionTestUtils.setField(draft, "status", PublicationStatus.DRAFT);
    when(ruleVersionRepository.findByIdFetchingRule(draft.getId())).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> service.publish(draft.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("VERSION_NOT_APPROVED"));
  }

  @Test
  void publish_rejectsAMissingEffectiveFrom() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));

    assertThatThrownBy(() -> service.publish(version.getId(), null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("MISSING_EFFECTIVE_FROM"));
  }

  @Test
  void publish_rejectsAnInvalidConditionTree() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    org.mockito.Mockito.doThrow(new ConditionTreeValidationException(List.of("unknown fact \"A\"")))
        .when(conditionTreeValidator)
        .validate(version.getConditionTree());

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CONDITION_TREE_INVALID"));
  }

  @Test
  void publish_rejectsAVersionWithNoSourceAtAll() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId())).thenReturn(List.of());

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NO_VERIFIED_SOURCE"));
  }

  @Test
  void publish_rejectsAVersionWhoseOnlyVerifiedSourceIsSupportingNotPrimaryOrLegalBasis() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(verifiedSource(version, SourceRole.SUPPORTING)));

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NO_VERIFIED_SOURCE"));
  }

  @Test
  void publish_acceptsALegalBasisSource_notOnlyPrimary() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(verifiedSource(version, SourceRole.LEGAL_BASIS)));
    mockNoOtherPublishedVersions(version);
    when(ruleThresholdReferenceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of());

    RuleVersion published = service.publish(version.getId(), mockUser(), LocalDate.now(clock));

    assertThat(published.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void publish_succeedsWithApprovedVersionValidTreeAndVerifiedPrimarySource() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(verifiedSource(version, SourceRole.PRIMARY)));
    mockNoOtherPublishedVersions(version);
    when(ruleThresholdReferenceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of());

    RuleVersion published = service.publish(version.getId(), mockUser(), LocalDate.now(clock));

    assertThat(published.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    verify(conditionTreeValidator, times(1)).validate(version.getConditionTree());
  }

  @Test
  void publish_rejectsANewVersionThatWouldNotStartAfterTheCurrentlyPublishedVersion() {
    RuleVersion version = approvedVersion();
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(verifiedSource(version, SourceRole.PRIMARY)));

    RuleVersion alreadyPublished = approvedVersion();
    ReflectionTestUtils.setField(alreadyPublished, "rule", version.getRule());
    ReflectionTestUtils.setField(alreadyPublished, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(alreadyPublished, "effectiveFrom", LocalDate.now(clock));
    when(ruleVersionRepository.findPublishedVersions(version.getRule().getId()))
        .thenReturn(List.of(alreadyPublished));

    assertThatThrownBy(() -> service.publish(version.getId(), mockUser(), LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex ->
                assertThat(((ApiException) ex).getCode())
                    .isEqualTo("OVERLAPPING_PUBLISHED_VERSION"));
  }

  @Test
  void publish_extractsThresholdReferencesFromTheConditionTree_alwaysRebuildingFromScratch() {
    Rule rule =
        new Rule(
            "SALARY_RULE",
            "Salary rule",
            RuleType.ELIGIBILITY,
            RuleTargetType.PROCEDURE,
            "BLUE_CARD");
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    RuleVersion version =
        RuleVersion.draft(
            rule,
            1,
            "{\"all\":[{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN\",\"threshold\":\"BLUE_CARD_SALARY_THRESHOLD\"},"
                + "{\"fact\":\"AGE_YEARS\",\"operator\":\"GREATER_THAN_OR_EQUAL\",\"value\":18}]}",
            "rules.salary",
            null);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(version, "status", PublicationStatus.APPROVED);
    when(ruleVersionRepository.findByIdFetchingRule(version.getId()))
        .thenReturn(Optional.of(version));
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(verifiedSource(version, SourceRole.PRIMARY)));
    mockNoOtherPublishedVersions(version);
    // Pre-existing (now-stale) reference rows must be deleted before the fresh set is written.
    RuleThresholdReference stale = new RuleThresholdReference(version, "OLD_STALE_THRESHOLD");
    when(ruleThresholdReferenceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(stale));

    service.publish(version.getId(), mockUser(), LocalDate.now(clock));

    verify(ruleThresholdReferenceRepository).deleteAll(List.of(stale));
    org.mockito.ArgumentCaptor<RuleThresholdReference> captor =
        org.mockito.ArgumentCaptor.forClass(RuleThresholdReference.class);
    verify(ruleThresholdReferenceRepository).save(captor.capture());
    assertThat(captor.getValue().getThresholdCode()).isEqualTo("BLUE_CARD_SALARY_THRESHOLD");
  }

  @Test
  void archive_neverRunsPublishValidation() {
    RuleVersion published = approvedVersion();
    ReflectionTestUtils.setField(published, "status", PublicationStatus.PUBLISHED);
    when(ruleVersionRepository.findByIdFetchingRule(published.getId()))
        .thenReturn(Optional.of(published));

    RuleVersion archived = service.archive(published.getId());

    assertThat(archived.getStatus()).isEqualTo(PublicationStatus.ARCHIVED);
    verify(conditionTreeValidator, never()).validate(any());
  }

  private User mockUser() {
    return org.mockito.Mockito.mock(User.class);
  }
}
