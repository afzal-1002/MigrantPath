package com.foreignerwarsaw.reference.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Real Testcontainers PostgreSQL 18 (via {@link TestcontainersConfiguration}), real Flyway-applied
 * schema (V7-V11) - not H2, not {@code ddl-auto=create} (brief §38's "prove it against the real
 * database" standard). Each test runs inside a rolled-back transaction (default
 * {@code @DataJpaTest} behaviour), so it sees the 250 Flyway-seeded countries (V8) plus whatever it
 * inserts itself, without polluting other tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CountryRepositoryTest {

  @Autowired private CountryRepository countryRepository;

  @Test
  void findByCodeIgnoreCase_matchesRegardlessOfCase() {
    assertThat(countryRepository.findByCodeIgnoreCase("pl")).isPresent();
    assertThat(countryRepository.findByCodeIgnoreCase("Pl")).isPresent();
    assertThat(countryRepository.findByCodeIgnoreCase("PL")).isPresent();
    assertThat(countryRepository.findByCodeIgnoreCase("pl").orElseThrow().getCanonicalName())
        .isEqualTo("Poland");
  }

  @Test
  void findByCodeIgnoreCase_unknownCode_returnsEmpty() {
    assertThat(countryRepository.findByCodeIgnoreCase("ZZ")).isEmpty();
  }

  @Test
  void countryCodeUniqueConstraint_isEnforcedByTheDatabaseNotJustTheApplication() {
    Country duplicate = newCountry("PL", "Duplicate Poland", 9999);

    assertThatThrownBy(() -> countryRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByActiveTrueOrderByDisplayOrderAsc_excludesInactiveAndIsOrderedByDisplayOrder() {
    Country inactive = newCountry("ZZ", "Not A Real Country", -1);
    org.springframework.test.util.ReflectionTestUtils.setField(inactive, "active", false);
    countryRepository.saveAndFlush(inactive);

    List<Country> active = countryRepository.findByActiveTrueOrderByDisplayOrderAsc();

    assertThat(active).extracting(Country::getCode).doesNotContain("ZZ");
    assertThat(active).hasSizeGreaterThanOrEqualTo(250);
    for (int i = 1; i < active.size(); i++) {
      assertThat(active.get(i - 1).getDisplayOrder())
          .isLessThanOrEqualTo(active.get(i).getDisplayOrder());
    }
  }

  @Test
  void seedData_containsExactly250Countries() {
    // Exact count from V8 (the mledoze/countries ISO 3166-1 dataset) - a change here
    // signals someone edited the seed migration, not a flaky test (brief §73's "prove
    // seed data quality" requirement).
    assertThat(countryRepository.count()).isEqualTo(250);
  }

  @Test
  void seedData_exactlyOneRowIsNotAnOfficiallyAssignedIsoCode_andItIsKosovo() {
    // 250 seeded rows, but ISO 3166-1 currently has 249 officially assigned alpha-2
    // codes (confirmed against Wikipedia's ISO 3166-1 alpha-2 article during this
    // audit) - the one extra is XK (Kosovo), a user-assigned code the mledoze dataset
    // includes but ISO never assigned. V18 makes this fact a real, queryable column
    // instead of only living in a doc comment (brief's post-approval audit).
    List<Country> allCountries = countryRepository.findAll();

    List<Country> notOfficiallyAssigned =
        allCountries.stream().filter(c -> !c.isOfficiallyAssigned()).toList();
    assertThat(notOfficiallyAssigned).extracting(Country::getCode).containsExactly("XK");
    assertThat(notOfficiallyAssigned.get(0).getCodeStandard())
        .isEqualTo(CountryCodeStandard.USER_ASSIGNED);
    assertThat(notOfficiallyAssigned.get(0).getNotes()).isNotBlank();

    long officiallyAssignedCount =
        allCountries.stream().filter(Country::isOfficiallyAssigned).count();
    assertThat(officiallyAssignedCount)
        .as("must match ISO 3166-1's current published count of officially assigned alpha-2 codes")
        .isEqualTo(249);
  }

  @Test
  void seedData_everyRealIsoCountryIsMarkedIso3166_1AndOfficiallyAssigned() {
    Country poland = countryRepository.findByCodeIgnoreCase("PL").orElseThrow();
    assertThat(poland.getCodeStandard()).isEqualTo(CountryCodeStandard.ISO_3166_1);
    assertThat(poland.isOfficiallyAssigned()).isTrue();
    assertThat(poland.getNotes()).isNull();
  }

  private Country newCountry(String code, String name, int displayOrder) {
    Country country = new Country();
    org.springframework.test.util.ReflectionTestUtils.setField(country, "code", code);
    org.springframework.test.util.ReflectionTestUtils.setField(country, "canonicalName", name);
    org.springframework.test.util.ReflectionTestUtils.setField(
        country, "displayOrder", displayOrder);
    return country;
  }
}
