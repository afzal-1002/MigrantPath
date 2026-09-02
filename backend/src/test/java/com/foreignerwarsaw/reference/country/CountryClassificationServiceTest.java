package com.foreignerwarsaw.reference.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
 * Mocked-repository unit tests for the derived-classification logic itself (brief §20's "don't
 * build one giant reference-data service" split) - {@link CountryGroupMembershipRepositoryTest} in
 * the same package proves the same historical GB/EU_MEMBER scenario against the real seeded data;
 * this class isolates {@link CountryClassificationService}'s own derivation logic (the {@code
 * EU_EEA_SWISS_FREE_MOVEMENT_GROUPS} set, "any membership is enough", "no membership means outside
 * the free-movement group") against fixtures it controls completely. Deliberately never calls this
 * a "third-country" test - see the service class's own Javadoc for why that word is avoided here.
 */
@ExtendWith(MockitoExtension.class)
class CountryClassificationServiceTest {

  @Mock private CountryRepository countryRepository;
  @Mock private CountryGroupMembershipRepository membershipRepository;

  private CountryClassificationService service;

  @BeforeEach
  void setUp() {
    service = new CountryClassificationService(countryRepository, membershipRepository);
  }

  @Test
  void classificationsFor_unknownCountryCode_throwsCountryNotFound() {
    when(countryRepository.findByCodeIgnoreCase("zz")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.classificationsFor("zz", LocalDate.now()))
        .isInstanceOf(CountryNotFoundException.class);
  }

  @Test
  void classificationsFor_returnsEveryOverlappingGroupNotJustOne() {
    Country germany = countryWithCode("DE");
    when(countryRepository.findByCodeIgnoreCase("DE")).thenReturn(Optional.of(germany));
    LocalDate today = LocalDate.of(2026, 1, 1);
    when(membershipRepository.findActiveMembershipsForCountry(germany.getId(), today))
        .thenReturn(
            List.of(
                membershipToGroup("EU_MEMBER"),
                membershipToGroup("EEA"),
                membershipToGroup("SCHENGEN"),
                membershipToGroup("EU_EEA_SWISS")));

    List<String> classifications = service.classificationsFor("DE", today);

    assertThat(classifications)
        .containsExactlyInAnyOrder("EU_MEMBER", "EEA", "SCHENGEN", "EU_EEA_SWISS");
  }

  @Test
  void isMember_historicalUkBrexitCase_trueBeforeFalseAfter() {
    // The exact case the brief (§43) names: a static boolean would get this wrong for
    // any pre-2020 evaluation date.
    Country uk = countryWithCode("GB");
    when(countryRepository.findByCodeIgnoreCase("GB")).thenReturn(Optional.of(uk));
    LocalDate beforeBrexit = LocalDate.of(2019, 6, 1);
    LocalDate afterBrexit = LocalDate.of(2021, 6, 1);
    when(membershipRepository.findActiveMembershipsForCountry(uk.getId(), beforeBrexit))
        .thenReturn(List.of(membershipToGroup("EU_MEMBER")));
    when(membershipRepository.findActiveMembershipsForCountry(uk.getId(), afterBrexit))
        .thenReturn(List.of());

    assertThat(service.isMember("GB", "EU_MEMBER", beforeBrexit)).isTrue();
    assertThat(service.isMember("GB", "EU_MEMBER", afterBrexit)).isFalse();
  }

  @Test
  void isOutsideEuEeaSwissFreeMovementGroup_derivedFromAbsenceOfEuEeaOrEfta_neverStored() {
    Country pakistan = countryWithCode("PK");
    when(countryRepository.findByCodeIgnoreCase("PK")).thenReturn(Optional.of(pakistan));
    LocalDate today = LocalDate.now();
    when(membershipRepository.findActiveMembershipsForCountry(pakistan.getId(), today))
        .thenReturn(List.of());

    assertThat(service.isOutsideEuEeaSwissFreeMovementGroup("PK", today)).isTrue();
  }

