package com.foreignerwarsaw.reference.country;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Exercises the V11-seeded historical memberships directly against the real database - the same
 * "prove it against real data" standard as {@link CountryRepositoryTest}. The UK/Brexit row ({@code
 * GB, EU_MEMBER, 1973-01-01..2020-01-31}) is the one the brief explicitly calls out (§43) as the
 * case a static boolean would get wrong.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CountryGroupMembershipRepositoryTest {

  @Autowired private CountryRepository countryRepository;
  @Autowired private CountryGroupRepository countryGroupRepository;
  @Autowired private CountryGroupMembershipRepository membershipRepository;

  @Test
  void findActiveMembershipsForCountry_gbBeforeBrexit_includesEuMember() {
    Country gb = countryRepository.findByCodeIgnoreCase("GB").orElseThrow();

    List<CountryGroupMembership> memberships =
        membershipRepository.findActiveMembershipsForCountry(gb.getId(), LocalDate.of(2019, 1, 1));

    assertThat(memberships).extracting(m -> m.getCountryGroup().getCode()).contains("EU_MEMBER");
  }

  @Test
  void findActiveMembershipsForCountry_gbOnTheLastInclusiveDay_stillIncludesEuMember() {
    // valid_to = 2020-01-31 is inclusive (ADR-006) - the last day the UK was legally
    // still an EU member.
    Country gb = countryRepository.findByCodeIgnoreCase("GB").orElseThrow();

    List<CountryGroupMembership> memberships =
        membershipRepository.findActiveMembershipsForCountry(gb.getId(), LocalDate.of(2020, 1, 31));

    assertThat(memberships).extracting(m -> m.getCountryGroup().getCode()).contains("EU_MEMBER");
  }

  @Test
  void findActiveMembershipsForCountry_gbTheDayAfterBrexit_excludesEuMember() {
    Country gb = countryRepository.findByCodeIgnoreCase("GB").orElseThrow();

    List<CountryGroupMembership> memberships =
        membershipRepository.findActiveMembershipsForCountry(gb.getId(), LocalDate.of(2020, 2, 1));

    assertThat(memberships)
        .extracting(m -> m.getCountryGroup().getCode())
        .doesNotContain("EU_MEMBER");
  }

  @Test
  void findActiveMembershipsForCountry_deToday_hasFourOverlappingMemberships() {
    // Germany: EU_MEMBER, EEA, SCHENGEN, EU_EEA_SWISS all overlap simultaneously - by
    // design (CountryClassificationService's Javadoc: "memberships legitimately
    // overlap"), never collapsed into a single classification.
    Country de = countryRepository.findByCodeIgnoreCase("DE").orElseThrow();

    List<CountryGroupMembership> memberships =
        membershipRepository.findActiveMembershipsForCountry(de.getId(), LocalDate.of(2026, 1, 1));

    assertThat(memberships)
        .extracting(m -> m.getCountryGroup().getCode())
        .containsExactlyInAnyOrder("EU_MEMBER", "EEA", "SCHENGEN", "EU_EEA_SWISS");
  }

  @Test
  void findActiveMembershipsForGroup_schengen_includesRecentAccessionsOnlyFromTheirEffectiveDate() {
    // Bulgaria/Romania joined Schengen 2025-01-01 (V11) - not present before that date.
    CountryGroup schengen = countryGroupRepository.findByCode("SCHENGEN").orElseThrow();

    List<CountryGroupMembership> before2025 =
        membershipRepository.findActiveMembershipsForGroup(
            schengen.getId(), LocalDate.of(2024, 12, 31));
    List<CountryGroupMembership> in2025 =
        membershipRepository.findActiveMembershipsForGroup(
            schengen.getId(), LocalDate.of(2025, 6, 1));

    assertThat(before2025).extracting(m -> m.getCountry().getCode()).doesNotContain("BG", "RO");
    assertThat(in2025).extracting(m -> m.getCountry().getCode()).contains("BG", "RO");
  }

  @Test
  void switzerland_isEftaButNotEea_realSeedData() {
    // The brief's explicit "do not equate EFTA == EEA" check, against real V11 data,
    // not a mocked fixture.
    Country switzerland = countryRepository.findByCodeIgnoreCase("CH").orElseThrow();

    List<String> groups =
        membershipRepository
            .findActiveMembershipsForCountry(switzerland.getId(), LocalDate.of(2026, 1, 1))
            .stream()
            .map(m -> m.getCountryGroup().getCode())
            .toList();

    assertThat(groups).contains("EFTA");
    assertThat(groups).doesNotContain("EEA");
    // Still participates in the Polish/EU convenience grouping used for free-movement
    // routing, via the 1999 EU-Swiss bilateral agreement (V11) - not via EEA membership,
    // which it never held.
    assertThat(groups).contains("EU_EEA_SWISS");
  }

  @Test
  void iceland_liechtenstein_norway_areBothEftaAndEea_realSeedData() {
    for (String code : List.of("IS", "LI", "NO")) {
      Country country = countryRepository.findByCodeIgnoreCase(code).orElseThrow();

      List<String> groups =
          membershipRepository
              .findActiveMembershipsForCountry(country.getId(), LocalDate.of(2026, 1, 1))
              .stream()
              .map(m -> m.getCountryGroup().getCode())
              .toList();

      assertThat(groups).as(code + " must hold both EFTA and EEA").contains("EFTA", "EEA");
    }
  }

  @Test
  void provenanceStatus_pre2000AccessionsAreDraft_post2000AreVerified() {
    // V19's cutoff, proven against real rows rather than asserted in the abstract.
    Country belgium = countryRepository.findByCodeIgnoreCase("BE").orElseThrow();
    CountryGroupMembership belgiumEuMember1958 =
        membershipRepository
            .findActiveMembershipsForCountry(belgium.getId(), LocalDate.of(2026, 1, 1))
            .stream()
            .filter(m -> m.getCountryGroup().getCode().equals("EU_MEMBER"))
            .findFirst()
            .orElseThrow();
    assertThat(belgiumEuMember1958.getValidFrom()).isEqualTo(LocalDate.of(1958, 1, 1));
    assertThat(belgiumEuMember1958.getProvenanceStatus())
        .isEqualTo(MembershipProvenanceStatus.DRAFT);

    Country poland = countryRepository.findByCodeIgnoreCase("PL").orElseThrow();
    CountryGroupMembership polandEuMember2004 =
        membershipRepository
            .findActiveMembershipsForCountry(poland.getId(), LocalDate.of(2026, 1, 1))
            .stream()
            .filter(m -> m.getCountryGroup().getCode().equals("EU_MEMBER"))
            .findFirst()
            .orElseThrow();
    assertThat(polandEuMember2004.getValidFrom()).isEqualTo(LocalDate.of(2004, 5, 1));
    assertThat(polandEuMember2004.getProvenanceStatus())
        .isEqualTo(MembershipProvenanceStatus.VERIFIED);
  }

  @Test
  void coversDate_isInclusiveOnBothEndsAndOpenEndedWhenValidToIsNull() {
    Country de = countryRepository.findByCodeIgnoreCase("DE").orElseThrow();
    CountryGroupMembership schengenMembership =
        membershipRepository
            .findActiveMembershipsForCountry(de.getId(), LocalDate.of(2026, 1, 1))
            .stream()
            .filter(m -> m.getCountryGroup().getCode().equals("SCHENGEN"))
            .findFirst()
            .orElseThrow();

    assertThat(schengenMembership.getValidTo()).as("still an open-ended membership").isNull();
    assertThat(schengenMembership.coversDate(schengenMembership.getValidFrom())).isTrue();
    assertThat(schengenMembership.coversDate(LocalDate.of(2099, 1, 1)))
        .as("null validTo means never-ending")
        .isTrue();
    assertThat(schengenMembership.coversDate(schengenMembership.getValidFrom().minusDays(1)))
        .isFalse();
  }
}
