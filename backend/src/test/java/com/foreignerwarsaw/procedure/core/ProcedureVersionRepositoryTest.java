package com.foreignerwarsaw.procedure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.TestcontainersConfiguration;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.category.ProcedureCategory;
import com.foreignerwarsaw.procedure.category.ProcedureCategoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Real Testcontainers PostgreSQL - the Active-Version Predicate (brief §9) and the
 * no-overlapping-PUBLISHED-versions exclusion constraint (brief §11) proven against the real
 * database, not a mocked repository.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProcedureVersionRepositoryTest {

  @Autowired private ProcedureRepository procedureRepository;
  @Autowired private ProcedureVersionRepository procedureVersionRepository;
  @Autowired private ProcedureCategoryRepository procedureCategoryRepository;
  @Autowired private TestEntityManager testEntityManager;

  private Procedure newProcedure(String code) {
    ProcedureCategory category =
        procedureCategoryRepository.findByCodeIgnoreCase("WORK").orElseThrow();
    return procedureRepository.saveAndFlush(
        Procedure.create(
            code,
            category,
            "Test procedure " + code,
            "For repository tests only",
            JurisdictionScope.NATIONAL));
  }

  private ProcedureVersion publishedVersion(
      Procedure procedure, int versionNumber, LocalDate from, LocalDate to) {
    ProcedureVersion version =
        ProcedureVersion.draft(procedure, versionNumber, "Title", "Summary", "Description", null);
    ReflectionTestUtils.setField(version, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(version, "effectiveFrom", from);
    ReflectionTestUtils.setField(version, "effectiveTo", to);
    return procedureVersionRepository.save(version);
  }

  @Test
  void findActivePublishedVersion_draftIsNeverReturned() {
    Procedure procedure = newProcedure("TEST_DRAFT_ONLY");
    procedureVersionRepository.save(ProcedureVersion.draft(procedure, 1, "T", "S", "D", null));
    testEntityManager.flush();

    Optional<ProcedureVersion> active =
        procedureVersionRepository.findActivePublishedVersion(procedure.getId(), LocalDate.now());

    assertThat(active).isEmpty();
  }

  @Test
  void
      findActivePublishedVersion_futureDatedPublishedVersion_isNotReturnedBeforeItsEffectiveDate() {
    Procedure procedure = newProcedure("TEST_FUTURE_DATED");
    LocalDate future = LocalDate.now().plusMonths(2);
    publishedVersion(procedure, 1, future, null);
    testEntityManager.flush();

    assertThat(
            procedureVersionRepository.findActivePublishedVersion(
                procedure.getId(), LocalDate.now()))
        .isEmpty();
    assertThat(procedureVersionRepository.findActivePublishedVersion(procedure.getId(), future))
        .isPresent();
    assertThat(
            procedureVersionRepository.findActivePublishedVersion(
                procedure.getId(), future.plusDays(1)))
        .isPresent();
  }

  @Test
  void findActivePublishedVersion_expiredVersion_isNotReturnedAfterEffectiveTo() {
    Procedure procedure = newProcedure("TEST_EXPIRED");
    LocalDate from = LocalDate.now().minusYears(1);
    LocalDate to = LocalDate.now().minusMonths(1);
    publishedVersion(procedure, 1, from, to);
    testEntityManager.flush();

    // Exclusive effectiveTo (brief §9/DATABASE.md §0): the boundary day itself is
    // already outside the range.
    assertThat(procedureVersionRepository.findActivePublishedVersion(procedure.getId(), to))
        .isEmpty();
    assertThat(
            procedureVersionRepository.findActivePublishedVersion(
                procedure.getId(), to.minusDays(1)))
        .isPresent();
  }

  @Test
  void findActivePublishedVersion_openEndedCurrentVersion_isReturnedToday() {
    Procedure procedure = newProcedure("TEST_OPEN_ENDED");
    publishedVersion(procedure, 1, LocalDate.now().minusDays(10), null);
    testEntityManager.flush();

    Optional<ProcedureVersion> active =
        procedureVersionRepository.findActivePublishedVersion(procedure.getId(), LocalDate.now());

    assertThat(active).isPresent();
    assertThat(active.get().getVersionNumber()).isEqualTo(1);
  }

  @Test
  void overlappingPublishedVersions_areRejectedByTheDatabaseExclusionConstraint() {
    Procedure procedure = newProcedure("TEST_OVERLAP");
    publishedVersion(procedure, 1, LocalDate.of(2026, 1, 1), null);
    testEntityManager.flush();

    // A second PUBLISHED version whose range overlaps the first's open-ended range -
    // must be rejected at the database level (brief §11), independent of any
    // service-layer check.
    ProcedureVersion overlapping = ProcedureVersion.draft(procedure, 2, "T2", "S2", "D2", null);
    ReflectionTestUtils.setField(overlapping, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(overlapping, "effectiveFrom", LocalDate.of(2026, 6, 1));

    assertThatThrownBy(() -> procedureVersionRepository.saveAndFlush(overlapping))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void nonOverlappingSequentialPublishedVersions_areAccepted() {
    Procedure procedure = newProcedure("TEST_SEQUENTIAL");
    publishedVersion(procedure, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
    testEntityManager.flush();

    ProcedureVersion v2 = publishedVersion(procedure, 2, LocalDate.of(2026, 6, 1), null);
    testEntityManager.flush();

    assertThat(v2.getId()).isNotNull();
    List<ProcedureVersion> published =
        procedureVersionRepository.findPublishedVersions(procedure.getId());
    assertThat(published).hasSize(2);
  }

  @Test
  void versionNumberMustBeUniquePerProcedure() {
    Procedure procedure = newProcedure("TEST_DUPLICATE_VERSION_NUMBER");
    procedureVersionRepository.saveAndFlush(
        ProcedureVersion.draft(procedure, 1, "T", "S", "D", null));

    ProcedureVersion duplicate = ProcedureVersion.draft(procedure, 1, "T2", "S2", "D2", null);

    assertThatThrownBy(() -> procedureVersionRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
