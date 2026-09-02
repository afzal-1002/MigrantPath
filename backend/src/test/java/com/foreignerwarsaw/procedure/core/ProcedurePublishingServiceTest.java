package com.foreignerwarsaw.procedure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.SourceType;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
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
 * Publish-readiness validation (brief §27/§28/§81), isolated with mocked repositories - {@link
 * ProcedureVersionRepositoryTest} proves the same rules again against a real database via the
 * actual exclusion constraint.
 */
@ExtendWith(MockitoExtension.class)
class ProcedurePublishingServiceTest {

  @Mock private ProcedureVersionRepository procedureVersionRepository;
  @Mock private ProcedureVersionSourceRepository procedureVersionSourceRepository;

  private ProcedurePublishingService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    service =
        new ProcedurePublishingService(
            procedureVersionRepository, procedureVersionSourceRepository, clock);
  }

  private ProcedureVersion approvedVersionWithTitleAndSummary() {
    Procedure procedure = new Procedure();
    ReflectionTestUtils.setField(procedure, "id", UUID.randomUUID());
    ProcedureVersion version =
        ProcedureVersion.draft(procedure, 1, "Title", "Summary", "Description", null);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(version, "status", PublicationStatus.APPROVED);
    return version;
  }

  private void mockNoOtherPublishedVersions(ProcedureVersion version) {
    when(procedureVersionRepository.findPublishedVersions(version.getProcedure().getId()))
        .thenReturn(List.of());
  }

  private void mockVerifiedPrimarySource(ProcedureVersion version) {
    OfficialSource source =
        OfficialSource.draft(
            "Test source", "https://example.gov.pl", SourceType.OFFICIAL_SERVICE_PAGE);
    ReflectionTestUtils.setField(source, "verificationStatus", VerificationStatus.VERIFIED);
    ProcedureVersionSource association =
        new ProcedureVersionSource(version, source, SourceRole.PRIMARY);
    when(procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()))
        .thenReturn(List.of(association));
  }

  @Test
  void publish_rejectsAVersionThatIsNotApproved() {
    ProcedureVersion draft = approvedVersionWithTitleAndSummary();
    ReflectionTestUtils.setField(draft, "status", PublicationStatus.DRAFT);
    when(procedureVersionRepository.findByIdFetchingProcedure(draft.getId()))
        .thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> service.publish(draft.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("VERSION_NOT_APPROVED"));
  }

  @Test
  void publish_rejectsAMissingEffectiveFrom() {
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));

    assertThatThrownBy(() -> service.publish(version.getId(), null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("MISSING_EFFECTIVE_FROM"));
  }

  @Test
  void publish_rejectsAVersionWithNoSourceAtAll() {
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));
    when(procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NO_VERIFIED_SOURCE"));
  }

  @Test
  void publish_rejectsAVersionWhoseOnlySourceIsUnverified() {
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));
    OfficialSource unverified =
        OfficialSource.draft(
            "Test source", "https://example.gov.pl", SourceType.OFFICIAL_SERVICE_PAGE);
    ProcedureVersionSource association =
        new ProcedureVersionSource(version, unverified, SourceRole.PRIMARY);
    when(procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()))
        .thenReturn(List.of(association));

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NO_VERIFIED_SOURCE"));
  }

  @Test
  void publish_rejectsAVersionWhoseOnlyVerifiedSourceIsSupportingNotPrimary() {
    // A VERIFIED source that isn't PRIMARY doesn't satisfy the bar (brief §28: "at
    // least one sufficiently authoritative source" - role matters, not just status).
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));
    OfficialSource verifiedButSupporting =
        OfficialSource.draft(
            "Test source", "https://example.gov.pl", SourceType.OFFICIAL_SERVICE_PAGE);
    ReflectionTestUtils.setField(
        verifiedButSupporting, "verificationStatus", VerificationStatus.VERIFIED);
    ProcedureVersionSource association =
        new ProcedureVersionSource(version, verifiedButSupporting, SourceRole.SUPPORTING);
    when(procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()))
        .thenReturn(List.of(association));

    assertThatThrownBy(() -> service.publish(version.getId(), null, LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NO_VERIFIED_SOURCE"));
  }

  @Test
  void publish_succeedsWithAnApprovedVersionAndAVerifiedPrimarySource() {
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));
    mockNoOtherPublishedVersions(version);
    mockVerifiedPrimarySource(version);

    ProcedureVersion published = service.publish(version.getId(), mockUser(), LocalDate.now(clock));

    assertThat(published.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void publish_rejectsANewVersionThatWouldNotStartAfterTheCurrentlyPublishedVersion() {
    ProcedureVersion version = approvedVersionWithTitleAndSummary();
    when(procedureVersionRepository.findByIdFetchingProcedure(version.getId()))
        .thenReturn(Optional.of(version));
    mockVerifiedPrimarySource(version);

    ProcedureVersion alreadyPublished = approvedVersionWithTitleAndSummary();
    ReflectionTestUtils.setField(alreadyPublished, "procedure", version.getProcedure());
    ReflectionTestUtils.setField(alreadyPublished, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(alreadyPublished, "effectiveFrom", LocalDate.now(clock));
    when(procedureVersionRepository.findPublishedVersions(version.getProcedure().getId()))
        .thenReturn(List.of(alreadyPublished));

    // Attempting to publish a "new" version starting on/before the already-published
    // version's own start date - rejected rather than silently reordering history.
    assertThatThrownBy(() -> service.publish(version.getId(), mockUser(), LocalDate.now(clock)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex ->
                assertThat(((ApiException) ex).getCode())
                    .isEqualTo("OVERLAPPING_PUBLISHED_VERSION"));
  }

  private User mockUser() {
    User user = org.mockito.Mockito.mock(User.class);
    return user;
  }
}
