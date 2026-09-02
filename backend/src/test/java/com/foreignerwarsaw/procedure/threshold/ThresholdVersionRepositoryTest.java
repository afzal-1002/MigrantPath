package com.foreignerwarsaw.procedure.threshold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.TestcontainersConfiguration;
import com.foreignerwarsaw.procedure.PublicationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Same Active-Version Predicate + exclusion-constraint proof as {@code
 * ProcedureVersionRepositoryTest}, for the independently-versioned {@link Threshold} engine
 * (IMPLEMENTATION_PLAN.md 4.6). No real threshold value is ever seeded (brief §21/§53) - every
 * fixture here is TEST-only.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ThresholdVersionRepositoryTest {

  @Autowired private ThresholdRepository thresholdRepository;
  @Autowired private ThresholdVersionRepository thresholdVersionRepository;
  @Autowired private TestEntityManager testEntityManager;

  private ThresholdVersion publishedVersion(
      Threshold threshold, BigDecimal value, LocalDate from, LocalDate to) {
    ThresholdVersion version = ThresholdVersion.draft(threshold, value, null, null);
    ReflectionTestUtils.setField(version, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(version, "effectiveFrom", from);
    ReflectionTestUtils.setField(version, "effectiveTo", to);
    return thresholdVersionRepository.save(version);
  }

  @Test
  void findActivePublishedVersion_draftIsNeverReturned() {
    Threshold threshold =
        thresholdRepository.saveAndFlush(
            new Threshold("TEST_THRESHOLD_DRAFT", "Test", ThresholdValueType.MONEY));
    thresholdVersionRepository.save(ThresholdVersion.draft(threshold, BigDecimal.TEN, null, null));
    testEntityManager.flush();

    assertThat(
            thresholdVersionRepository.findActivePublishedVersion(
                threshold.getId(), LocalDate.now()))
        .isEmpty();
  }

  @Test
  void findActivePublishedVersion_resolvesTheOpenEndedCurrentVersion() {
    Threshold threshold =
        thresholdRepository.saveAndFlush(
            new Threshold("TEST_THRESHOLD_OPEN", "Test", ThresholdValueType.MONEY));
    publishedVersion(threshold, new BigDecimal("13355.34"), LocalDate.now().minusDays(5), null);
    testEntityManager.flush();

    Optional<ThresholdVersion> active =
        thresholdVersionRepository.findActivePublishedVersion(threshold.getId(), LocalDate.now());

    assertThat(active).isPresent();
    assertThat(active.get().getValue()).isEqualByComparingTo("13355.34");
  }

  @Test
  void findActivePublishedVersion_expiredVersion_excludedOnItsExclusiveBoundaryDay() {
    Threshold threshold =
        thresholdRepository.saveAndFlush(
            new Threshold("TEST_THRESHOLD_EXPIRED", "Test", ThresholdValueType.MONEY));
    LocalDate to = LocalDate.now().minusDays(1);
    publishedVersion(threshold, BigDecimal.ONE, LocalDate.now().minusYears(1), to);
    testEntityManager.flush();

    assertThat(thresholdVersionRepository.findActivePublishedVersion(threshold.getId(), to))
        .isEmpty();
    assertThat(
            thresholdVersionRepository.findActivePublishedVersion(
                threshold.getId(), to.minusDays(1)))
        .isPresent();
  }

  @Test
  void overlappingPublishedThresholdVersions_areRejectedByTheDatabaseExclusionConstraint() {
    Threshold threshold =
        thresholdRepository.saveAndFlush(
            new Threshold("TEST_THRESHOLD_OVERLAP", "Test", ThresholdValueType.MONEY));
    publishedVersion(threshold, BigDecimal.ONE, LocalDate.of(2026, 1, 1), null);
    testEntityManager.flush();

    ThresholdVersion overlapping = ThresholdVersion.draft(threshold, BigDecimal.TEN, null, null);
    ReflectionTestUtils.setField(overlapping, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(overlapping, "effectiveFrom", LocalDate.of(2026, 6, 1));

    assertThatThrownBy(() -> thresholdVersionRepository.saveAndFlush(overlapping))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void valueOrValueTextMustBePresent_databaseCheckConstraint() {
    Threshold threshold =
        thresholdRepository.saveAndFlush(
            new Threshold("TEST_THRESHOLD_NO_VALUE", "Test", ThresholdValueType.TEXT));
    ThresholdVersion noValue = ThresholdVersion.draft(threshold, null, null, null);

    assertThatThrownBy(() -> thresholdVersionRepository.saveAndFlush(noValue))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