  @Test
  void isOutsideEuEeaSwissFreeMovementGroup_switzerlandIsInsideBecauseOfEftaMembership() {
    // No separate "is Switzerland" special case anywhere - EFTA membership alone is
    // enough (CountryClassificationService's own Javadoc).
    Country switzerland = countryWithCode("CH");
    when(countryRepository.findByCodeIgnoreCase("CH")).thenReturn(Optional.of(switzerland));
    LocalDate today = LocalDate.now();
    when(membershipRepository.findActiveMembershipsForCountry(switzerland.getId(), today))
        .thenReturn(List.of(membershipToGroup("EFTA"), membershipToGroup("EU_EEA_SWISS")));

    assertThat(service.isOutsideEuEeaSwissFreeMovementGroup("CH", today)).isFalse();
  }

  @Test
  void isOutsideEuEeaSwissFreeMovementGroup_aConvenienceGroupAloneDoesNotExemptACountry() {
    // EU_EEA_SWISS is a CONVENIENCE grouping (ADR-006), not one of the three
    // EU_MEMBER/EEA/EFTA LEGAL groups this method actually checks - a country in only a
    // convenience group (a scenario that can't currently occur from real seed data, but
    // the derivation logic itself must not special-case it) is still outside the
    // free-movement group.
    Country hypothetical = countryWithCode("XX");
    when(countryRepository.findByCodeIgnoreCase("XX")).thenReturn(Optional.of(hypothetical));
    LocalDate today = LocalDate.now();
    when(membershipRepository.findActiveMembershipsForCountry(hypothetical.getId(), today))
        .thenReturn(List.of(membershipToGroup("EU_EEA_SWISS")));

    assertThat(service.isOutsideEuEeaSwissFreeMovementGroup("XX", today)).isTrue();
  }

  @Test
  void isOutsideEuEeaSwissFreeMovementGroup_schengenAloneDoesNotExemptACountry() {
    // The brief's "keep Schengen independent" instruction: SCHENGEN is border-control
    // cooperation, not a free-movement/residence-rights framework - membership in it
    // alone must never flip this result, even though every *current* real Schengen
    // member also happens to hold EU_MEMBER/EEA/EFTA (so this scenario needs a
    // hypothetical fixture, not real seed data, to exercise at all).
    Country hypothetical = countryWithCode("YY");
    when(countryRepository.findByCodeIgnoreCase("YY")).thenReturn(Optional.of(hypothetical));
    LocalDate today = LocalDate.now();
    when(membershipRepository.findActiveMembershipsForCountry(hypothetical.getId(), today))
        .thenReturn(List.of(membershipToGroup("SCHENGEN")));

    assertThat(service.isOutsideEuEeaSwissFreeMovementGroup("YY", today)).isTrue();
  }

  @Test
  void eftaAndEeaAreNotEquated_switzerlandDiffersFromIcelandLiechtensteinNorway() {
    // Switzerland: EFTA only. Iceland/Liechtenstein/Norway: both EFTA and EEA. If the
    // service ever conflated the two groups, this would be the first thing to break.
    Country switzerland = countryWithCode("CH");
    when(countryRepository.findByCodeIgnoreCase("CH")).thenReturn(Optional.of(switzerland));
    LocalDate today = LocalDate.now();
    when(membershipRepository.findActiveMembershipsForCountry(switzerland.getId(), today))
        .thenReturn(List.of(membershipToGroup("EFTA")));

    List<String> classifications = service.classificationsFor("CH", today);

    assertThat(classifications).contains("EFTA").doesNotContain("EEA");
    assertThat(service.isOutsideEuEeaSwissFreeMovementGroup("CH", today))
        .as("EFTA membership alone is still enough to be inside the free-movement group")
        .isFalse();
  }

  private Country countryWithCode(String code) {
    Country country = new Country();
    ReflectionTestUtils.setField(country, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(country, "code", code);
    ReflectionTestUtils.setField(country, "canonicalName", "Test Country " + code);
    return country;
  }

  private CountryGroupMembership membershipToGroup(String groupCode) {
    CountryGroup group = new CountryGroup();
    ReflectionTestUtils.setField(group, "code", groupCode);
    CountryGroupMembership membership = new CountryGroupMembership();
    ReflectionTestUtils.setField(membership, "countryGroup", group);
    return membership;
  }
}
